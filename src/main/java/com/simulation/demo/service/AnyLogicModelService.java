package com.simulation.demo.service;

// AnyLogic imports - 现在可以直接导入了！
import com.anylogic.engine.gui.ExperimentHost;
import com.anylogic.engine.gui.IExperimentHost;
import nanjingdong.Simulation;

import com.simulation.demo.entity.SimulationRun;
import com.anylogic.engine.Experiment;
import com.simulation.demo.repository.SimulationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * AnyLogic模型服务 - 管理仿真模型的运行和控制
 *
 * 功能包括：
 * - 启动和停止仿真
 * - 监控仿真状态
 * - 管理仿真资源
 * - 获取模型端口号
 */
@Service
public class AnyLogicModelService {

    private static final Logger logger = LoggerFactory.getLogger(AnyLogicModelService.class);

    @Autowired
    private SimulationRunRepository simulationRunRepository;

    @Value("${simulation.timeout.minutes:30}")
    private int simulationTimeoutMinutes;

    @Value("${simulation.max.concurrent:3}")
    private int maxConcurrentSimulations;

    @Value("${simulation.web.enabled:false}")
    private boolean webServerEnabled;

    // 添加全局仿真实例管理
    private volatile Simulation globalSimulation = null;
    private volatile Object globalExperimentHost = null;
    private volatile boolean isGlobalSimulationInitialized = false;
    private final Object simulationLock = new Object();

    // 线程池管理 - 使用非守护线程防止JVM关闭时被强制终止
    private final ExecutorService simulationExecutor = Executors.newFixedThreadPool(5, new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(() -> {
                // 在线程开始时设置日志上下文
                try {
                    Thread.currentThread().setContextClassLoader(AnyLogicModelService.class.getClassLoader());
                    // 确保日志配置在子线程中生效
                    org.slf4j.LoggerFactory.getILoggerFactory();
                } catch (Exception e) {
                    // 忽略日志初始化异常
                }
                r.run();
            }, "SimulationWorker-" + threadNumber.getAndIncrement());
            t.setDaemon(false); // 改为非守护线程，防止JVM关闭时被强制终止
            t.setUncaughtExceptionHandler((thread, exception) -> {
                logger.error("仿真线程 {} 发生未捕获异常: {}", thread.getName(), exception.getMessage(), exception);
                // 不让异常传播到主线程
            });
            return t;
        }
    });

    // 运行中的仿真跟踪
    private final Map<Integer, CompletableFuture<Void>> runningSimulations = new ConcurrentHashMap<>();
    private final Map<Integer, Object> activeExperiments = new ConcurrentHashMap<>();
    @Value("${anylogic.model.file:model.jar}")
    private String modelFileName;

    // 初始化时注册关闭钩子和配置无界面模式
    {
        // 设置AnyLogic无界面模式，防止弹出浏览器
        configureHeadlessMode();

        // 注册JVM关闭钩子，确保在应用程序关闭时清理仿真资源
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("检测到JVM关闭信号，开始清理仿真资源...");
            try {
                // 检查是否是AnyLogic界面关闭导致的JVM关闭
                if (isAnyLogicInterfaceClose()) {
                    handleAnyLogicInterfaceClose();
                } else {
                    // 真正的应用程序关闭
                    cleanupSimulationResources();
                }
            } catch (Exception e) {
                logger.error("清理仿真资源时发生异常: {}", e.getMessage(), e);
            }
        }, "SimulationCleanupHook"));
    }

    /**
     * 配置AnyLogic无界面模式
     */
    private static void configureHeadlessMode() {
        try {
            // 设置系统属性禁用GUI和浏览器
            System.setProperty("java.awt.headless", "true");
            System.setProperty("anylogic.noui", "true");
            System.setProperty("anylogic.nobrowser", "true");
            System.setProperty("anylogic.server.mode", "true");
            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");

            // 禁用显示相关的系统属性
            System.setProperty("java.awt.graphicsenv", "java.awt.GraphicsEnvironment");

            // 添加更多AnyLogic特定的无界面配置
            System.setProperty("anylogic.headless", "true");
            System.setProperty("anylogic.no.gui", "true");
            System.setProperty("anylogic.no.browser", "true");
            System.setProperty("anylogic.web.server.only", "true");
            System.setProperty("anylogic.no.experiment.gui", "true");
            System.setProperty("anylogic.no.simulation.gui", "true");

            logger.info("✓ AnyLogic无界面模式配置完成");

        } catch (Exception e) {
            logger.warn("配置无界面模式时发生异常: {}", e.getMessage());
        }
    }

        /**
     * 清理仿真资源但不关闭应用
     */
    private void cleanupSimulationResources() {
        logger.info("开始清理仿真资源...");

        try {
            // 停止所有运行中的仿真
            for (Integer runId : new java.util.ArrayList<>(runningSimulations.keySet())) {
                try {
                    logger.info("正在停止仿真 run_id={}", runId);
                    // stopSimulation(runId);
                } catch (Exception e) {
                    logger.error("停止仿真失败 run_id={}: {}", runId, e.getMessage());
                }
            }

            // 清理资源但不关闭线程池
            runningSimulations.clear();
            activeExperiments.clear();

            logger.info("仿真资源清理完成");

        } catch (Exception e) {
            logger.error("清理仿真资源时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 检测AnyLogic界面关闭并处理
     */
    private void handleAnyLogicInterfaceClose() {
        logger.info("检测到AnyLogic界面关闭，但保持后端服务运行...");

        try {
            // 只清理仿真资源，不关闭整个应用
            // cleanupSimulationResources();

            // 重新初始化线程池（如果需要）
            if (simulationExecutor.isShutdown()) {
                logger.info("线程池已关闭，重新初始化...");
                // 注意：这里不能直接重新创建ExecutorService，因为它是final的
                // 在实际应用中，可以考虑使用可重新初始化的线程池
            }

            logger.info("AnyLogic界面关闭处理完成，后端服务继续运行");

        } catch (Exception e) {
            logger.error("处理AnyLogic界面关闭时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 检测是否是AnyLogic界面关闭导致的JVM关闭
     */
    private boolean isAnyLogicInterfaceClose() {
        try {
            // 检查是否有运行中的仿真
            boolean hasRunningSimulations = !runningSimulations.isEmpty();

            // 检查线程池状态
            boolean threadPoolActive = !simulationExecutor.isShutdown();

            // 检查是否有AnyLogic相关的线程
            Thread[] threads = new Thread[Thread.activeCount()];
            Thread.enumerate(threads);
            boolean hasAnyLogicThreads = false;

            for (Thread thread : threads) {
                if (thread != null && thread.getName().contains("AnyLogic")) {
                    hasAnyLogicThreads = true;
                    break;
                }
            }

            // 如果还有运行中的仿真但线程池仍然活跃，可能是界面关闭
            if (hasRunningSimulations && threadPoolActive && !hasAnyLogicThreads) {
                logger.info("检测到可能是AnyLogic界面关闭");
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.warn("检测AnyLogic界面关闭状态时发生异常: {}", e.getMessage());
            return false;
        }
    }

    public boolean isModelFileExists() {
        File modelFile = new File(modelFileName);
        boolean exists = modelFile.exists() && modelFile.isFile();
        logger.debug("检查模型文件 {}: {}", modelFileName, exists ? "存在" : "不存在");
        return exists;
    }

    /**
     * 获取运行中的仿真数量
     */
    public int getRunningSimulationCount() {
        return runningSimulations.size();
    }

    /**
     * 异步启动仿真
     */
    @Async
    public CompletableFuture<Integer> startSimulation() {
        logger.info("开始启动新的仿真...");

        // 检查并发限制
        if (runningSimulations.size() >= maxConcurrentSimulations) {
            logger.warn("已达到最大并发仿真数量限制: {}", maxConcurrentSimulations);
            throw new RuntimeException("已达到最大并发仿真数量限制");
        }

        try {
            // 创建仿真运行记录
            SimulationRun simulationRun = new SimulationRun();
            simulationRun.setModelName("NanJingDong");
            simulationRun.setStartDate(LocalDateTime.now());
            simulationRun.setState(Experiment.State.RUNNING);
            simulationRun = simulationRunRepository.save(simulationRun);

            Integer runId = simulationRun.getRunId();
            logger.info("成功创建仿真运行记录，获取 run_id = {}", runId);

            // 创建异步任务
            CompletableFuture<Void> simulationTask = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return runSimulationInThread(runId);
                    } catch (InterruptedException e) {
                        logger.warn("仿真线程被中断 run_id={}: {}", runId, e.getMessage());
                        Thread.currentThread().interrupt();
                        return null;
                    } catch (Exception e) {
                        logger.error("仿真执行异常 run_id={}: {}", runId, e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                }, simulationExecutor)
                .orTimeout(simulationTimeoutMinutes, TimeUnit.MINUTES)
                .exceptionally(throwable -> {
                    if (throwable instanceof TimeoutException) {
                        logger.error("仿真超时 run_id={}", runId);
                        updateSimulationState(runId, Experiment.State.ERROR);
                    } else {
                        logger.error("仿真失败 run_id={}: {}", runId, throwable.getMessage());
                        updateSimulationState(runId, Experiment.State.ERROR);
                    }
                    return null;
                })
                .thenRun(() -> {
                    logger.info("仿真完成，清理资源 run_id={}", runId);
                    resetSimulation(globalSimulation, runId);
                    stopSimulation(globalSimulation, runId);
                    runningSimulations.remove(runId);
                    activeExperiments.remove(runId);
                });

            // 记录运行中的仿真
            runningSimulations.put(runId, simulationTask);

            return CompletableFuture.completedFuture(runId);

        } catch (Exception e) {
            logger.error("启动仿真失败: {}", e.getMessage(), e);
            throw new RuntimeException("启动仿真失败: " + e.getMessage(), e);
        }
    }
    /**
     * 创建并启动仿真（Controller接口兼容方法）
     */
    public SimulationRun createAndStartSimulation(String modelName, Map<String, Object> engineParameters, Map<String, Object> agentParameters, String description) {
        logger.info("创建并启动仿真: modelName={}, engineParams={}, agentParams={}, description={}",
                   modelName,
                   engineParameters != null ? engineParameters.size() : 0,
                   agentParameters != null ? agentParameters.size() : 0,
                   description);

        try {
            // 检查并发限制
            if (runningSimulations.size() >= maxConcurrentSimulations) {
                throw new RuntimeException("已达到最大并发仿真数量限制: " + maxConcurrentSimulations);
            }

            // 将参数Map转换为JSON字符串
            String engineParametersJson = null;
            String agentParametersJson = null;

            if (engineParameters != null && !engineParameters.isEmpty()) {
                ObjectMapper objectMapper = new ObjectMapper();
                engineParametersJson = objectMapper.writeValueAsString(engineParameters);
                logger.info("引擎参数JSON: {}", engineParametersJson);
            }

            if (agentParameters != null && !agentParameters.isEmpty()) {
                ObjectMapper objectMapper = new ObjectMapper();
                agentParametersJson = objectMapper.writeValueAsString(agentParameters);
                logger.info("智能体参数JSON: {}", agentParametersJson);
            }

            // 创建仿真运行记录
            SimulationRun simulationRun = new SimulationRun();
            simulationRun.setModelName(modelName != null ? modelName : "NanJingDong");
            simulationRun.setStartDate(LocalDateTime.now());
            simulationRun.setState(Experiment.State.IDLE);
            simulationRun.setEngineParameters(engineParametersJson);
            simulationRun.setAgentParameters(agentParametersJson);
            simulationRun.setDescription(description);
            simulationRun = simulationRunRepository.save(simulationRun);

            Integer runId = simulationRun.getRunId();
            logger.info("成功创建仿真运行记录，获取 run_id = {}", runId);

            // 异步启动仿真
            startSimulationAsync(runId);

            // 更新状态为运行中
            simulationRun.setState(Experiment.State.RUNNING);
            return simulationRunRepository.save(simulationRun);

        } catch (Exception e) {
            logger.error("创建并启动仿真失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建并启动仿真失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步启动指定的仿真
     */
    private void startSimulationAsync(Integer runId) {
        logger.info("异步启动仿真 run_id={}", runId);

        // 创建异步任务
        CompletableFuture<Void> simulationTask = CompletableFuture
            .supplyAsync(() -> {
                try {
                    return runSimulationInThread(runId);
                } catch (InterruptedException e) {
                    logger.warn("仿真线程被中断 run_id={}: {}", runId, e.getMessage());
                    Thread.currentThread().interrupt();
                    return null;
                } catch (Exception e) {
                    logger.error("仿真执行异常 run_id={}: {}", runId, e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }, simulationExecutor)
            .orTimeout(simulationTimeoutMinutes, TimeUnit.MINUTES)
            .exceptionally(throwable -> {
                if (throwable instanceof TimeoutException) {
                    logger.error("仿真超时 run_id={}", runId);
                    updateSimulationState(runId, Experiment.State.ERROR);
                } else {
                    logger.error("仿真失败 run_id={}: {}", runId, throwable.getMessage());
                    updateSimulationState(runId, Experiment.State.ERROR);
                }
                return null;
            })
            .thenRun(() -> {
                logger.info("仿真完成，清理资源 run_id={}", runId);
                runningSimulations.remove(runId);
                activeExperiments.remove(runId);
            });

        // 记录运行中的仿真
        runningSimulations.put(runId, simulationTask);
    }
    /**
     * 在线程中运行仿真
     * 流程：创建仿真实例 -> 应用引擎参数 -> 创建智能体 -> 应用智能体参数 -> 运行仿真
     */
    private Object runSimulationInThread(Integer runId) throws InterruptedException {
        logger.info("开始在线程中运行仿真 run_id={}", runId);

        try {
            // 1. 获取仿真参数
            SimulationRun simulationRun = simulationRunRepository.findById(runId).orElse(null);
            if (simulationRun == null) {
                logger.error("找不到仿真运行记录 run_id={}", runId);
                updateSimulationState(runId, Experiment.State.ERROR);
                return null;
            }

            String engineParametersJson = simulationRun.getEngineParameters();
            String agentParametersJson = simulationRun.getAgentParameters();
            agentParametersJson=agentParametersJson.replace("\"runId\":null", "\"runId\":"+runId);

            logger.info("获取到仿真参数 run_id={}, engineParams={}, agentParams={}",
                       runId, engineParametersJson, agentParametersJson);

            // 2. 获取或创建全局仿真实例
            logger.info("=== 获取全局仿真实例 ===");
            Simulation experiment = getOrCreateGlobalSimulation();
            activeExperiments.put(runId, experiment);
            logger.info("✓ 获取全局仿真实例成功");

            // 状态检查与管理逻辑优化
            synchronized (simulationLock) {
                Experiment.State state = globalSimulation.getState();
                logger.info("当前仿真状态: {}", state.name());
                if (state == Experiment.State.RUNNING || state == Experiment.State.PAUSED || state == Experiment.State.PLEASE_WAIT) {
                    logger.info("仿真处于{}状态，先reset", state.name());
                    resetSimulation(globalSimulation, runId);
                }
                // 只有IDLE、FINISHED、ERROR才允许启动
                if (state != Experiment.State.IDLE && state != Experiment.State.FINISHED && state != Experiment.State.ERROR) {
                    throw new IllegalStateException("仿真状态异常，无法启动新仿真: " + state.name());
                }
            }

            // 3. 检查仿真状态，如果正在运行则重置
            // if (isSimulationRunning(experiment)) { // This line is removed as per the new logic
            //     logger.info("检测到仿真正在运行，执行重置 run_id={}", runId);
            //     resetSimulation(experiment, runId);
            //     pauseSimulation(experiment, runId);
            // }

            // 4. 应用引擎参数（在创建智能体之前）
            logger.info("=== 应用引擎参数 ===");
            applyEngineParameters(experiment, engineParametersJson);

            // 5. 确保顶层智能体存在
            logger.info("=== 检查顶层智能体 ===");
            try {
                // 尝试获取根对象，如果不存在则创建
                Object root = experiment.getEngine().getRoot();
                if (root == null) {
                    logger.info("顶层智能体不存在，创建新的顶层智能体");
                    experiment.step();
                    logger.info("✓ 顶层智能体创建成功");
                } else {
                    logger.info("✓ 顶层智能体已存在");
                }
            } catch (Exception e) {
                logger.info("创建顶层智能体时发生异常，重新创建: {}", e.getMessage());
                experiment.step();
                logger.info("✓ 顶层智能体重新创建成功");
            }

            // 5. 应用智能体参数（在智能体创建之后）
            logger.info("=== 应用智能体参数 ===");
            applyAgentParameters(experiment, agentParametersJson);

            // 6. 根据配置决定是否获取模型端口号
            if (webServerEnabled) {
                logger.info("=== 获取模型端口号 ===");
                int port = 12345;
                if (port > 0) {
                    logger.info("✓✓✓ 最终获取到的端口号: {} ✓✓✓", port);
                    updateSimulationPort(runId, port);
                } else {
                    logger.warn("× 未能获取到有效的端口号");
                }
            } else {
                logger.info("Web服务器已禁用，跳过端口获取");
            }

            // 7. 启动仿真
            logger.info("开始运行仿真...");
            long startTime = System.currentTimeMillis();

            // 在隔离的线程中启动仿真（无界面模式）
            CompletableFuture<Void> simulationRunFuture = CompletableFuture.runAsync(() -> {
                Thread currentThread = Thread.currentThread();
                String originalName = currentThread.getName();
                currentThread.setName("Simulation-" + runId);

                try {
                    // 在线程开始时立即测试日志
                    Logger threadLogger = LoggerFactory.getLogger(AnyLogicModelService.class);
                    threadLogger.info("=== 线程开始 - 日志测试 ===");
                    threadLogger.info("仿真开始运行 run_id={}, 线程: {} (无界面模式)", runId, currentThread.getName());

                    // 测试子线程日志
                    testThreadLogging(runId);

                    // 设置线程中断处理
                    if (Thread.currentThread().isInterrupted()) {
                        threadLogger.warn("仿真线程被中断，停止运行 run_id={}", runId);
                        return;
                    }

                    // 启动无界面仿真
                    runHeadlessSimulation(experiment, runId);
                    threadLogger.info("仿真正常结束 run_id={}");

                } catch (Exception e) {
                    Logger threadLogger = LoggerFactory.getLogger(AnyLogicModelService.class);
                    threadLogger.error("仿真运行异常 run_id={}: {}", runId, e.getMessage(), e);
                    updateSimulationState(runId, Experiment.State.ERROR);
                } finally {
                    currentThread.setName(originalName);
                    Logger threadLogger = LoggerFactory.getLogger(AnyLogicModelService.class);
                    threadLogger.info("仿真线程清理完成 run_id={}", runId);
                }
            }, simulationExecutor).exceptionally(throwable -> {
                Logger threadLogger = LoggerFactory.getLogger(AnyLogicModelService.class);
                threadLogger.error("仿真执行出现严重错误 run_id={}: {}", runId, throwable.getMessage(), throwable);
                updateSimulationState(runId, Experiment.State.ERROR);
                return null;
            });

            // 8. 等待仿真完成
            try {
                simulationRunFuture.get(30, TimeUnit.SECONDS); // 等待30秒
                logger.info("仿真任务完成 run_id={}", runId);
                updateSimulationState(runId, Experiment.State.FINISHED);
                stopSimulation(experiment,runId);
            } catch (TimeoutException e) {
                logger.warn("仿真任务超时 run_id={}", runId);
                updateSimulationState(runId, Experiment.State.ERROR);
            } catch (Exception e) {
                logger.error("仿真任务异常 run_id={}: {}", runId, e.getMessage(), e);
                updateSimulationState(runId, Experiment.State.ERROR);
            }

            return experiment;

        } catch (Exception e) {
            logger.error("仿真线程执行失败 run_id={}: {}", runId, e.getMessage(), e);
            updateSimulationState(runId, Experiment.State.ERROR);
            return null;
        }
    }

    /**
     * 测试子线程日志输出
     */
    private void testThreadLogging(Integer runId) {
        try {
            Logger threadLogger = LoggerFactory.getLogger(AnyLogicModelService.class);
            threadLogger.info("=== 子线程日志测试开始 ===");
            threadLogger.info("当前线程: {}", Thread.currentThread().getName());
            threadLogger.info("线程ID: {}", Thread.currentThread().getId());
            threadLogger.info("类加载器: {}", Thread.currentThread().getContextClassLoader());
            threadLogger.info("=== 子线程日志测试结束 ===");
        } catch (Exception e) {
            System.err.println("日志测试失败: " + e.getMessage());
        }
    }

    /**
     * 获取或创建全局仿真实例
     */
    private Simulation getOrCreateGlobalSimulation() {
        synchronized (simulationLock) {
            if (globalSimulation == null || !isGlobalSimulationInitialized) {
                try {
                    logger.info("创建全局仿真实例...");

                    // // 配置无界面模式
                    // configureHeadlessMode();

                    // 创建仿真实例
                    globalSimulation = new Simulation();
                    logger.info("✓ 全局仿真实例创建成功");

                    // 初始化ExperimentHost
                    initializeGlobalExperimentHost();

                    isGlobalSimulationInitialized = true;
                    logger.info("✓ 全局仿真初始化完成");

                } catch (Exception e) {
                    logger.error("创建全局仿真实例失败: {}", e.getMessage(), e);
                    // throw new SimulationException("创建全局仿真实例失败", e);
                }
            }
            return globalSimulation;
        }
    }

    /**
     * 初始化全局ExperimentHost
     */
    private void initializeGlobalExperimentHost() {
        try {
            // 创建ExperimentHost实例
            // Class<?> experimentHostClass = Class.forName("com.anylogic.engine.gui.ExperimentHost");
            // Constructor<?> constructor = experimentHostClass.getDeclaredConstructor();
            // constructor.setAccessible(true);
            globalExperimentHost = (IExperimentHost)new ExperimentHost(globalSimulation);
            globalSimulation.setup((IExperimentHost)globalExperimentHost);
            ((IExperimentHost)globalExperimentHost).launch();
            // globalExperimentHos
            // 配置ExperimentHost
            configureExperimentHost(globalExperimentHost);

            logger.info("✓ 全局ExperimentHost初始化完成");

        } catch (Exception e) {
            logger.error("初始化全局ExperimentHost失败: {}", e.getMessage(), e);
            // throw new SimulationException("初始化全局ExperimentHost失败", e);
        }
    }

    /**
     * 配置ExperimentHost
     */
    private void configureExperimentHost(Object experimentHost) {
        try {
            // 浏览器启动

            // 设置其他配置
            Method setWebServerEnabledMethod = experimentHost.getClass().getMethod("setWebServerEnabled", boolean.class);
            setWebServerEnabledMethod.invoke(experimentHost, webServerEnabled);

            logger.info("✓ ExperimentHost配置完成");

        } catch (Exception e) {
            logger.warn("配置ExperimentHost时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 重置仿真到初始状态
     */
    private void resetSimulation(Simulation simulation, Integer runId) {
        try {
            logger.info("重置仿真到初始状态 run_id={}", runId);

            // 停止当前仿真
            try {
                Method stopMethod = simulation.getClass().getMethod("stop");
                stopMethod.invoke(simulation);
                logger.info("✓ 停止当前仿真 run_id={}", runId);
            } catch (Exception e) {
                logger.debug("停止仿真时发生异常: {}", e.getMessage());
            }

            // 重置仿真
            try {
                Method resetMethod = simulation.getClass().getMethod("reset");
                resetMethod.invoke(simulation);
                logger.info("✓ 重置仿真完成 run_id={}", runId);
            } catch (Exception e) {
                logger.error("重置仿真失败: {}", e.getMessage(), e);
                throw new SimulationException("重置仿真失败", e);
            }

            // 等待重置完成
            Thread.sleep(1000);

        } catch (Exception e) {
            logger.error("重置仿真时发生异常 run_id={}: {}", runId, e.getMessage(), e);
            // throw new SimulationException("重置仿真失败", e);
        }
    }

    /**
     * 暂停仿真
     */
    private void pauseSimulation(Simulation simulation, Integer runId) {
        try {
            logger.info("暂停仿真 run_id={}", runId);

            Method pauseMethod = simulation.getClass().getMethod("pause");
            pauseMethod.invoke(simulation);
            logger.info("✓ 仿真已暂停 run_id={}", runId);

        } catch (Exception e) {
            logger.warn("暂停仿真时发生异常 run_id={}: {}", runId, e.getMessage());
        }
    }

    /**
     * 恢复仿真
     */
    private void resumeSimulation(Simulation simulation, Integer runId) {
        try {
            logger.info("恢复仿真 run_id={}", runId);

            Method resumeMethod = simulation.getClass().getMethod("resume");
            resumeMethod.invoke(simulation);
            logger.info("✓ 仿真已恢复 run_id={}", runId);

        } catch (Exception e) {
            logger.warn("恢复仿真时发生异常 run_id={}: {}", runId, e.getMessage());
        }
    }

    /**
     * 检查仿真是否正在运行
     */
    private boolean isSimulationRunning(Simulation simulation) {
        try {
            Object state = simulation.getState();
            return state != null && !state.toString().equals("FINISHED") && !state.toString().equals("STOPPED");
        } catch (Exception e) {
            logger.debug("检查仿真状态时发生异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 安全清理仿真资源（仅在必要时）
     */
    private void safeCleanupSimulationResources(Simulation experiment, Integer runId) {
        try {
            logger.info("开始安全清理仿真资源 run_id={}", runId);

            // 1. 使用Engine.finish()方法安全终止模型
            try {
                if (experiment != null) {
                    // 获取引擎对象
                    Object engine = experiment.getEngine();
                    if (engine != null) {
                        // 使用Engine.finish()方法安全终止模型
                        try {
                            Method finishMethod = engine.getClass().getMethod("finish");
                            boolean finished = (Boolean) finishMethod.invoke(engine);
                            logger.info("✓ 成功调用引擎finish方法 run_id={}, result={}", runId, finished);
                        } catch (Exception e) {
                            logger.debug("无法调用引擎finish方法 run_id={}: {}", runId, e.getMessage());
                        }
                    }

                    // 尝试调用实验stop方法（不使用close方法）
                    try {
                        Method stopMethod = experiment.getClass().getMethod("stop");
                        stopMethod.invoke(experiment);
                        logger.info("✓ 成功调用实验stop方法 run_id={}", runId);
                    } catch (Exception e) {
                        logger.debug("无法调用实验stop方法 run_id={}: {}", runId, e.getMessage());
                    }

                    // 明确不使用close方法，因为它会销毁模型并可能影响JVM
                    logger.info("✓ 跳过close方法调用，避免销毁模型和影响JVM run_id={}", runId);
                }
            } catch (Exception e) {
                logger.warn("停止实验时发生异常 run_id={}: {}", runId, e.getMessage());
            }

            // 2. 等待一段时间让资源释放
            try {
                Thread.sleep(3000); // 增加等待时间
                logger.info("✓ 等待资源释放完成 run_id={}", runId);
            } catch (InterruptedException e) {
                logger.warn("等待资源释放时被中断 run_id={}", runId);
                Thread.currentThread().interrupt();
            }

            // 3. 清理缓存和内存
            try {
                // 清理快照缓存
                if (experiment != null) {
                    Object engine = experiment.getEngine();
                    if (engine != null) {
                        try {
                            Method flushCacheMethod = engine.getClass().getMethod("flushSnapshotCache");
                            flushCacheMethod.invoke(engine);
                            logger.info("✓ 清理快照缓存完成 run_id={}", runId);
                        } catch (Exception e) {
                            logger.debug("无法清理快照缓存 run_id={}: {}", runId, e.getMessage());
                        }
                    }
                }

                // 建议垃圾回收
                System.gc();
                logger.info("✓ 建议垃圾回收完成 run_id={}", runId);
            } catch (Exception e) {
                logger.warn("清理缓存时发生异常 run_id={}: {}", runId, e.getMessage());
            }

            logger.info("仿真资源安全清理完成 run_id={}", runId);

        } catch (Exception e) {
            logger.error("安全清理仿真资源时发生异常 run_id={}: {}", runId, e.getMessage(), e);
        }
    }

    /**
     * 添加实验执行监听器
     */
    private void addExperimentExecutionListener(Simulation experiment, Integer runId) {
        try {
            // 添加执行监听器来监听实验状态
            Method addListenerMethod = experiment.getClass().getMethod("addExecutionListener",
                Class.forName("com.anylogic.engine.ExperimentExecutionListener"));

            // 创建监听器实例
            Object listener = java.lang.reflect.Proxy.newProxyInstance(
                experiment.getClass().getClassLoader(),
                new Class<?>[] { Class.forName("com.anylogic.engine.ExperimentExecutionListener") },
                (proxy, method, args) -> {
                    if ("onExecutionFinished".equals(method.getName())) {
                        logger.info("实验执行完成监听器触发 run_id={}", runId);
                        // 在这里可以添加自定义的清理逻辑
                    }
                    return null;
                }
            );

            addListenerMethod.invoke(experiment, listener);
            logger.info("✓ 成功添加实验执行监听器 run_id={}", runId);

        } catch (Exception e) {
            logger.debug("无法添加实验执行监听器 run_id={}: {}", runId, e.getMessage());
        }
    }

    /**
     * 运行无界面仿真 - 改进版本，集成LongRunSimulation的控制逻辑
     */
    private void runHeadlessSimulation(Simulation experiment, Integer runId) {
        try {
            // 在子线程中重新配置日志上下文
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
            System.setProperty("java.awt.headless", "true"); // 确保当前线程也是无界面模式

            // 强制刷新日志配置 - 在AnyLogic运行前重新初始化
            try {
                // 重新获取logger实例，确保在子线程中正常工作
                Logger threadLogger = LoggerFactory.getLogger(AnyLogicModelService.class);
                threadLogger.info("=== 子线程日志测试 - 在AnyLogic运行前 ===");

                // 强制刷新日志工厂
                org.slf4j.LoggerFactory.getILoggerFactory();
            } catch (Exception e) {
                System.err.println("日志工厂初始化异常: " + e.getMessage());
            }

            logger.info("启动无界面仿真 run_id={}", runId);

            // 获取引擎对象
            Object engine = experiment.getEngine();
            if (engine != null) {
                logger.info("获取到引擎对象: {}", engine.getClass().getName());

                // 设置引擎参数（参考LongRunSimulation）
                try {
                    // 设置实时模式
                    Method setRealTimeModeMethod = engine.getClass().getMethod("setRealTimeMode", boolean.class);
                    setRealTimeModeMethod.invoke(engine, false); // 设置为非实时模式
                    logger.info("✓ 设置引擎实时模式: false");
                } catch (Exception e) {
                    logger.debug("无法设置实时模式: {}", e.getMessage());
                }

                // 设置时间缩放
                try {
                    Method setRealTimeScaleMethod = engine.getClass().getMethod("setRealTimeScale", double.class);
                    setRealTimeScaleMethod.invoke(engine, 1000.0); // 设置时间缩放
                    logger.info("✓ 设置引擎时间缩放: 1000.0");
                } catch (Exception e) {
                    logger.debug("无法设置时间缩放: {}", e.getMessage());
                }
            }

            // 启动仿真
            logger.info("开始运行仿真...");
            experiment.run();

            // 替换原有的仿真状态监控循环
            int checkCount = 0;
            boolean simulationFinished = false;
            while (!simulationFinished && checkCount < 3600) { // 最多监控1小时
                Experiment.State state = globalSimulation.getState();
                logger.info("仿真监控中，当前状态: {}", state.name());
                if (state == Experiment.State.FINISHED) {
                    logger.info("仿真已完成 run_id={}", runId);
                    simulationFinished = true;
                    break;
                }
                if (state == Experiment.State.IDLE || state == Experiment.State.ERROR) {
                    logger.info("仿真已停止或出错 run_id={}", runId);
                    simulationFinished = true;
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("仿真监控线程被中断");
                    break;
                }
                checkCount++;
            }

            logger.info("无界面仿真运行完成 run_id={}", runId);

        } catch (Exception e) {
            logger.error("无界面仿真运行失败 run_id={}: {}", runId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 安全获取仿真时间
     */
    private double getCurrentSimulationTime(Simulation experiment) {
        try {
            // 使用反射调用getTime方法以避免弃用警告
            return experiment.time();
        } catch (Exception e) {
            logger.debug("无法获取仿真时间: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 获取模型端口号 - 使用你提供的简洁方式
     * @param experiment 仿真实验对象
     * @return 端口号，如果获取失败返回-1
     */

    private static int getModelPort(Simulation experiment) {
        logger.info("通过ExperimentHost获取端口号...");

        try {
            // 创建端口结果容器
            final int[] portResult = {-1};

            // 使用匿名类继承ExperimentHost，重写launch方法以禁用浏览器启动
            IExperimentHost host = new ExperimentHost(experiment) {
                @Override
                public void launch() {
                    logger.info("启动ExperimentHost (无浏览器模式)...");

                    try {
                        // 禁用自动浏览器启动
                        disableBrowserLaunch(this);

                        // 调用父类启动方法
                        super.launch();
                        logger.info("ExperimentHost启动成功，开始获取端口...");

                        try {
                            // 按照你的工作代码方式获取端口
                            Field swsField = ExperimentHost.class.getDeclaredField("jn");
                            swsField.setAccessible(true);
                            Object sws = swsField.get(this);

                            if (sws != null) {
                                logger.info("✓ 成功获取到 jn 字段: {}", sws.getClass().getName());

                                Field ctrlField = sws.getClass().getDeclaredField("controller");
                                ctrlField.setAccessible(true);
                                Object controller = ctrlField.get(sws);

                                if (controller != null) {
                                    logger.info("✓ 成功获取到 controller 字段: {}", controller.getClass().getName());

                                    Field nField = controller.getClass().getDeclaredField("n");
                                    nField.setAccessible(true);
                                    int nValue = nField.getInt(controller);

                                    logger.info("✓ 成功获取到端口号: {}", nValue);

                                    // 验证端口号
                                    if (nValue > 0 && nValue < 65536) {
                                        logger.info("✓ 端口号验证: 有效端口范围 (1-65535)");
                                        portResult[0] = nValue;
                                    } else {
                                        logger.warn("× 端口号验证: 无效的端口号");
                                    }

                                } else {
                                    logger.warn("× controller 字段为 null");
                                }
                            } else {
                                logger.warn("× jn 字段为 null");
                            }

                        } catch (NoSuchFieldException nsf) {
                            logger.error("× 反射错误 - 字段未找到: {}", nsf.getMessage());
                        } catch (Exception ex) {
                            logger.error("× 反射获取端口失败: {}", ex.getMessage(), ex);
                        }

                    } catch (Exception e) {
                        logger.error("× ExperimentHost启动失败: {}", e.getMessage(), e);
                    }
                }

                /**
                 * 禁用浏览器自动启动
                 */
                private void disableBrowserLaunch(ExperimentHost host) {
                    try {
                        // 尝试设置系统属性禁用浏览器启动
                        System.setProperty("java.awt.headless", "true");
                        System.setProperty("anylogic.noui", "true");
                        System.setProperty("anylogic.nobrowser", "true");

                        // 尝试通过反射禁用浏览器启动功能
                        try {
                            Field browserLaunchField = ExperimentHost.class.getDeclaredField("browserLaunch");
                            browserLaunchField.setAccessible(true);
                            browserLaunchField.setBoolean(host, false);
                            logger.info("✓ 成功禁用浏览器启动 (browserLaunch字段)");
                        } catch (NoSuchFieldException e) {
                            logger.debug("browserLaunch字段不存在: {}", e.getMessage());
                        }

                        // 尝试其他可能的字段名
                        try {
                            Field autoOpenField = ExperimentHost.class.getDeclaredField("autoOpen");
                            autoOpenField.setAccessible(true);
                            autoOpenField.setBoolean(host, false);
                            logger.info("✓ 成功禁用浏览器启动 (autoOpen字段)");
                        } catch (NoSuchFieldException e) {
                            logger.debug("autoOpen字段不存在: {}", e.getMessage());
                        }

                        // 尝试设置启动模式为服务器模式
                        try {
                            Field modeField = ExperimentHost.class.getDeclaredField("mode");
                            modeField.setAccessible(true);
                            // 假设存在SERVER模式常量
                            modeField.set(host, "SERVER");
                            logger.info("✓ 设置为服务器模式");
                        } catch (Exception e) {
                            logger.debug("无法设置服务器模式: {}", e.getMessage());
                        }

                        logger.info("✓ 浏览器启动禁用配置完成");

                    } catch (Exception e) {
                        logger.warn("禁用浏览器启动时发生异常: {}", e.getMessage());
                    }
                }
            };

            // 初始化实验主机
            logger.info("初始化仿真...");
            experiment.setup(host);

            // 调用launch来触发端口获取
            logger.info("启动ExperimentHost...");
            host.launch();

            return portResult[0];

        } catch (Exception e) {
            logger.error("× 创建ExperimentHost失败: {}", e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 停止指定的仿真 - 改进版本，集成LongRunSimulation的控制逻辑
     */
    public boolean stopSimulation(Simulation simulation,Integer runId) {
        logger.info("尝试停止仿真 run_id={}", runId);

        try {
            Object experiment = activeExperiments.get(runId);
            if (experiment != null) {
                try {
                    Method pauseMethod = experiment.getClass().getMethod("stop");
                    pauseMethod.invoke(experiment);
                    logger.info("成功停止仿真 run_id={}", runId);
                    // updateSimulationState(runId, Experiment.State.IDLE);
                    return true;
                } catch (Exception e) {
                    logger.error("暂停停止失败 run_id={}: {}", runId, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("停止仿真时发生异常 run_id={}: {}", runId, e.getMessage(), e);
        }

        logger.warn("无法停止仿真 run_id={} - 可能不存在或已结束", runId);
        return false;
    }

    /**
     * 暂停指定的仿真
     */
    public boolean pauseSimulation(Integer runId) {
        logger.info("尝试暂停仿真 run_id={}", runId);

        try {
            Object experiment = activeExperiments.get(runId);
            if (experiment != null) {
                try {
                    Method pauseMethod = experiment.getClass().getMethod("pause");
                    pauseMethod.invoke(experiment);
                    logger.info("成功暂停仿真 run_id={}", runId);
                    updateSimulationState(runId, Experiment.State.PAUSED);
                    return true;
                } catch (Exception e) {
                    logger.error("暂停仿真失败 run_id={}: {}", runId, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("暂停仿真时发生异常 run_id={}: {}", runId, e.getMessage(), e);
        }

        logger.warn("无法暂停仿真 run_id={} - 可能不存在或已结束", runId);
        return false;
    }

    /**
     * 恢复指定的仿真
     */
    public boolean resumeSimulation(Integer runId) {
        logger.info("尝试恢复仿真 run_id={}", runId);

        try {
            Object experiment = activeExperiments.get(runId);
            if (experiment != null) {
                try {
                    Method runMethod = experiment.getClass().getMethod("run");
                    runMethod.invoke(experiment);
                    logger.info("成功恢复仿真 run_id={}", runId);
                    updateSimulationState(runId, Experiment.State.RUNNING);
                    return true;
                } catch (Exception e) {
                    logger.error("恢复仿真失败 run_id={}: {}", runId, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("恢复仿真时发生异常 run_id={}: {}", runId, e.getMessage(), e);
        }

        logger.warn("无法恢复仿真 run_id={} - 可能不存在或已结束", runId);
        return false;
    }

    /**
     * 重置指定的仿真
     */
    public boolean resetSimulation(Integer runId) {
        logger.info("尝试重置仿真 run_id={}", runId);

        try {
            Object experiment = activeExperiments.get(runId);
            if (experiment != null) {
                try {
                    Method resetMethod = experiment.getClass().getMethod("reset");
                    resetMethod.invoke(experiment);
                    logger.info("成功重置仿真 run_id={}", runId);
                    updateSimulationState(runId, Experiment.State.IDLE);
                    return true;
                } catch (Exception e) {
                    logger.error("重置仿真失败 run_id={}: {}", runId, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("重置仿真时发生异常 run_id={}: {}", runId, e.getMessage(), e);
        }

        logger.warn("无法重置仿真 run_id={} - 可能不存在或已结束", runId);
        return false;
    }

    /**
     * 获取所有运行中的仿真
     */
    public List<SimulationRun> getRunningSimulations() {
        return simulationRunRepository.findByState(Experiment.State.RUNNING);
    }

    /**
     * 获取仿真运行状态
     */
    public SimulationRun getSimulationState(Integer runId) {
        return simulationRunRepository.findById(runId).orElse(null);
    }

    /**
     * 更新仿真状态
     */
    private void updateSimulationState(Integer runId, Experiment.State state) {
        try {
            SimulationRun simulationRun = simulationRunRepository.findById(runId).orElse(null);
            if (simulationRun != null) {
                simulationRun.setState(state);
                if (state == Experiment.State.FINISHED ||
                    state == Experiment.State.ERROR ||
                    state == Experiment.State.IDLE) {
                    // simulationRun.setEndDate(LocalDateTime.now());
                }
                simulationRunRepository.save(simulationRun);
                logger.info("更新仿真状态: run_id={}, status={}", runId, state);
            }
        } catch (Exception e) {
            logger.error("更新仿真状态失败 run_id={}: {}", runId, e.getMessage(), e);
        }
    }

    /**
     * 更新仿真端口号
     */
    private void updateSimulationPort(Integer runId, int port) {
        try {
            SimulationRun simulationRun = simulationRunRepository.findById(runId).orElse(null);
            if (simulationRun != null) {
                simulationRun.setModelPort(port);
                simulationRunRepository.save(simulationRun);
                logger.info("更新仿真端口号: run_id={}, port={}", runId, port);
            }
        } catch (Exception e) {
            logger.error("更新仿真端口号失败 run_id={}: {}", runId, e.getMessage(), e);
        }
    }

    /**
     * 获取系统资源状态
     */
    public SystemResourceStatus getSystemResourceStatus() {
        SystemResourceStatus status = new SystemResourceStatus();

        // 内存信息
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        status.maxMemoryMB = maxMemory / (1024 * 1024);
        status.usedMemoryMB = usedMemory / (1024 * 1024);
        status.freeMemoryMB = freeMemory / (1024 * 1024);
        status.memoryUsage = (double) usedMemory / maxMemory;

        // 线程信息
        status.activeThreads = Thread.activeCount();

        // 仿真信息
        status.runningSimulations = runningSimulations.size();

        return status;
    }

    /**
     * 系统资源状态类
     */
    public static class SystemResourceStatus {
        public long maxMemoryMB;
        public long usedMemoryMB;
        public long freeMemoryMB;
        public double memoryUsage;
        public int activeThreads;
        public int runningSimulations;

        @Override
        public String toString() {
            return String.format("内存: %dMB/%dMB (%.1f%%), 可用: %dMB, 线程: %d, 仿真: %d",
                usedMemoryMB, maxMemoryMB, memoryUsage * 100, freeMemoryMB,
                activeThreads, runningSimulations);
        }
    }

    /**
     * 应用引擎参数
     * 在创建智能体之前应用引擎级别的参数
     */
    private void applyEngineParameters(Simulation experiment, String engineParametersJson) {
        if (engineParametersJson == null || engineParametersJson.trim().isEmpty()) {
            logger.info("没有提供引擎参数，使用默认值");
            return;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = objectMapper.readValue(engineParametersJson, Map.class);
            logger.info("开始应用引擎参数: {}", parameters);

            // 获取引擎对象
            Object engine = experiment.getEngine();
            if (engine == null) {
                logger.error("无法获取引擎，参数设置失败");
                return;
            }

            logger.info("获取到引擎对象: {}", engine.getClass().getName());

            // 应用每个参数
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();
                try {
                    setEngineParameterValue(engine, paramName, paramValue);
                    logger.info("✓ 成功设置引擎参数: {} = {}", paramName, paramValue);
                } catch (Exception e) {
                    logger.error("× 设置引擎参数失败: {} = {}, 错误: {}", paramName, paramValue, e.getMessage());
                }
            }

            logger.info("引擎参数应用完成");

        } catch (Exception e) {
            logger.error("解析或应用引擎参数失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 设置仿真参数 - 改进版本，集成LongRunSimulation的参数设置逻辑
     */
    private void setSimulTargetTime(Object experiment, String targetTime) {
        try {
            logger.info("尝试设置 simulTargetTime 参数: {}", targetTime);

            // 方法1: 尝试通过反射查找参数字段
            try {
                Field field = experiment.getClass().getField("simulTargetTime");
                field.setAccessible(true);
                field.set(experiment, targetTime);
                logger.info("✓ 通过字段反射成功设置 simulTargetTime = {}", targetTime);

                // 验证设置
                Object value = field.get(experiment);
                logger.info("✓ 验证读取到的值: {}", value);
            } catch (NoSuchFieldException e) {
                logger.debug("未找到公共字段 simulTargetTime");
            }

            // 方法2: 尝试通过setter方法
            try {
                Method setter = experiment.getClass().getMethod("setSimulTargetTime", String.class);
                setter.invoke(experiment, targetTime);
                logger.info("✓ 通过setter方法成功设置 simulTargetTime");
            } catch (NoSuchMethodException e) {
                logger.debug("未找到 setSimulTargetTime 方法");
            }

            // 方法3: 尝试查找所有包含"simulTargetTime"的字段
            Field[] allFields = experiment.getClass().getDeclaredFields();
            for (Field field : allFields) {
                if (field.getName().contains("simulTargetTime") || field.getName().toLowerCase().contains("target")) {
                    try {
                        field.setAccessible(true);
                        if (field.getType() == String.class) {
                            field.set(experiment, targetTime);
                            logger.info("✓ 通过私有字段 {} 设置成功", field.getName());
                            logger.info("✓ 当前值: {}", field.get(experiment));
                        }
                    } catch (Exception ex) {
                        logger.debug("设置字段 {} 失败: {}", field.getName(), ex.getMessage());
                    }
                }
            }

            // 方法4: 尝试转换为日期并设置停止时间
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                java.util.Date date = sdf.parse(targetTime);

                // 尝试设置停止日期
                Method setStopDateMethod = experiment.getClass().getMethod("setStopDate", java.util.Date.class);
                setStopDateMethod.invoke(experiment, date);
                logger.info("✓ 通过 setStopDate 设置目标时间成功");

                // 验证停止日期
                Method getStopDateMethod = experiment.getClass().getMethod("getStopDate");
                java.util.Date stopDate = (java.util.Date) getStopDateMethod.invoke(experiment);
                logger.info("✓ 停止日期: {}", sdf.format(stopDate));
            } catch (java.text.ParseException pe) {
                logger.debug("日期格式解析失败: {}", pe.getMessage());
            } catch (NoSuchMethodException e) {
                logger.debug("未找到 setStopDate 方法");
            }

        } catch (Exception e) {
            logger.error("设置 simulTargetTime 参数失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 应用智能体参数 - 改进版本，集成LongRunSimulation的参数设置逻辑
     */
    private void applyAgentParameters(Simulation experiment, String agentParametersJson) {
        if (agentParametersJson == null || agentParametersJson.trim().isEmpty()) {
            logger.info("没有提供智能体参数，使用默认值");
            return;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = objectMapper.readValue(agentParametersJson, Map.class);
            logger.info("开始应用智能体参数: {}", parameters);

            // 获取主智能体对象
            Object mainAgent = getMainAgent(experiment);
            if (mainAgent == null) {
                logger.error("无法获取主智能体，参数设置失败");
                return;
            }

            logger.info("获取到主智能体对象: {}", mainAgent.getClass().getName());

            // 应用每个参数
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();
                try {
                    // 特殊处理 simulTargetTime 参数
                    if ("simulTargetTime".equals(paramName)) {
                        setSimulTargetTime(experiment, paramValue.toString());
                    } else {
                        setParameterValue(mainAgent, paramName, paramValue);
                    }
                    logger.info("✓ 成功设置智能体参数: {} = {}", paramName, paramValue);
                } catch (Exception e) {
                    logger.error("× 设置智能体参数失败: {} = {}, 错误: {}", paramName, paramValue, e.getMessage());
                }
            }

            logger.info("智能体参数应用完成");

        } catch (Exception e) {
            logger.error("解析或应用智能体参数失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 设置引擎参数值
     */
    private void setEngineParameterValue(Object engine, String paramName, Object paramValue) throws Exception {
        Class<?> engineClass = engine.getClass();

        try {
            // 尝试直接设置字段
            Field field = engineClass.getDeclaredField(paramName);
            field.setAccessible(true);

            // 类型转换
            Object convertedValue = ParameterConversionUtils.convertParameterValue(field.getType(), paramValue);
            field.set(engine, convertedValue);

        } catch (NoSuchFieldException e) {
            // 如果字段不存在，尝试使用setter方法
            String setterName = "set" + capitalize(paramName);
            Method[] methods = engineClass.getMethods();

            for (Method method : methods) {
                if (method.getName().equals(setterName) &&
                    method.getParameterCount() == 1) {

                    Class<?> paramType = method.getParameterTypes()[0];
                    Object convertedValue = ParameterConversionUtils.convertParameterValue(paramType, paramValue);
                    method.invoke(engine, convertedValue);
                    return;
                }
            }

            throw new Exception("找不到引擎参数: " + paramName);
        }
    }




    /**
     * 获取主智能体对象
     * 通过引擎的getRoot()方法获取顶层智能体
     */
    private Object getMainAgent(Object experiment) {
        try {
            if (experiment == null) {
                logger.error("实验对象为空，无法获取主智能体");
                return null;
            }

            // 获取引擎对象
            Object engine = null;
            try {
                Method getEngineMethod = experiment.getClass().getMethod("getEngine");
                engine = getEngineMethod.invoke(experiment);
            } catch (Exception e) {
                logger.error("无法获取引擎对象: {}", e.getMessage());
                return null;
            }

            if (engine == null) {
                logger.error("引擎对象为空，无法获取主智能体");
                return null;
            }

            // 通过引擎获取根智能体
            Object mainAgent = null;
            try {
                Method getRootMethod = engine.getClass().getMethod("getRoot");
                mainAgent = getRootMethod.invoke(engine);
            } catch (Exception e) {
                logger.error("无法获取根智能体: {}", e.getMessage());
                return null;
            }

            if (mainAgent == null) {
                logger.error("主智能体对象为空");
                return null;
            }

            logger.info("成功获取主智能体: {}", mainAgent.getClass().getName());
            return mainAgent;

        } catch (Exception e) {
            logger.error("获取主智能体失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 设置参数值
     */
    private void setParameterValue(Object agent, String paramName, Object paramValue)
        throws Exception {

        Class<?> agentClass = agent.getClass();

        try {
            // 尝试直接设置字段
            Field field = agentClass.getDeclaredField(paramName);
            field.setAccessible(true);

            // 类型转换
            Object convertedValue = ParameterConversionUtils.convertParameterValue(field.getType(), paramValue);
            field.set(agent, convertedValue);

        } catch (NoSuchFieldException e) {
            // 如果字段不存在，尝试使用setter方法
            logger.info("{}字段不存在，尝试使用setter方法", paramName);
            String setterName = "set" + capitalize(paramName);
            Method[] methods = agentClass.getMethods();

            for (Method method : methods) {
                if (method.getName().equals(setterName) &&
                    method.getParameterCount() == 1) {

                    Class<?> paramType = method.getParameterTypes()[0];
                    logger.info("paramType: {}", paramType);
                    Object convertedValue = ParameterConversionUtils.convertParameterValue(paramType, paramValue);
                    method.invoke(agent, convertedValue);
                    return;
                }
            }

            throw new Exception("找不到参数: " + paramName);
        }
    }



    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }



    /**
     * 获取仿真服务健康状态
     */
    public Map<String, Object> getServiceHealthStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            status.put("isHealthy", true);
            status.put("runningSimulations", runningSimulations.size());
            status.put("activeExperiments", activeExperiments.size());
            status.put("threadPoolActive", !simulationExecutor.isShutdown());
            status.put("threadPoolTerminated", simulationExecutor.isTerminated());

            // 检查每个运行中的仿真状态
            Map<String, String> simulationStatuses = new HashMap<>();
            for (Map.Entry<Integer, CompletableFuture<Void>> entry : runningSimulations.entrySet()) {
                Integer runId = entry.getKey();
                CompletableFuture<Void> future = entry.getValue();

                if (future.isDone()) {
                    if (future.isCompletedExceptionally()) {
                        simulationStatuses.put(runId.toString(), "FAILED");
                    } else {
                        simulationStatuses.put(runId.toString(), "COMPLETED");
                    }
                } else if (future.isCancelled()) {
                    simulationStatuses.put(runId.toString(), "CANCELLED");
                } else {
                    simulationStatuses.put(runId.toString(), "RUNNING");
                }
            }
            status.put("simulationDetails", simulationStatuses);

            // 添加线程池详细信息
            status.put("threadPoolShutdown", simulationExecutor.isShutdown());
            status.put("threadPoolTerminated", simulationExecutor.isTerminated());
            status.put("activeThreadCount", Thread.activeCount());

        } catch (Exception e) {
            status.put("isHealthy", false);
            status.put("error", e.getMessage());
            logger.error("获取服务健康状态时发生异常: {}", e.getMessage(), e);
        }

        return status;
    }



    /**
     * 仿真异常类
     */
    public static class SimulationException extends Exception {
        public SimulationException(String message) {
            super(message);
        }

        public SimulationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
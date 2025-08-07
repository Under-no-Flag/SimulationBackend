package com.simulation.demo.service;

// AnyLogic imports - 现在可以直接导入了！
import com.anylogic.engine.gui.ExperimentHost;
import com.anylogic.engine.gui.IExperimentHost;
import nanjingdong.Simulation;

import com.simulation.demo.entity.SimulationRun;
import com.simulation.demo.entity.SimulationStatus;
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

    // 线程池管理 - 使用守护线程防止JVM关闭
    private final ExecutorService simulationExecutor = Executors.newFixedThreadPool(5, new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "SimulationWorker-" + threadNumber.getAndIncrement());
            t.setDaemon(true); // 设置为守护线程
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
            logger.info("应用程序关闭，开始清理仿真资源...");
            shutdownGracefully();
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

            logger.info("✓ AnyLogic无界面模式配置完成");

        } catch (Exception e) {
            logger.warn("配置无界面模式时发生异常: {}", e.getMessage());
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
            simulationRun.setStatus(SimulationStatus.RUNNING);
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
                        updateSimulationStatus(runId, SimulationStatus.FAILED);
                    } else {
                        logger.error("仿真失败 run_id={}: {}", runId, throwable.getMessage());
                        updateSimulationStatus(runId, SimulationStatus.FAILED);
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

            return CompletableFuture.completedFuture(runId);

        } catch (Exception e) {
            logger.error("启动仿真失败: {}", e.getMessage(), e);
            throw new RuntimeException("启动仿真失败: " + e.getMessage(), e);
        }
    }
    /**
     * 创建并启动仿真（Controller接口兼容方法）
     */
    public SimulationRun createAndStartSimulation(String modelName, String engineParameters, String agentParameters, String description) {
        logger.info("创建并启动仿真: modelName={}, engineParameters={}, agentParameters={}, description={}",
                   modelName, engineParameters, agentParameters, description);

        try {
            // 检查并发限制
            if (runningSimulations.size() >= maxConcurrentSimulations) {
                throw new RuntimeException("已达到最大并发仿真数量限制: " + maxConcurrentSimulations);
            }

            // 创建仿真运行记录
            SimulationRun simulationRun = new SimulationRun();
            simulationRun.setModelName(modelName != null ? modelName : "NanJingDong");
            simulationRun.setStartDate(LocalDateTime.now());
            simulationRun.setStatus(SimulationStatus.PENDING);
            simulationRun.setEngineParameters(engineParameters);
            simulationRun.setAgentParameters(agentParameters);
            simulationRun.setDescription(description);
            simulationRun = simulationRunRepository.save(simulationRun);

            Integer runId = simulationRun.getRunId();
            logger.info("成功创建仿真运行记录，获取 run_id = {}", runId);

            // 异步启动仿真
            startSimulationAsync(runId);

            // 更新状态为运行中
            simulationRun.setStatus(SimulationStatus.RUNNING);
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
                    updateSimulationStatus(runId, SimulationStatus.FAILED);
                } else {
                    logger.error("仿真失败 run_id={}: {}", runId, throwable.getMessage());
                    updateSimulationStatus(runId, SimulationStatus.FAILED);
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
     * 在独立线程中运行仿真
     */
    private Object runSimulationInThread(Integer runId) throws InterruptedException {
        logger.info("开始在线程中运行仿真 run_id={}", runId);

        try {
            // 获取仿真参数
            SimulationRun simulationRun = simulationRunRepository.findById(runId).orElse(null);
            String engineParameters = simulationRun != null ? simulationRun.getEngineParameters() : null;
            String agentParameters = simulationRun != null ? simulationRun.getAgentParameters() : null;

            // 创建仿真实例
            logger.info("正在创建 Simulation 实例...");
            Simulation experiment = new Simulation();
            activeExperiments.put(runId, experiment);
            logger.info("✓ Simulation 实例创建成功");

            // 应用引擎参数和智能体参数
            applyEngineParameters(experiment, engineParameters);
            applyAgentParameters(experiment, agentParameters);

            // 根据配置决定是否获取模型端口号
            if (webServerEnabled) {
                logger.info("=== 获取模型端口号 ===");
                int port = getModelPort(experiment);
                if (port > 0) {
                    logger.info("✓✓✓ 最终获取到的端口号: {} ✓✓✓", port);

                    // 更新数据库记录
                    updateSimulationPort(runId, port);
                } else {
                    logger.warn("× 未能获取到有效的端口号");
                }
            } else {
                logger.info("Web服务器已禁用，跳过端口获取");
            }

            // 启动仿真
            logger.info("开始运行仿真...");
            long startTime = System.currentTimeMillis();

            // 在隔离的线程中启动仿真（无界面模式）
            CompletableFuture<Void> simulationRunFuture = CompletableFuture.runAsync(() -> {
                Thread currentThread = Thread.currentThread();
                String originalName = currentThread.getName();
                currentThread.setName("Simulation-" + runId);

                try {
                    logger.info("仿真开始运行 run_id={}, 线程: {} (无界面模式)", runId, currentThread.getName());

                    // 设置线程中断处理
                    if (Thread.currentThread().isInterrupted()) {
                        logger.warn("仿真线程被中断，停止运行 run_id={}", runId);
                        return;
                    }

                    // 启动无界面仿真
                    runHeadlessSimulation(experiment, runId);
                    logger.info("仿真正常结束 run_id={}", runId);


                } catch (Exception e) {
                    logger.error("仿真运行异常 run_id={}: {}", runId, e.getMessage(), e);
                    updateSimulationStatus(runId, SimulationStatus.FAILED);
                } finally {
                    currentThread.setName(originalName);
                    logger.info("仿真线程清理完成 run_id={}", runId);
                }
            }, simulationExecutor).exceptionally(throwable -> {
                logger.error("仿真执行出现严重错误 run_id={}: {}", runId, throwable.getMessage(), throwable);
                updateSimulationStatus(runId, SimulationStatus.FAILED);
                return null;
            });

            // 监控仿真状态 - 改进版本，支持早期退出和异常恢复
            boolean simulationCompleted = false;
            for (int i = 0; i < 12 && !simulationCompleted; i++) { // 监控1分钟
                try {
                    Thread.sleep(5000); // 等待5秒

                    // 检查当前线程是否被中断
                    if (Thread.currentThread().isInterrupted()) {
                        logger.warn("监控线程被中断，停止监控 run_id={}", runId);
                        break;
                    }

                    // 检查仿真是否已经完成
                    if (simulationRunFuture.isDone()) {
                        logger.info("仿真执行已完成 run_id={}", runId);
                        simulationCompleted = true;
                        break;
                    }

                    long currentTime = System.currentTimeMillis();
                    long elapsed = currentTime - startTime;

                    logger.info("仿真运行时间: {} 秒 (run_id={})", elapsed / 1000, runId);

                    // 安全地获取当前仿真时间
                    try {
                        double simTime = getCurrentSimulationTime(experiment);
                        if (simTime >= 0) {
                            logger.info("当前仿真时间: {} (run_id={})", simTime, runId);
                        }
                    } catch (Exception e) {
                        logger.debug("获取仿真时间失败: {}", e.getMessage());
                        // 不影响主监控流程
                    }

                    logger.info("监控中... ({}/12) run_id={}", i + 1, runId);


                } catch (Exception e) {
                    logger.error("监控过程中发生异常 run_id={}: {}", runId, e.getMessage(), e);
                    // 继续监控，不因单次异常而停止
                }
            }

            // 标记仿真完成
            updateSimulationStatus(runId, SimulationStatus.COMPLETED);
            logger.info("仿真监控结束 run_id={}", runId);

            return experiment;


        } catch (Exception e) {
            logger.error("仿真线程执行失败 run_id={}: {}", runId, e.getMessage(), e);
            updateSimulationStatus(runId, SimulationStatus.FAILED);

            // 不抛出RuntimeException，避免影响主应用程序
            logger.error("仿真执行失败，但不影响后端服务继续运行 run_id={}", runId);
            return null; // 返回null表示失败，但不中断应用程序
        }
    }

    /**
     * 运行无界面仿真
     */
    private void runHeadlessSimulation(Simulation experiment, Integer runId) {
        try {
            logger.info("启动无界面仿真 run_id={}", runId);

            // 确保在当前线程中也设置无界面模式
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

            // 设置线程级别的系统属性
            System.setProperty("java.awt.headless", "true");

            // 获取引擎并设置引擎参数
            Object engine = experiment.getEngine();
            if (engine != null) {
                logger.info("获取到引擎: {}", engine.getClass().getName());

                // 这里可以添加引擎特定的设置
                // 例如：engine.setStartDate(), engine.setStopDate(), engine.setRealTimeScale()
            }

            // 启动仿真到能获取agent的状态
            experiment.step();
            experiment.pause();

            // 获取主智能体并设置参数
            Object mainAgent = experiment.getEngine().getRoot();
            if (mainAgent != null) {
                logger.info("获取到主智能体: {}", mainAgent.getClass().getName());

                // 这里可以添加智能体特定的设置
                // 例如：mainAgent.setParameter("simulTargetTime", value, true)
            }

            // 使用简单的run方式启动仿真，避免ExperimentHost相关的浏览器启动
            experiment.run();

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
     * 停止指定的仿真 - 改进版本，安全清理资源
     */
    public boolean stopSimulation(Integer runId) {
        logger.info("尝试停止仿真 run_id={}", runId);

        try {
            CompletableFuture<Void> task = runningSimulations.get(runId);
            Object experiment = activeExperiments.get(runId);

            boolean stopped = false;

            // 尝试停止CompletableFuture任务
            if (task != null) {
                boolean cancelled = task.cancel(true);
                if (cancelled) {
                    logger.info("成功取消仿真任务 run_id={}", runId);
                    stopped = true;
                }
            }

            // 尝试停止AnyLogic实验
            if (experiment != null) {
                try {
                    // 尝试调用停止方法（如果存在）
                    Method stopMethod = experiment.getClass().getMethod("stop");
                    stopMethod.invoke(experiment);
                    logger.info("成功调用实验停止方法 run_id={}", runId);
                    stopped = true;
                } catch (Exception e) {
                    logger.debug("无法调用实验停止方法 run_id={}: {}", runId, e.getMessage());
                }
            }

            // 清理资源
            runningSimulations.remove(runId);
            activeExperiments.remove(runId);

            if (stopped) {
                updateSimulationStatus(runId, SimulationStatus.CANCELLED);
                logger.info("成功停止仿真 run_id={}", runId);
                return true;
            }

        } catch (Exception e) {
            logger.error("停止仿真时发生异常 run_id={}: {}", runId, e.getMessage(), e);
            // 即使发生异常，也要清理资源
            runningSimulations.remove(runId);
            activeExperiments.remove(runId);
        }

        logger.warn("无法停止仿真 run_id={} - 可能已经结束或不存在", runId);
        return false;
    }

    /**
     * 获取所有运行中的仿真
     */
    public List<SimulationRun> getRunningSimulations() {
        return simulationRunRepository.findByStatus(SimulationStatus.RUNNING);
    }

    /**
     * 获取仿真运行状态
     */
    public SimulationRun getSimulationStatus(Integer runId) {
        return simulationRunRepository.findById(runId).orElse(null);
    }

    /**
     * 更新仿真状态
     */
    private void updateSimulationStatus(Integer runId, SimulationStatus status) {
        try {
            SimulationRun simulationRun = simulationRunRepository.findById(runId).orElse(null);
            if (simulationRun != null) {
                simulationRun.setStatus(status);
                if (status == SimulationStatus.COMPLETED ||
                    status == SimulationStatus.FAILED ||
                    status == SimulationStatus.CANCELLED) {
                    simulationRun.setEndDate(LocalDateTime.now());
                }
                simulationRunRepository.save(simulationRun);
                logger.info("更新仿真状态: run_id={}, status={}", runId, status);
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
     * 应用引擎参数（ExperimentSimulation引擎参数）
     */
    private void applyEngineParameters(Simulation experiment, String engineParametersJson) {
        if (engineParametersJson == null || engineParametersJson.trim().isEmpty()) {
            logger.info("没有提供引擎参数，使用默认值");
            return;
        }

        try {
            // 解析JSON参数
            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = objectMapper.readValue(engineParametersJson, Map.class);

            logger.info("开始应用引擎参数: {}", parameters);

            // 获取引擎
            Object engine = experiment.getEngine();
            if (engine == null) {
                logger.error("无法获取引擎，参数设置失败");
                return;
            }

            // 遍历参数并设置
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();

                try {
                    setEngineParameterValue(engine, paramName, paramValue);
                    logger.info("✓ 成功设置引擎参数: {} = {}", paramName, paramValue);
                } catch (Exception e) {
                    logger.error("× 设置引擎参数失败: {} = {}, 错误: {}",
                        paramName, paramValue, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("解析或应用引擎参数失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 应用智能体参数（Main智能体参数）
     */
    private void applyAgentParameters(Simulation experiment, String agentParametersJson) {
        if (agentParametersJson == null || agentParametersJson.trim().isEmpty()) {
            logger.info("没有提供智能体参数，使用默认值");
            return;
        }

        try {
            // 解析JSON参数
            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = objectMapper.readValue(agentParametersJson, Map.class);

            logger.info("开始应用智能体参数: {}", parameters);

            // 获取主代理
            Object mainAgent = getMainAgent(experiment);
            if (mainAgent == null) {
                logger.error("无法获取主代理，参数设置失败");
                return;
            }

            // 遍历参数并设置
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();

                try {
                    setParameterValue(mainAgent, paramName, paramValue);
                    logger.info("✓ 成功设置智能体参数: {} = {}", paramName, paramValue);
                } catch (Exception e) {
                    logger.error("× 设置智能体参数失败: {} = {}, 错误: {}",
                        paramName, paramValue, e.getMessage());
                }
            }

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
            Object convertedValue = convertParameterValue(field.getType(), paramValue);
            field.set(engine, convertedValue);

        } catch (NoSuchFieldException e) {
            // 如果字段不存在，尝试使用setter方法
            String setterName = "set" + capitalize(paramName);
            Method[] methods = engineClass.getMethods();

            for (Method method : methods) {
                if (method.getName().equals(setterName) &&
                    method.getParameterCount() == 1) {

                    Class<?> paramType = method.getParameterTypes()[0];
                    Object convertedValue = convertParameterValue(paramType, paramValue);
                    method.invoke(engine, convertedValue);
                    return;
                }
            }

            throw new Exception("找不到引擎参数: " + paramName);
        }
    }

    /**
     * 应用仿真参数（保留兼容性）
     */
    private void applySimulationParameters(Object experiment, String parametersJson) {
        if (parametersJson == null || parametersJson.trim().isEmpty()) {
            logger.info("没有提供仿真参数，使用默认值");
            return;
        }

        try {
            // 解析JSON参数
            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = objectMapper.readValue(parametersJson, Map.class);

            logger.info("开始应用仿真参数: {}", parameters);

            // 获取主代理
            Object mainAgent = getMainAgent(experiment);
            if (mainAgent == null) {
                logger.error("无法获取主代理，参数设置失败");
                return;
            }

            // 遍历参数并设置
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();

                try {
                    setParameterValue(mainAgent, paramName, paramValue);
                    logger.info("✓ 成功设置参数: {} = {}", paramName, paramValue);
                } catch (Exception e) {
                    logger.error("× 设置参数失败: {} = {}, 错误: {}",
                        paramName, paramValue, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("解析或应用仿真参数失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取主代理
     */
    private Object getMainAgent(Object experiment) {
        try {
            // 根据你的代码示例
            if (experiment instanceof Simulation) {
                Simulation sim = (Simulation) experiment;
                // 启动仿真到能获取agent的状态
                sim.step();
                sim.pause();
                return sim.getEngine().getRoot();
            }
            return null;
        } catch (Exception e) {
            logger.error("获取主代理失败: {}", e.getMessage(), e);
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
            Object convertedValue = convertParameterValue(field.getType(), paramValue);
            field.set(agent, convertedValue);

        } catch (NoSuchFieldException e) {
            // 如果字段不存在，尝试使用setter方法
            String setterName = "set" + capitalize(paramName);
            Method[] methods = agentClass.getMethods();

            for (Method method : methods) {
                if (method.getName().equals(setterName) &&
                    method.getParameterCount() == 1) {

                    Class<?> paramType = method.getParameterTypes()[0];
                    Object convertedValue = convertParameterValue(paramType, paramValue);
                    method.invoke(agent, convertedValue);
                    return;
                }
            }

            throw new Exception("找不到参数: " + paramName);
        }
    }

    /**
     * 参数类型转换
     */
    private Object convertParameterValue(Class<?> targetType, Object value) {
        if (value == null) return null;

        // 如果类型已经匹配
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        // 类型转换
        String valueStr = value.toString();

        if (targetType == String.class) {
            return valueStr;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.valueOf(valueStr);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.valueOf(valueStr);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.valueOf(valueStr);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.valueOf(valueStr);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.valueOf(valueStr);
        }
        // 可以添加更多类型转换

        logger.warn("不支持的参数类型转换: {} -> {}", value.getClass(), targetType);
        return value;
    }

    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 优雅关闭仿真服务
     */
    public void shutdownGracefully() {
        logger.info("开始优雅关闭仿真服务...");

        try {
            // 停止所有运行中的仿真
            for (Integer runId : new java.util.ArrayList<>(runningSimulations.keySet())) {
                try {
                    logger.info("正在停止仿真 run_id={}", runId);
                    stopSimulation(runId);
                } catch (Exception e) {
                    logger.error("停止仿真失败 run_id={}: {}", runId, e.getMessage());
                }
            }

            // 关闭线程池
            simulationExecutor.shutdown();
            try {
                if (!simulationExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn("仿真线程池未能在30秒内优雅关闭，强制关闭");
                    simulationExecutor.shutdownNow();

                    if (!simulationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                        logger.error("仿真线程池强制关闭失败");
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("等待线程池关闭时被中断");
                simulationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // 清理资源
            runningSimulations.clear();
            activeExperiments.clear();

            logger.info("仿真服务优雅关闭完成");

        } catch (Exception e) {
            logger.error("优雅关闭仿真服务时发生异常: {}", e.getMessage(), e);
        }
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
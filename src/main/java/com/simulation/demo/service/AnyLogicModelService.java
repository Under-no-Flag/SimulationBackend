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

import java.io.File;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.List;
import java.util.Map;

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

    // 线程池管理
    private final ExecutorService simulationExecutor = Executors.newFixedThreadPool(5);

    // 运行中的仿真跟踪
    private final Map<Integer, CompletableFuture<Void>> runningSimulations = new ConcurrentHashMap<>();
    private final Map<Integer, Object> activeExperiments = new ConcurrentHashMap<>();
    @Value("${anylogic.model.file:model.jar}")
    private String modelFileName;

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
    public SimulationRun createAndStartSimulation(String modelName, String parameters, String description) {
        logger.info("创建并启动仿真: modelName={}, parameters={}, description={}", modelName, parameters, description);

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
            simulationRun.setSimulationParameters(parameters);
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
    private Object runSimulationInThread(Integer runId) {
        logger.info("开始在线程中运行仿真 run_id={}", runId);

        try {
            // 创建仿真实例
            logger.info("正在创建 Simulation 实例...");
            Simulation experiment = new Simulation();
            activeExperiments.put(runId, experiment);
            logger.info("✓ Simulation 实例创建成功");

            // 获取模型端口号
            logger.info("=== 获取模型端口号 ===");
            int port = getModelPort(experiment);
            if (port > 0) {
                logger.info("✓✓✓ 最终获取到的端口号: {} ✓✓✓", port);

                // 更新数据库记录
                updateSimulationPort(runId, port);
            } else {
                logger.warn("× 未能获取到有效的端口号");
            }

            // 启动仿真
            logger.info("开始运行仿真...");
            long startTime = System.currentTimeMillis();

            // 在后台启动仿真
            CompletableFuture.runAsync(() -> {
                try {
                    experiment.run();
                } catch (Exception e) {
                    logger.error("仿真运行异常 run_id={}: {}", runId, e.getMessage(), e);
                }
            });

            // 监控仿真状态（简化版，运行1分钟）
            for (int i = 0; i < 12; i++) { // 监控1分钟
                Thread.sleep(5000); // 等待5秒
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - startTime;

                logger.info("仿真运行时间: {} 秒 (run_id={})", elapsed / 1000, runId);

                // 尝试获取当前仿真时间
                try {
                    // 注意：避免使用已弃用的方法
                    double simTime = getCurrentSimulationTime(experiment);
                    logger.info("当前仿真时间: {} (run_id={})", simTime, runId);
                } catch (Exception e) {
                    logger.debug("获取仿真时间失败: {}", e.getMessage());
                }

                logger.info("监控中... ({}/12) run_id={}", i + 1, runId);
            }

            // 标记仿真完成
            updateSimulationStatus(runId, SimulationStatus.COMPLETED);
            logger.info("仿真监控结束 run_id={}", runId);

            return experiment;

        } catch (Exception e) {
            logger.error("仿真线程执行失败 run_id={}: {}", runId, e.getMessage(), e);
            updateSimulationStatus(runId, SimulationStatus.FAILED);
            throw new RuntimeException("仿真执行失败", e);
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

            // 使用匿名类继承ExperimentHost，重写launch方法
            IExperimentHost host = new ExperimentHost(experiment) {
                @Override
                public void launch() {
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
     * 停止指定的仿真
     */
    public boolean stopSimulation(Integer runId) {
        logger.info("尝试停止仿真 run_id={}", runId);

        CompletableFuture<Void> task = runningSimulations.get(runId);
        if (task != null) {
            boolean cancelled = task.cancel(true);
            if (cancelled) {
                runningSimulations.remove(runId);
                activeExperiments.remove(runId);
                updateSimulationStatus(runId, SimulationStatus.CANCELLED);
                logger.info("成功停止仿真 run_id={}", runId);
                return true;
            }
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
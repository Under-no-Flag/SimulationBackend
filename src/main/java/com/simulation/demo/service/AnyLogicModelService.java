package com.simulation.demo.service;

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
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AnyLogicModelService {

    private static final Logger logger = LoggerFactory.getLogger(AnyLogicModelService.class);

    @Autowired
    private SimulationRunRepository simulationRunRepository;

    @Value("${anylogic.model.dataset:dataset/selected_cameras_20250531.xlsx}")
    private String datasetPath;

    @Value("${anylogic.simulation.stoptime:3600}")
    private double defaultStopTime;

    // 存储运行中的仿真实例
    private final ConcurrentHashMap<Integer, Object> runningSimulations = new ConcurrentHashMap<>();

    // 专用线程池用于运行仿真
    private final ExecutorService simulationExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "AnyLogic-Simulation-" + System.currentTimeMillis());
        t.setDaemon(true); // 设置为守护线程
        return t;
    });

    // 系统资源监控
    private final ExecutorService resourceMonitorExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Resource-Monitor");
        t.setDaemon(true);
        return t;
    });

    /**
     * 启动AnyLogic模型
     * @param simulationRun 模拟运行实例
     * @return CompletableFuture<Boolean> 异步执行结果
     */
    @Async
    public CompletableFuture<Boolean> startSimulation(SimulationRun simulationRun) {
        logger.info("开始启动模拟模型，运行ID: {}", simulationRun.getRunId());

        try {
            // 检查数据集文件
            File datasetFile = new File(datasetPath);
            if (!datasetFile.exists()) {
                logger.error("数据集文件不存在: {}", datasetPath);
                updateSimulationStatus(simulationRun, SimulationStatus.FAILED);
                return CompletableFuture.completedFuture(false);
            }

            logger.info("数据集文件存在: {}, 文件大小: {} 字节",
                datasetFile.getAbsolutePath(), datasetFile.length());

            // 更新状态为运行中
            simulationRun.setStatus(SimulationStatus.RUNNING);
            simulationRunRepository.save(simulationRun);

            // 在专用线程中运行仿真，添加超时机制
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                return runSimulationInThread(simulationRun);
            }, simulationExecutor)
            .orTimeout(7200, TimeUnit.SECONDS) // 2小时超时
            .exceptionally(throwable -> {
                if (throwable instanceof TimeoutException) {
                    logger.error("仿真执行超时，运行ID: {}", simulationRun.getRunId());
                    forceStopSimulation(simulationRun.getRunId(), "执行超时自动停止");
                } else {
                    logger.error("仿真执行异常，运行ID: {}, 错误: {}",
                        simulationRun.getRunId(), throwable.getMessage(), throwable);
                }
                updateSimulationStatus(simulationRun, SimulationStatus.FAILED);
                return false;
            });

            return future;

        } catch (Exception e) {
            logger.error("启动模拟时发生异常，运行ID: {}", simulationRun.getRunId(), e);
            updateSimulationStatus(simulationRun, SimulationStatus.FAILED);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * 在专用线程中运行仿真
     */
    private boolean runSimulationInThread(SimulationRun simulationRun) {
        Object experiment = null;
        try {
            logger.info("正在创建仿真实验对象...");

            // 使用反射创建实验对象（因为具体的类名可能会变化）
            Class<?> simulationClass = getSimulationClass();
            logger.info("仿真类名: {}", simulationClass.getName());
            experiment = simulationClass.getDeclaredConstructor().newInstance();


            // 打印 类名
            // 存储运行中的仿真实例
            runningSimulations.put(simulationRun.getRunId(), experiment);

            logger.info("仿真实验对象创建成功，运行ID: {}", simulationRun.getRunId());

            // 尝试设置仿真参数
            configureSimulation(experiment, simulationRun);

            // 启动仿真监控
            startSimulationMonitoring(simulationRun, experiment);

            // 启动系统资源监控
            startSystemResourceMonitoring(simulationRun);

            logger.info("开始运行仿真，运行ID: {}", simulationRun.getRunId());
            long startTime = System.currentTimeMillis();

            // 运行仿真（这是阻塞调用）
            runExperiment(experiment);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            logger.info("仿真完成，运行ID: {}, 执行时间: {} 毫秒",
                simulationRun.getRunId(), duration);

            // 更新状态为完成
            updateSimulationStatus(simulationRun, SimulationStatus.COMPLETED);
            return true;

        } catch (InterruptedException e) {
            logger.warn("仿真被中断，运行ID: {}", simulationRun.getRunId());
            updateSimulationStatus(simulationRun, SimulationStatus.CANCELLED);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            logger.error("仿真执行失败，运行ID: {}", simulationRun.getRunId(), e);
            updateSimulationStatus(simulationRun, SimulationStatus.FAILED);
            return false;
        } finally {
            // 清理资源
            if (experiment != null) {
                runningSimulations.remove(simulationRun.getRunId());
                cleanupExperiment(experiment);
                logger.info("清理仿真资源，运行ID: {}", simulationRun.getRunId());
            }
        }
    }

    /**
     * 获取仿真类
     */
    private Class<?> getSimulationClass() throws ClassNotFoundException {
        // 尝试多个可能的类名
        String[] possibleClassNames = {
            "nanjingdong.Simulation",
            "nanjingdong.Main",
            "Simulation",
            "Main"
        };

        for (String className : possibleClassNames) {
            try {
                logger.info("尝试加载类: {}", className);
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                logger.debug("类 {} 不存在，尝试下一个", className);
            }
        }

        throw new ClassNotFoundException("找不到仿真类，尝试了: " + String.join(", ", possibleClassNames));
    }

    /**
     * 配置仿真参数
     */
    private void configureSimulation(Object experiment, SimulationRun simulationRun) {
        try {
            // 尝试设置停止时间
            setStopTime(experiment, defaultStopTime);

            // 解析并应用自定义参数
            if (simulationRun.getSimulationParameters() != null && !simulationRun.getSimulationParameters().trim().isEmpty()) {
                logger.info("应用仿真参数: {}", simulationRun.getSimulationParameters());
                // 这里可以解析JSON参数并设置到仿真中
                applyCustomParameters(experiment, simulationRun.getSimulationParameters());
            }

        } catch (Exception e) {
            logger.warn("配置仿真参数时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 设置仿真停止时间
     */
    private void setStopTime(Object experiment, double stopTime) {
        try {
            java.lang.reflect.Method setStopTimeMethod = experiment.getClass().getMethod("setStopTime", double.class);
            setStopTimeMethod.invoke(experiment, stopTime);
            logger.info("设置仿真停止时间: {} 秒", stopTime);
        } catch (Exception e) {
            logger.debug("无法设置停止时间: {}", e.getMessage());
        }
    }

    /**
     * 应用自定义参数
     */
    private void applyCustomParameters(Object experiment, String parameters) {
        // 这里可以实现JSON参数解析和应用逻辑
        logger.debug("应用自定义参数: {}", parameters);
    }

    /**
     * 启动仿真监控
     */
    private void startSimulationMonitoring(SimulationRun simulationRun, Object experiment) {
        // 在另一个线程中监控仿真状态
        Thread monitorThread = new Thread(() -> {
            try {
                monitorSimulation(simulationRun, experiment);
            } catch (Exception e) {
                logger.error("仿真监控异常: {}", e.getMessage());
            }
        }, "Monitor-" + simulationRun.getRunId());

        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    /**
     * 启动系统资源监控
     */
    private void startSystemResourceMonitoring(SimulationRun simulationRun) {
        resourceMonitorExecutor.submit(() -> {
            try {
                monitorSystemResources(simulationRun);
            } catch (Exception e) {
                logger.error("系统资源监控异常，运行ID: {}, 错误: {}",
                    simulationRun.getRunId(), e.getMessage(), e);
            }
        });
    }

    /**
     * 监控系统资源
     */
    private void monitorSystemResources(SimulationRun simulationRun) {
        Integer runId = simulationRun.getRunId();
        int cycles = 0;

        while (runningSimulations.containsKey(runId)) {
            try {
                Thread.sleep(30000); // 每30秒检查一次
                cycles++;

                // 获取系统资源状态
                SystemResourceStatus resourceStatus = getSystemResourceStatus();

                // 每隔4个周期（2分钟）输出详细信息
                if (cycles % 4 == 0) {
                    logger.info("系统资源监控，运行ID: {}, {}", runId, resourceStatus);
                }

                // 检查内存使用
                if (resourceStatus.memoryUsage > 0.95) {
                    logger.error("内存使用率过高: {:.1f}%, 可能需要停止仿真，运行ID: {}",
                        resourceStatus.memoryUsage * 100, runId);
                    // 可以选择强制停止
                    // forceStopSimulation(runId, "内存不足自动停止");
                    // break;
                } else if (resourceStatus.memoryUsage > 0.85) {
                    logger.warn("内存使用率较高: {:.1f}%, 运行ID: {}",
                        resourceStatus.memoryUsage * 100, runId);
                }

                // 检查可用内存
                if (resourceStatus.freeMemoryMB < 512) {
                    logger.warn("可用内存不足: {}MB, 运行ID: {}",
                        resourceStatus.freeMemoryMB, runId);
                }

            } catch (InterruptedException e) {
                logger.info("系统资源监控被中断，运行ID: {}", runId);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("系统资源监控异常，运行ID: {}, 错误: {}",
                    runId, e.getMessage());
            }
        }

        logger.debug("系统资源监控结束，运行ID: {}", runId);
    }

    /**
     * 获取系统资源状态
     */
    private SystemResourceStatus getSystemResourceStatus() {
        SystemResourceStatus status = new SystemResourceStatus();

        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            status.maxMemoryMB = maxMemory / 1024 / 1024;
            status.usedMemoryMB = usedMemory / 1024 / 1024;
            status.freeMemoryMB = freeMemory / 1024 / 1024;
            status.memoryUsage = (double) usedMemory / maxMemory;

            // 获取当前活跃线程数
            status.activeThreads = Thread.activeCount();

            // 获取运行中的仿真数量
            status.runningSimulations = runningSimulations.size();

        } catch (Exception e) {
            logger.debug("获取系统资源状态失败: {}", e.getMessage());
        }

        return status;
    }

    /**
     * 监控仿真执行 - 改进版
     */
    private void monitorSimulation(SimulationRun simulationRun, Object experiment) {
        int maxMonitorCycles = 240; // 最多监控20分钟（每5秒一次）
        double lastTime = -1;
        int stuckCounter = 0;
        int maxStuckCount = 6; // 连续30秒时间不变认为卡住
        long startTime = System.currentTimeMillis();

        logger.info("开始监控仿真，运行ID: {}", simulationRun.getRunId());

        for (int i = 0; i < maxMonitorCycles; i++) {
            try {
                Thread.sleep(5000); // 每5秒检查一次

                // 检查仿真是否还在运行
                if (!runningSimulations.containsKey(simulationRun.getRunId())) {
                    logger.info("仿真已结束，停止监控，运行ID: {}", simulationRun.getRunId());
                    break;
                }

                // 获取详细状态
                DetailedSimulationStatus status = getDetailedSimulationStatus(experiment);
                status.runTimeSeconds = (System.currentTimeMillis() - startTime) / 1000;

                // 检查是否卡住
                if (status.currentTime > 0 && Math.abs(status.currentTime - lastTime) < 0.001) {
                    stuckCounter++;
                    logger.warn("检测到仿真时间未变化，运行ID: {}, 连续次数: {}/{}, 当前时间: {}",
                        simulationRun.getRunId(), stuckCounter, maxStuckCount, status.currentTime);

                    if (stuckCounter >= maxStuckCount) {
                        logger.error("仿真已卡住超过{}秒，强制停止，运行ID: {}",
                            maxStuckCount * 5, simulationRun.getRunId());
                        forceStopSimulation(simulationRun.getRunId(), "仿真卡住自动停止");
                        break;
                    }
                } else {
                    if (stuckCounter > 0) {
                        logger.info("仿真恢复正常，运行ID: {}", simulationRun.getRunId());
                    }
                    stuckCounter = 0;
                }

                lastTime = status.currentTime;

                // 检查内存使用
                if (status.memoryUsage > 0.9) {
                    logger.warn("内存使用率过高: {:.1f}%, 运行ID: {}",
                        status.memoryUsage * 100, simulationRun.getRunId());
                }

                // 每隔10个周期（50秒）输出详细信息
                if (i % 10 == 0 || stuckCounter > 0) {
                    logger.info("仿真监控报告，运行ID: {}, 监控周期: {}, {}",
                        simulationRun.getRunId(), i + 1, status);
                } else {
                    logger.debug("仿真运行中，运行ID: {}, 监控周期: {}, 时间: {}",
                        simulationRun.getRunId(), i + 1, status.currentTime);
                }

            } catch (InterruptedException e) {
                logger.info("仿真监控被中断，运行ID: {}", simulationRun.getRunId());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("监控过程中发生异常，运行ID: {}, 错误: {}",
                    simulationRun.getRunId(), e.getMessage(), e);
            }
        }

        logger.info("监控结束，运行ID: {}", simulationRun.getRunId());
    }

    /**
     * 获取详细的仿真状态
     */
    private DetailedSimulationStatus getDetailedSimulationStatus(Object experiment) {
        DetailedSimulationStatus status = new DetailedSimulationStatus();

        try {
            // 获取当前时间
            java.lang.reflect.Method getTimeMethod = experiment.getClass().getMethod("getTime");
            status.currentTime = (Double) getTimeMethod.invoke(experiment);
        } catch (Exception e) {
            logger.debug("无法获取仿真时间: {}", e.getMessage());
        }

        try {
            // 获取步数
            java.lang.reflect.Method getStepMethod = experiment.getClass().getMethod("getStep");
            status.step = (Long) getStepMethod.invoke(experiment);
        } catch (Exception e) {
            logger.debug("无法获取仿真步数: {}", e.getMessage());
        }

        try {
            // 获取状态
            java.lang.reflect.Method getStateMethod = experiment.getClass().getMethod("getState");
            Object state = getStateMethod.invoke(experiment);
            status.state = state != null ? state.toString() : "UNKNOWN";
        } catch (Exception e) {
            logger.debug("无法获取仿真状态: {}", e.getMessage());
        }

        // 获取系统内存使用情况
        status.memoryUsage = getMemoryUsage();

        return status;
    }



    /**
     * 获取内存使用率
     */
    private double getMemoryUsage() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            return (double) usedMemory / maxMemory;
        } catch (Exception e) {
            logger.debug("无法获取内存使用情况: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 强制停止仿真
     */
    private void forceStopSimulation(Integer runId, String reason) {
        try {
            Object experiment = runningSimulations.get(runId);
            if (experiment != null) {
                // 尝试停止仿真
                try {
                    java.lang.reflect.Method stopMethod = experiment.getClass().getMethod("stop");
                    stopMethod.invoke(experiment);
                    logger.info("已调用stop方法，运行ID: {}", runId);
                } catch (Exception e) {
                    logger.warn("调用stop方法失败，运行ID: {}, 错误: {}", runId, e.getMessage());
                }

                // 更新数据库状态
                SimulationRun simulationRun = simulationRunRepository.findById(runId).orElse(null);
                if (simulationRun != null) {
                    simulationRun.setDescription(simulationRun.getDescription() + " [" + reason + "]");
                    updateSimulationStatus(simulationRun, SimulationStatus.CANCELLED);
                }

                // 清理资源
                runningSimulations.remove(runId);
                cleanupExperiment(experiment);

                logger.info("强制停止仿真完成，运行ID: {}, 原因: {}", runId, reason);
            }
        } catch (Exception e) {
            logger.error("强制停止仿真失败，运行ID: {}, 错误: {}", runId, e.getMessage(), e);
        }
    }

    /**
     * 运行实验 - 改进版，包含异常处理
     */
    private void runExperiment(Object experiment) throws Exception {
        try {
            logger.info("开始执行仿真run方法");
            java.lang.reflect.Method runMethod = experiment.getClass().getMethod("run");
            runMethod.invoke(experiment);
            logger.info("仿真run方法执行完成");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            logger.error("仿真执行时发生异常: {}", cause != null ? cause.getClass().getSimpleName() : "Unknown");

            if (cause instanceof RuntimeException) {
                throw new SimulationException("仿真运行时异常: " + cause.getMessage(), cause);
            } else if (cause instanceof Error) {
                throw new SimulationException("仿真系统错误: " + cause.getMessage(), cause);
            } else if (cause instanceof Exception) {
                throw new SimulationException("仿真执行异常: " + cause.getMessage(), cause);
            }
            throw e;
        } catch (NoSuchMethodException e) {
            throw new SimulationException("找不到run方法，可能模型类不正确", e);
        } catch (SecurityException e) {
            throw new SimulationException("安全权限不足，无法调用run方法", e);
        } catch (IllegalAccessException e) {
            throw new SimulationException("无法访问run方法，权限不足", e);
        } catch (Exception e) {
            throw new SimulationException("执行仿真时发生未知异常: " + e.getMessage(), e);
        }
    }

    /**
     * 清理实验资源
     */
    private void cleanupExperiment(Object experiment) {
        try {
            // 尝试调用清理方法
            java.lang.reflect.Method[] methods = experiment.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName();
                if (methodName.equals("stop") || methodName.equals("close") || methodName.equals("dispose")) {
                    if (method.getParameterCount() == 0) {
                        method.invoke(experiment);
                        logger.debug("调用清理方法: {}", methodName);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("清理实验资源时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 创建并启动新的模拟运行
     * @param modelName 模型名称
     * @param parameters 模拟参数
     * @param description 模拟描述
     * @return 模拟运行实例
     */
    public SimulationRun createAndStartSimulation(String modelName, String parameters, String description) {
        logger.info("创建新的模拟运行: {}", modelName);

        SimulationRun simulationRun = new SimulationRun();
        simulationRun.setModelName(modelName);
        simulationRun.setStartDate(LocalDateTime.now());
        simulationRun.setSimulationParameters(parameters);
        simulationRun.setDescription(description);
        simulationRun.setStatus(SimulationStatus.PENDING);

        // 保存到数据库
        simulationRun = simulationRunRepository.save(simulationRun);

        // 异步启动模拟
        startSimulation(simulationRun);

        return simulationRun;
    }

    /**
     * 停止指定的仿真
     */
    public boolean stopSimulation(Integer runId) {
        Object experiment = runningSimulations.get(runId);
        if (experiment != null) {
            try {
                // 尝试停止仿真
                java.lang.reflect.Method stopMethod = experiment.getClass().getMethod("stop");
                stopMethod.invoke(experiment);

                // 更新状态
                SimulationRun simulationRun = simulationRunRepository.findById(runId).orElse(null);
                if (simulationRun != null) {
                    updateSimulationStatus(simulationRun, SimulationStatus.CANCELLED);
                }

                logger.info("仿真已停止，运行ID: {}", runId);
                return true;
            } catch (Exception e) {
                logger.error("停止仿真失败，运行ID: {}", runId, e);
                return false;
            }
        }
        return false;
    }

    /**
     * 获取运行中的仿真数量
     */
    public int getRunningSimulationCount() {
        return runningSimulations.size();
    }

    /**
     * 更新模拟状态
     */
    private void updateSimulationStatus(SimulationRun simulationRun, SimulationStatus status) {
        try {
            simulationRun.setStatus(status);
            if (status == SimulationStatus.COMPLETED || status == SimulationStatus.FAILED || status == SimulationStatus.CANCELLED) {
                simulationRun.setEndDate(LocalDateTime.now());
            }
            simulationRunRepository.save(simulationRun);
        } catch (Exception e) {
            logger.error("更新模拟状态失败，运行ID: {}", simulationRun.getRunId(), e);
        }
    }

    /**
     * 检查模型文件是否存在
     */
    public boolean isModelFileExists() {
        File modelFile = new File("model.jar");
        File datasetFile = new File(datasetPath);

        boolean modelExists = modelFile.exists();
        boolean datasetExists = datasetFile.exists();

        logger.info("模型文件检查 - model.jar: {}, 数据集: {}", modelExists, datasetExists);

        return modelExists && datasetExists;
    }

    /**
     * 关闭服务时清理资源
     */
    public void shutdown() {
        logger.info("正在关闭 AnyLogicModelService...");

        // 停止所有运行中的仿真
        runningSimulations.forEach((runId, experiment) -> {
            try {
                stopSimulation(runId);
            } catch (Exception e) {
                logger.error("关闭仿真失败，运行ID: {}", runId, e);
            }
        });

        // 关闭线程池
        simulationExecutor.shutdown();
        resourceMonitorExecutor.shutdown();

        try {
            if (!simulationExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                simulationExecutor.shutdownNow();
            }
            if (!resourceMonitorExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                resourceMonitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            simulationExecutor.shutdownNow();
            resourceMonitorExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("AnyLogicModelService 已关闭");
    }

    /**
     * 仿真状态详细信息类
     */
    private static class DetailedSimulationStatus {
        double currentTime = -1;
        long step = -1;
        String state = "UNKNOWN";
        double memoryUsage = 0;
        long runTimeSeconds = 0;

        @Override
        public String toString() {
            return String.format("状态=%s, 时间=%.2f, 步数=%d, 内存使用=%.1f%%, 运行时长=%ds",
                state, currentTime, step, memoryUsage * 100, runTimeSeconds);
        }
    }

    /**
     * 系统资源状态类
     */
    private static class SystemResourceStatus {
        long maxMemoryMB = 0;
        long usedMemoryMB = 0;
        long freeMemoryMB = 0;
        double memoryUsage = 0;
        int activeThreads = 0;
        int runningSimulations = 0;

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

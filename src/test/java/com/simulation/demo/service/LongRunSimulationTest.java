package com.simulation.demo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 长时间运行仿真测试
 * 这个测试类用于测试实际的 AnyLogic 模型执行
 * 只有在模型文件存在时才会执行
 */
public class LongRunSimulationTest {

    private static final Logger logger = LoggerFactory.getLogger(LongRunSimulationTest.class);

    /**
     * 测试方法 - 检查是否有必需的文件存在
     */
    @Test
    @EnabledIf("hasModelFiles")
    public void testLongRunSimulation() {
        try {
            logger.info("=== 长时间运行仿真测试 ===");

            // 检查Excel文件
            File excelFile = new File("dataset/selected_cameras_20250531.xlsx");
            if (excelFile.exists()) {
                logger.info("Excel文件存在: {}", excelFile.getAbsolutePath());
                logger.info("文件大小: {} 字节", excelFile.length());
            } else {
                logger.error("错误: Excel文件不存在");
                return;
            }

            // 检查模型JAR文件
            File modelJar = new File("model.jar");
            if (modelJar.exists()) {
                logger.info("模型JAR文件存在: {}", modelJar.getAbsolutePath());
                logger.info("文件大小: {} 字节", modelJar.length());
            } else {
                logger.error("错误: 模型JAR文件不存在");
                return;
            }

            // 创建模型和实验
            logger.info("正在创建实验对象...");
            Object experiment = createExperiment();

            if (experiment != null) {
                logger.info("实验对象创建成功");

                // 列出所有可用的方法，特别关注时间设置
                logger.info("\n查找时间相关的方法...");
                listSimulationMethods(experiment);

                logger.info("\n开始运行仿真...");
                long startTime = System.currentTimeMillis();

                // 让仿真在后台运行，我们监控它的状态
                Thread simulationThread = new Thread(() -> {
                    try {
                        runExperiment(experiment);
                    } catch (Exception e) {
                        logger.error("仿真运行异常: {}", e.getMessage(), e);
                    }
                }, "SimulationThread");

                simulationThread.setDaemon(true);
                simulationThread.start();

                // 监控仿真状态，每5秒输出一次信息
                for (int i = 0; i < 60; i++) { // 监控1分钟
                    Thread.sleep(2000); // 等待5秒
                    long currentTime = System.currentTimeMillis();
                    long elapsed = currentTime - startTime;

                    logger.info("仿真运行时间: {} 秒", elapsed / 1000);

                    // 尝试获取当前仿真时间
                    // double simTime = getCurrentSimulationTime(experiment);
                    // if (simTime >= 0) {
                    //     logger.info("当前仿真时间: {}", simTime);
                    // }

                    // 检查是否有更多输出（Excel处理信息）
                    logger.info("监控中... ({}/12)", i + 1);

                    // 检查线程是否还活着
                    if (!simulationThread.isAlive()) {
                        logger.info("仿真线程已结束");
                        break;
                    }
                }

                logger.info("监控结束");
            }

        } catch (Exception e) {
            logger.error("程序异常:", e);
        }
    }

    /**
     * 检查是否有必需的模型文件
     */
    public static boolean hasModelFiles() {
        File modelJar = new File("model.jar");
        File datasetFile = new File("dataset/selected_cameras_20250531.xlsx");
        return modelJar.exists() && datasetFile.exists();
    }

    /**
     * 创建仿真实验对象
     */
    private Object createExperiment() {
        try {
            // 尝试多个可能的类名
            String[] possibleClassNames = {
                "nanjingdong.Simulation",
                "nanjingdong.Main",
                "Simulation",
                "Main"
            };

            for (String className : possibleClassNames) {
                try {
                    Class<?> simulationClass = Class.forName(className);
                    logger.info("找到仿真类: {}", className);
                    return simulationClass.getDeclaredConstructor().newInstance();
                } catch (ClassNotFoundException e) {
                    logger.debug("类 {} 不存在，尝试下一个", className);
                }
            }

            logger.error("找不到仿真类，尝试了: {}", String.join(", ", possibleClassNames));
            return null;

        } catch (Exception e) {
            logger.error("创建实验对象失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 列出仿真相关的方法
     */
    private void listSimulationMethods(Object experiment) {
        java.lang.reflect.Method[] methods = experiment.getClass().getMethods();

        for (java.lang.reflect.Method method : methods) {
            String methodName = method.getName();
            // 查找常见的AnyLogic仿真控制方法
            if (methodName.equals("setStopTime") || methodName.equals("getStopTime") ||
                methodName.equals("setTimeUnit") || methodName.equals("getTimeUnit") ||
                methodName.equals("run") || methodName.equals("runFast") ||
                methodName.equals("pause") || methodName.equals("stop") ||
                methodName.equals("setAnimationMode") || methodName.equals("setRunMode")) {
                logger.info("找到方法: {}", methodName);
                logger.info("  参数: {}", java.util.Arrays.toString(method.getParameterTypes()));
            }
        }
    }

    /**
     * 运行仿真实验
     */
    private void runExperiment(Object experiment) throws Exception {
        java.lang.reflect.Method runMethod = experiment.getClass().getMethod("run");
        runMethod.invoke(experiment);
    }

    /**
     * 获取当前仿真时间
     */
    private double getCurrentSimulationTime(Object experiment) {
        try {
            java.lang.reflect.Method getTimeMethod = experiment.getClass().getMethod("getTime");
            return (Double) getTimeMethod.invoke(experiment);
        } catch (Exception e) {
            return -1; // 无法获取时间
        }
    }
}

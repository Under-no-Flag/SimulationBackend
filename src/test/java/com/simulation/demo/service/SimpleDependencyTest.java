package com.simulation.demo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 简单的依赖测试 - 验证 JAR 文件和类加载
 */
public class SimpleDependencyTest {

    private static final Logger logger = LoggerFactory.getLogger(SimpleDependencyTest.class);

    @Test
    @EnabledIf("hasModelFiles")
    public void testJarFilesExist() {
        logger.info("=== JAR 文件存在性测试 ===");

        String[] jarFiles = {
            "model.jar",
            "lib/com.anylogic.engine.jar",
            "lib/com.anylogic.engine.sa.jar", 
            "lib/Core.jar"
        };

        boolean allExists = true;
        for (String jarPath : jarFiles) {
            File jarFile = new File(jarPath);
            if (jarFile.exists()) {
                logger.info("✓ {} 存在 (大小: {} 字节)", jarPath, jarFile.length());
            } else {
                logger.error("✗ {} 不存在", jarPath);
                allExists = false;
            }
        }

        if (allExists) {
            logger.info("所有 JAR 文件都存在");
        } else {
            logger.error("有 JAR 文件缺失");
        }
    }

    @Test
    @EnabledIf("hasModelFiles")
    public void testClassLoaderWithJars() {
        logger.info("=== 类加载器测试 ===");

        try {
            // 创建包含所有 JAR 的类加载器
            File[] jarFiles = {
                new File("model.jar"),
                new File("lib/com.anylogic.engine.jar"),
                new File("lib/com.anylogic.engine.sa.jar"),
                new File("lib/Core.jar")
            };

            URL[] urls = new URL[jarFiles.length];
            for (int i = 0; i < jarFiles.length; i++) {
                urls[i] = jarFiles[i].toURI().toURL();
                logger.info("添加到类路径: {}", urls[i]);
            }

            URLClassLoader classLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());

            // 尝试加载一些基本的 AnyLogic 类
            String[] testClasses = {
                "com.anylogic.engine.UtilitiesMath",
                "com.anylogic.engine.Engine",
                "nanjingdong.Simulation",
                "nanjingdong.Main"
            };

            for (String className : testClasses) {
                try {
                    Class<?> clazz = Class.forName(className, false, classLoader);
                    logger.info("✓ 成功加载类: {}", className);
                } catch (ClassNotFoundException e) {
                    logger.warn("✗ 无法加载类: {} - {}", className, e.getMessage());
                }
            }

            classLoader.close();

        } catch (Exception e) {
            logger.error("类加载器测试失败: {}", e.getMessage(), e);
        }
    }

    @Test
    public void testSystemProperties() {
        logger.info("=== 系统属性测试 ===");
        logger.info("Java 版本: {}", System.getProperty("java.version"));
        logger.info("操作系统: {}", System.getProperty("os.name"));
        logger.info("工作目录: {}", System.getProperty("user.dir"));
        logger.info("类路径: {}", System.getProperty("java.class.path"));
    }

    /**
     * 检查是否有必需的模型文件
     */
    public static boolean hasModelFiles() {
        File modelJar = new File("model.jar");
        File engineJar = new File("lib/com.anylogic.engine.jar");
        return modelJar.exists() && engineJar.exists();
    }
}

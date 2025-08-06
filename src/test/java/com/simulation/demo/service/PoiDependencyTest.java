package com.simulation.demo.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoiDependencyTest {
    
    private static final Logger logger = LoggerFactory.getLogger(PoiDependencyTest.class);
    
    @Test
    public void testPoiClassAvailability() {
        logger.info("=== POI 依赖测试 ===");
        
        try {
            // 尝试加载POI核心类
            Class<?> poiIOUtils = Class.forName("org.apache.poi.util.IOUtils");
            logger.info("✓ 成功加载 POI IOUtils 类");
            
            Class<?> poiWorkbook = Class.forName("org.apache.poi.ss.usermodel.Workbook");
            logger.info("✓ 成功加载 POI Workbook 类");
            
            // 检查类路径
            String classpath = System.getProperty("java.class.path");
            logger.info("当前类路径包含POI:");
            
            String[] paths = classpath.split(";");
            for (String path : paths) {
                if (path.contains("poi")) {
                    logger.info("  - " + path);
                }
            }
            
        } catch (ClassNotFoundException e) {
            logger.error("❌ 无法加载 POI 类: " + e.getMessage());
            logger.info("类路径: " + System.getProperty("java.class.path"));
        }
    }
}

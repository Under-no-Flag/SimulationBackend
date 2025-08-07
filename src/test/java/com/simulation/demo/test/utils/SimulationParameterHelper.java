package com.simulation.demo.test.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 仿真参数测试助手类
 * 用于生成和验证仿真参数的工具方法
 */
public class SimulationParameterHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(SimulationParameterHelper.class);
    
    /**
     * 创建基础测试参数
     */
    public static Map<String, Object> createBasicTestParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("simulTargetTime", "2025-05-31 12:30:00");
        parameters.put("pedestrianCount", 1000);
        parameters.put("simulationSpeed", 1.0);
        parameters.put("weatherCondition", "sunny");
        parameters.put("enableLogging", true);
        
        return parameters;
    }
    
    /**
     * 创建完整测试参数
     */
    public static Map<String, Object> createFullTestParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("simulTargetTime", "2025-05-31 12:30:00");
        parameters.put("pedestrianCount", 1500);
        parameters.put("simulationSpeed", 2.0);
        parameters.put("weatherCondition", "rainy");
        parameters.put("enableLogging", true);
        parameters.put("maxWalkingSpeed", 1.5);
        parameters.put("minWalkingSpeed", 0.8);
        parameters.put("emergencyExitEnabled", false);
        parameters.put("crowdDensityThreshold", 0.8);
        
        return parameters;
    }
    
    /**
     * 将参数Map转换为JSON字符串
     */
    public static String parametersToJson(Map<String, Object> parameters) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(parameters);
        } catch (Exception e) {
            logger.error("参数转换为JSON失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从JSON字符串解析参数Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> jsonToParameters(String json) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            logger.error("JSON解析为参数失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 验证参数格式
     */
    public static boolean validateParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            logger.warn("参数为空");
            return false;
        }
        
        // 检查必要参数
        String[] requiredParams = {"simulTargetTime", "pedestrianCount"};
        for (String param : requiredParams) {
            if (!parameters.containsKey(param)) {
                logger.warn("缺少必要参数: {}", param);
                return false;
            }
        }
        
        // 验证参数类型和范围
        try {
            Object pedestrianCount = parameters.get("pedestrianCount");
            if (pedestrianCount instanceof Number) {
                int count = ((Number) pedestrianCount).intValue();
                if (count <= 0 || count > 10000) {
                    logger.warn("行人数量超出合理范围: {}", count);
                    return false;
                }
            }
            
            Object simulationSpeed = parameters.get("simulationSpeed");
            if (simulationSpeed instanceof Number) {
                double speed = ((Number) simulationSpeed).doubleValue();
                if (speed <= 0 || speed > 10) {
                    logger.warn("仿真速度超出合理范围: {}", speed);
                    return false;
                }
            }
            
        } catch (Exception e) {
            logger.error("参数验证失败: {}", e.getMessage());
            return false;
        }
        
        return true;
    }
    
    /**
     * 打印参数信息（用于调试）
     */
    public static void printParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            logger.info("参数为空");
            return;
        }
        
        logger.info("=== 仿真参数信息 ===");
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            logger.info("{}: {} ({})", 
                entry.getKey(), 
                entry.getValue(), 
                entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null");
        }
        logger.info("==================");
    }
}

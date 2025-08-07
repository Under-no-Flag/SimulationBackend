package com.simulation.demo.test;

import com.simulation.demo.test.utils.SimulationParameterHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 仿真参数功能测试类
 */
public class SimulationParameterTest {
    
    private static final Logger logger = LoggerFactory.getLogger(SimulationParameterTest.class);
    
    @Test
    public void testBasicParameterCreation() {
        logger.info("测试基础参数创建...");
        
        Map<String, Object> parameters = SimulationParameterHelper.createBasicTestParameters();
        assertNotNull(parameters);
        assertFalse(parameters.isEmpty());
        
        // 验证基础参数
        assertEquals("2025-05-31 12:30:00", parameters.get("simulTargetTime"));
        assertEquals(1000, parameters.get("pedestrianCount"));
        assertEquals(1.0, parameters.get("simulationSpeed"));
        assertEquals("sunny", parameters.get("weatherCondition"));
        assertEquals(true, parameters.get("enableLogging"));
        
        logger.info("✓ 基础参数创建测试通过");
    }
    
    @Test
    public void testParameterJsonConversion() {
        logger.info("测试参数JSON转换...");
        
        Map<String, Object> originalParameters = SimulationParameterHelper.createBasicTestParameters();
        
        // 转换为JSON
        String json = SimulationParameterHelper.parametersToJson(originalParameters);
        assertNotNull(json);
        assertFalse(json.trim().isEmpty());
        logger.info("参数JSON: {}", json);
        
        // 从JSON转换回Map
        Map<String, Object> convertedParameters = SimulationParameterHelper.jsonToParameters(json);
        assertNotNull(convertedParameters);
        assertEquals(originalParameters.size(), convertedParameters.size());
        
        // 验证关键参数
        assertEquals(originalParameters.get("simulTargetTime"), convertedParameters.get("simulTargetTime"));
        assertEquals(originalParameters.get("pedestrianCount"), convertedParameters.get("pedestrianCount"));
        
        logger.info("✓ 参数JSON转换测试通过");
    }
    
    @Test
    public void testParameterValidation() {
        logger.info("测试参数验证...");
        
        // 测试有效参数
        Map<String, Object> validParameters = SimulationParameterHelper.createBasicTestParameters();
        assertTrue(SimulationParameterHelper.validateParameters(validParameters));
        
        // 测试空参数
        assertFalse(SimulationParameterHelper.validateParameters(null));
        assertFalse(SimulationParameterHelper.validateParameters(Map.of()));
        
        // 测试缺少必要参数
        Map<String, Object> incompleteParameters = Map.of("simulationSpeed", 1.0);
        assertFalse(SimulationParameterHelper.validateParameters(incompleteParameters));
        
        // 测试无效数值范围
        Map<String, Object> invalidParameters = SimulationParameterHelper.createBasicTestParameters();
        invalidParameters.put("pedestrianCount", -100); // 无效的行人数量
        assertFalse(SimulationParameterHelper.validateParameters(invalidParameters));
        
        logger.info("✓ 参数验证测试通过");
    }
    
    @Test
    public void testFullParameterSet() {
        logger.info("测试完整参数集...");
        
        Map<String, Object> fullParameters = SimulationParameterHelper.createFullTestParameters();
        assertNotNull(fullParameters);
        assertTrue(fullParameters.size() >= 5);
        
        // 验证完整参数集的有效性
        assertTrue(SimulationParameterHelper.validateParameters(fullParameters));
        
        // 打印参数信息
        SimulationParameterHelper.printParameters(fullParameters);
        
        logger.info("✓ 完整参数集测试通过");
    }
    
    @Test
    public void testParameterPrintFunction() {
        logger.info("测试参数打印功能...");
        
        Map<String, Object> parameters = SimulationParameterHelper.createBasicTestParameters();
        
        // 这个测试主要是确保打印功能不会抛出异常
        assertDoesNotThrow(() -> {
            SimulationParameterHelper.printParameters(parameters);
        });
        
        // 测试空参数打印
        assertDoesNotThrow(() -> {
            SimulationParameterHelper.printParameters(null);
        });
        
        logger.info("✓ 参数打印功能测试通过");
    }
}

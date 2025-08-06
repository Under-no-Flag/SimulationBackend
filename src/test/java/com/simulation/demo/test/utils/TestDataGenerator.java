package com.simulation.demo.test.utils;

import com.simulation.demo.entity.EventsLog;
import com.simulation.demo.entity.PedestrianData;
import com.simulation.demo.entity.SimulationRun;
import com.simulation.demo.entity.SimulationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 测试数据生成器
 */
public class TestDataGenerator {
    
    private static final Random random = new Random();
    
    /**
     * 创建测试的模拟运行
     */
    public static SimulationRun createTestSimulationRun(String modelName) {
        SimulationRun run = new SimulationRun(modelName, LocalDateTime.now());
        run.setStatus(SimulationStatus.COMPLETED);
        run.setDescription("测试数据 - " + modelName);
        run.setSimulationParameters("{\"param1\":\"value1\",\"param2\":\"value2\"}");
        run.setEndDate(LocalDateTime.now().plusMinutes(30));
        return run;
    }
    
    /**
     * 创建测试的行人数据
     */
    public static PedestrianData createTestPedestrianData(Integer runId, Integer pedestrianId) {
        PedestrianData data = new PedestrianData(runId, pedestrianId);
        data.setSimTime(new BigDecimal(String.valueOf(random.nextDouble() * 100)));
        data.setPosX(new BigDecimal(String.valueOf(random.nextDouble() * 100)));
        data.setPosY(new BigDecimal(String.valueOf(random.nextDouble() * 100)));
        data.setPosZ(new BigDecimal("0.0"));
        data.setSpeed(new BigDecimal(String.valueOf(random.nextDouble() * 5)));
        data.setAreaName("Area" + (random.nextInt(5) + 1));
        return data;
    }
    
    /**
     * 批量创建行人数据
     */
    public static List<PedestrianData> createPedestrianDataBatch(Integer runId, int count) {
        List<PedestrianData> dataList = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            dataList.add(createTestPedestrianData(runId, 100 + i));
        }
        return dataList;
    }
    
    /**
     * 创建测试的事件日志
     */
    public static EventsLog createTestEventsLog(Integer runId, Integer pedestrianId, String eventType) {
        EventsLog log = new EventsLog(runId, eventType);
        log.setPedestrianId(pedestrianId);
        log.setSimTime(new BigDecimal(String.valueOf(random.nextDouble() * 100)));
        log.setEventDetails("测试事件详情 - " + eventType);
        return log;
    }
    
    /**
     * 批量创建事件日志
     */
    public static List<EventsLog> createEventsLogBatch(Integer runId, int count) {
        List<EventsLog> logList = new ArrayList<>();
        String[] eventTypes = {"ENTER", "EXIT", "COLLISION", "STOP", "MOVE"};
        
        for (int i = 1; i <= count; i++) {
            String eventType = eventTypes[random.nextInt(eventTypes.length)];
            logList.add(createTestEventsLog(runId, 100 + i, eventType));
        }
        return logList;
    }
}

package com.simulation.demo.service;

import com.simulation.demo.entity.EventsLog;
import com.simulation.demo.entity.PedestrianData;
import com.simulation.demo.entity.SimulationRun;
import com.simulation.demo.entity.SimulationStatus;
import com.simulation.demo.repository.EventsLogRepository;
import com.simulation.demo.repository.PedestrianDataRepository;
import com.simulation.demo.repository.SimulationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SimulationDataServiceIntegrationTest {
    
    @Autowired
    private SimulationDataService simulationDataService;
    
    @Autowired
    private SimulationRunRepository simulationRunRepository;
    
    @Autowired
    private PedestrianDataRepository pedestrianDataRepository;
    
    @Autowired
    private EventsLogRepository eventsLogRepository;
    
    private SimulationRun testSimulationRun;
    private PedestrianData testPedestrianData1;
    private PedestrianData testPedestrianData2;
    private EventsLog testEventsLog;
    
    @BeforeEach
    void setUp() {
        // 清理数据 - 使用try-catch防止表不存在时的错误
        try {
            eventsLogRepository.deleteAll();
        } catch (Exception e) {
            // 忽略表不存在的错误
        }
        try {
            pedestrianDataRepository.deleteAll();
        } catch (Exception e) {
            // 忽略表不存在的错误
        }
        try {
            simulationRunRepository.deleteAll();
        } catch (Exception e) {
            // 忽略表不存在的错误
        }
        
        // 创建测试数据
        testSimulationRun = new SimulationRun("IntegrationTestModel", LocalDateTime.now());
        testSimulationRun.setStatus(SimulationStatus.COMPLETED);
        testSimulationRun.setDescription("集成测试模拟运行");
        testSimulationRun = simulationRunRepository.save(testSimulationRun);
        
        // 创建行人数据
        testPedestrianData1 = new PedestrianData(testSimulationRun.getRunId(), 101);
        testPedestrianData1.setSimTime(new BigDecimal("1.0"));
        testPedestrianData1.setPosX(new BigDecimal("10.5"));
        testPedestrianData1.setPosY(new BigDecimal("20.3"));
        testPedestrianData1.setSpeed(new BigDecimal("1.2"));
        testPedestrianData1.setAreaName("Area1");
        
        testPedestrianData2 = new PedestrianData(testSimulationRun.getRunId(), 102);
        testPedestrianData2.setSimTime(new BigDecimal("2.0"));
        testPedestrianData2.setPosX(new BigDecimal("15.8"));
        testPedestrianData2.setPosY(new BigDecimal("25.7"));
        testPedestrianData2.setSpeed(new BigDecimal("1.5"));
        testPedestrianData2.setAreaName("Area2");
        
        pedestrianDataRepository.saveAll(Arrays.asList(testPedestrianData1, testPedestrianData2));
        
        // 创建事件日志
        testEventsLog = new EventsLog(testSimulationRun.getRunId(), "ENTER");
        testEventsLog.setSimTime(new BigDecimal("1.5"));
        testEventsLog.setPedestrianId(101);
        testEventsLog.setEventDetails("行人进入区域");
        eventsLogRepository.save(testEventsLog);
    }
    
    @Test
    void testGetAllSimulationRuns() {
        List<SimulationRun> runs = simulationDataService.getAllSimulationRuns();
        
        assertNotNull(runs);
        assertEquals(1, runs.size());
        assertEquals("IntegrationTestModel", runs.get(0).getModelName());
        assertEquals(SimulationStatus.COMPLETED, runs.get(0).getStatus());
    }
    
    @Test
    void testGetSimulationRunById() {
        Optional<SimulationRun> run = simulationDataService.getSimulationRunById(testSimulationRun.getRunId());
        
        assertTrue(run.isPresent());
        assertEquals("IntegrationTestModel", run.get().getModelName());
        assertEquals(testSimulationRun.getRunId(), run.get().getRunId());
    }
    
    @Test
    void testGetPedestrianDataByRunId() {
        Page<PedestrianData> page = simulationDataService.getPedestrianDataByRunId(
            testSimulationRun.getRunId(), 0, 10);
        
        assertNotNull(page);
        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        
        // 验证数据按时间排序
        assertEquals(new BigDecimal("1.0"), page.getContent().get(0).getSimTime());
        assertEquals(new BigDecimal("2.0"), page.getContent().get(1).getSimTime());
    }
    
    @Test
    void testGetPedestrianDataByRunIdAndPedestrianId() {
        List<PedestrianData> data = simulationDataService.getPedestrianDataByRunIdAndPedestrianId(
            testSimulationRun.getRunId(), 101);
        
        assertNotNull(data);
        assertEquals(1, data.size());
        assertEquals(Integer.valueOf(101), data.get(0).getPedestrianId());
        assertEquals(new BigDecimal("10.5"), data.get(0).getPosX());
    }
    
    @Test
    void testGetPedestrianDataByTimeRange() {
        List<PedestrianData> data = simulationDataService.getPedestrianDataByTimeRange(
            testSimulationRun.getRunId(), 
            new BigDecimal("0.5"), 
            new BigDecimal("1.5"));
        
        assertNotNull(data);
        assertEquals(1, data.size());
        assertEquals(new BigDecimal("1.0"), data.get(0).getSimTime());
    }
    
    @Test
    void testGetPedestrianDataByArea() {
        List<PedestrianData> data = simulationDataService.getPedestrianDataByArea(
            testSimulationRun.getRunId(), "Area1");
        
        assertNotNull(data);
        assertEquals(1, data.size());
        assertEquals("Area1", data.get(0).getAreaName());
        assertEquals(Integer.valueOf(101), data.get(0).getPedestrianId());
    }
    
    @Test
    void testCountPedestriansByRunId() {
        Long count = simulationDataService.countPedestriansByRunId(testSimulationRun.getRunId());
        
        assertNotNull(count);
        assertEquals(2L, count);
    }
    
    @Test
    void testGetEventsLogByRunId() {
        List<EventsLog> events = simulationDataService.getEventsLogByRunId(testSimulationRun.getRunId());
        
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("ENTER", events.get(0).getEventType());
        assertEquals(Integer.valueOf(101), events.get(0).getPedestrianId());
    }
    
    @Test
    void testGetEventsLogByEventType() {
        List<EventsLog> events = simulationDataService.getEventsLogByEventType(
            testSimulationRun.getRunId(), "ENTER");
        
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("ENTER", events.get(0).getEventType());
    }
    
    @Test
    void testSavePedestrianDataBatch() {
        // 创建新的测试数据
        PedestrianData newData1 = new PedestrianData(testSimulationRun.getRunId(), 103);
        newData1.setSimTime(new BigDecimal("3.0"));
        newData1.setPosX(new BigDecimal("30.0"));
        newData1.setPosY(new BigDecimal("40.0"));
        
        PedestrianData newData2 = new PedestrianData(testSimulationRun.getRunId(), 104);
        newData2.setSimTime(new BigDecimal("4.0"));
        newData2.setPosX(new BigDecimal("50.0"));
        newData2.setPosY(new BigDecimal("60.0"));
        
        List<PedestrianData> newDataList = Arrays.asList(newData1, newData2);
        
        // 批量保存
        List<PedestrianData> savedData = simulationDataService.savePedestrianDataBatch(newDataList);
        
        assertNotNull(savedData);
        assertEquals(2, savedData.size());
        
        // 验证数据是否真的保存到数据库
        Long totalCount = simulationDataService.countPedestriansByRunId(testSimulationRun.getRunId());
        assertEquals(4L, totalCount); // 原来2个 + 新增2个
    }
    
    @Test
    void testGetSimulationRunsByDateRange() {
        LocalDateTime startDate = LocalDateTime.now().minusHours(1);
        LocalDateTime endDate = LocalDateTime.now().plusHours(1);
        
        List<SimulationRun> runs = simulationDataService.getSimulationRunsByDateRange(startDate, endDate);
        
        assertNotNull(runs);
        assertEquals(1, runs.size());
        assertEquals("IntegrationTestModel", runs.get(0).getModelName());
    }
}

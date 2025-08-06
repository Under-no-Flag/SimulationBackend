package com.simulation.demo.service;

import com.simulation.demo.entity.PedestrianData;
import com.simulation.demo.entity.SimulationRun;
import com.simulation.demo.repository.SimulationRunRepository;
import com.simulation.demo.test.utils.TestDataGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SimulationDataServicePerformanceTest {
    
    @Autowired
    private SimulationDataService simulationDataService;
    
    @Autowired
    private SimulationRunRepository simulationRunRepository;
    
    private SimulationRun testRun;
    
    @BeforeEach
    void setUp() {
        // 创建测试运行
        testRun = TestDataGenerator.createTestSimulationRun("PerformanceTestModel");
        testRun = simulationRunRepository.save(testRun);
    }
    
    @Test
    void testBatchInsertPerformance() {
        // 创建大量测试数据
        List<PedestrianData> largeDataSet = TestDataGenerator.createPedestrianDataBatch(
            testRun.getRunId(), 1000);
        
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        
        // 批量插入
        List<PedestrianData> savedData = simulationDataService.savePedestrianDataBatch(largeDataSet);
        
        stopWatch.stop();
        
        System.out.println("批量插入1000条记录耗时: " + stopWatch.getTotalTimeMillis() + "ms");
        
        assertEquals(1000, savedData.size());
        assertTrue(stopWatch.getTotalTimeMillis() < 5000, "批量插入耗时应小于5秒");
    }
    
    @Test
    void testPaginationPerformance() {
        // 先插入大量数据
        List<PedestrianData> largeDataSet = TestDataGenerator.createPedestrianDataBatch(
            testRun.getRunId(), 500);
        simulationDataService.savePedestrianDataBatch(largeDataSet);
        
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        
        // 测试分页查询性能
        Page<PedestrianData> page = simulationDataService.getPedestrianDataByRunId(
            testRun.getRunId(), 0, 50);
        
        stopWatch.stop();
        
        System.out.println("分页查询50条记录耗时: " + stopWatch.getTotalTimeMillis() + "ms");
        
        assertNotNull(page);
        assertEquals(50, page.getContent().size());
        assertEquals(500, page.getTotalElements());
        assertTrue(stopWatch.getTotalTimeMillis() < 1000, "分页查询耗时应小于1秒");
    }
    
    @Test
    void testCountPerformance() {
        // 插入测试数据
        List<PedestrianData> dataSet = TestDataGenerator.createPedestrianDataBatch(
            testRun.getRunId(), 200);
        simulationDataService.savePedestrianDataBatch(dataSet);
        
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        
        // 测试统计性能
        Long count = simulationDataService.countPedestriansByRunId(testRun.getRunId());
        
        stopWatch.stop();
        
        System.out.println("统计行人数量耗时: " + stopWatch.getTotalTimeMillis() + "ms");
        
        assertEquals(200L, count);
        assertTrue(stopWatch.getTotalTimeMillis() < 500, "统计查询耗时应小于500ms");
    }
}

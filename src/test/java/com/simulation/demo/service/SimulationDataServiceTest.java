package com.simulation.demo.service;

import com.simulation.demo.entity.PedestrianData;
import com.simulation.demo.entity.SimulationRun;
import com.simulation.demo.entity.SimulationStatus;
import com.simulation.demo.repository.EventsLogRepository;
import com.simulation.demo.repository.PedestrianDataRepository;
import com.simulation.demo.repository.SimulationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationDataServiceTest {

    @Mock
    private SimulationRunRepository simulationRunRepository;

    @Mock
    private PedestrianDataRepository pedestrianDataRepository;

    @Mock
    private EventsLogRepository eventsLogRepository;

    @InjectMocks
    private SimulationDataService simulationDataService;

    private SimulationRun testSimulationRun;
    private PedestrianData testPedestrianData;

    @BeforeEach
    void setUp() {
        testSimulationRun = new SimulationRun("TestModel", LocalDateTime.now());
        testSimulationRun.setRunId(1);
        testSimulationRun.setStatus(SimulationStatus.COMPLETED);

        testPedestrianData = new PedestrianData(1, 1);
        testPedestrianData.setId(1L);
        testPedestrianData.setPosX(new BigDecimal("10.5"));
        testPedestrianData.setPosY(new BigDecimal("20.3"));
        testPedestrianData.setSpeed(new BigDecimal("1.2"));
    }

    @Test
    void testGetAllSimulationRuns() {
        // 准备测试数据
        List<SimulationRun> expectedRuns = Arrays.asList(testSimulationRun);

        // Mock行为
        when(simulationRunRepository.findAll(any(org.springframework.data.domain.Sort.class)))
            .thenReturn(expectedRuns);

        // 执行测试
        List<SimulationRun> result = simulationDataService.getAllSimulationRuns();

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testSimulationRun.getRunId(), result.get(0).getRunId());

        // 验证方法调用
        verify(simulationRunRepository, times(1)).findAll(any(org.springframework.data.domain.Sort.class));
    }

    @Test
    void testGetSimulationRunById() {
        // Mock行为
        when(simulationRunRepository.findById(1)).thenReturn(Optional.of(testSimulationRun));

        // 执行测试
        Optional<SimulationRun> result = simulationDataService.getSimulationRunById(1);

        // 验证结果
        assertTrue(result.isPresent());
        assertEquals(testSimulationRun.getRunId(), result.get().getRunId());

        // 验证方法调用
        verify(simulationRunRepository, times(1)).findById(1);
    }

    @Test
    void testGetPedestrianDataByRunId() {
        // 准备测试数据
        List<PedestrianData> pedestrianDataList = Arrays.asList(testPedestrianData);
        Page<PedestrianData> expectedPage = new PageImpl<>(pedestrianDataList);
        Pageable pageable = PageRequest.of(0, 10);

        // Mock行为
        when(pedestrianDataRepository.findByRunId(eq(1), any(Pageable.class)))
            .thenReturn(expectedPage);

        // 执行测试
        Page<PedestrianData> result = simulationDataService.getPedestrianDataByRunId(1, 0, 10);
        //打印PedestrianData信息
        result.getContent().forEach(data -> System.out.println("PedestrianData: " + data));
        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(testPedestrianData.getId(), result.getContent().get(0).getId());

        // 验证方法调用
        verify(pedestrianDataRepository, times(1)).findByRunId(eq(1), any(Pageable.class));
    }

    @Test
    void testGetPedestrianDataByRunIdAndPedestrianId() {
        // 准备测试数据
        List<PedestrianData> expectedData = Arrays.asList(testPedestrianData);

        // Mock行为
        when(pedestrianDataRepository.findByRunIdAndPedestrianId(1, 1))
            .thenReturn(expectedData);

        // 执行测试
        List<PedestrianData> result = simulationDataService.getPedestrianDataByRunIdAndPedestrianId(1, 1);
        // 打印PedestrianData信息
        // result.forEach(data -> System.out.println("PedestrianData: " + data.getPosX() + ", " + data.getPosY()));
        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testPedestrianData.getPedestrianId(), result.get(0).getPedestrianId());

        // 验证方法调用
        verify(pedestrianDataRepository, times(1)).findByRunIdAndPedestrianId(1, 1);
    }

    @Test
    void testCountPedestriansByRunId() {
        // Mock行为
        when(pedestrianDataRepository.countDistinctPedestriansByRunId(1)).thenReturn(50L);

        // 执行测试
        Long result = simulationDataService.countPedestriansByRunId(1);

        // 验证结果
        assertNotNull(result);
        assertEquals(50L, result);

        // 验证方法调用
        verify(pedestrianDataRepository, times(1)).countDistinctPedestriansByRunId(1);
    }

    @Test
    void testSavePedestrianData() {
        // Mock行为
        when(pedestrianDataRepository.save(testPedestrianData)).thenReturn(testPedestrianData);

        // 执行测试
        PedestrianData result = simulationDataService.savePedestrianData(testPedestrianData);

        // 验证结果
        assertNotNull(result);
        assertEquals(testPedestrianData.getId(), result.getId());

        // 验证方法调用
        verify(pedestrianDataRepository, times(1)).save(testPedestrianData);
    }
}

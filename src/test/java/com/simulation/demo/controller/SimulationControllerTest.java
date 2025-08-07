package com.simulation.demo.controller;

import com.simulation.demo.entity.SimulationRun;
import com.simulation.demo.entity.SimulationStatus;
import com.simulation.demo.service.AnyLogicModelService;
import com.simulation.demo.service.SimulationDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SimulationController.class)
class SimulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnyLogicModelService anyLogicModelService;

    @MockBean
    private SimulationDataService simulationDataService;

    @Autowired
    private ObjectMapper objectMapper;

    private SimulationRun testSimulationRun;

    @BeforeEach
    void setUp() {
        testSimulationRun = new SimulationRun("TestModel", LocalDateTime.now());
        testSimulationRun.setRunId(1);
        testSimulationRun.setStatus(SimulationStatus.PENDING);
        testSimulationRun.setDescription("Test simulation");
    }

    // @Test
    // void testStartSimulation() throws Exception {
    //     // 准备请求数据
    //     SimulationController.SimulationStartRequest request = new SimulationController.SimulationStartRequest();
    //     request.setModelName("TestModel");
    //     request.setParameters(new HashMap<>());
    //     request.setDescription("Test description");

    //     // Mock行为
    //     when(anyLogicModelService.isModelFileExists()).thenReturn(true);
    //     when(anyLogicModelService.createAndStartSimulation(anyString(), anyString(), anyString()))
    //         .thenReturn(testSimulationRun);

    //     // 执行测试
    //     mockMvc.perform(post("/api/simulation/start")
    //             .contentType(MediaType.APPLICATION_JSON)
    //             .content(objectMapper.writeValueAsString(request)))
    //             .andExpect(status().isOk())
    //             .andExpect(jsonPath("$.success").value(true))
    //             .andExpect(jsonPath("$.message").value("模拟启动成功"))
    //             .andExpect(jsonPath("$.data.runId").value(1))
    //             .andExpect(jsonPath("$.data.modelName").value("TestModel"));

    //     // 验证方法调用
    //     verify(anyLogicModelService, times(1)).isModelFileExists();
    //     verify(anyLogicModelService, times(1)).createAndStartSimulation(anyString(), anyString(), anyString());
    // }

    // @Test
    // void testStartSimulationModelNotExists() throws Exception {
    //     // 准备请求数据
    //     SimulationController.SimulationStartRequest request = new SimulationController.SimulationStartRequest();
    //     request.setModelName("TestModel");

    //     // Mock行为
    //     when(anyLogicModelService.isModelFileExists()).thenReturn(false);

    //     // 执行测试
    //     mockMvc.perform(post("/api/simulation/start")
    //             .contentType(MediaType.APPLICATION_JSON)
    //             .content(objectMapper.writeValueAsString(request)))
    //             .andExpect(status().isBadRequest())
    //             .andExpect(jsonPath("$.success").value(false))
    //             .andExpect(jsonPath("$.message").value("模型文件不存在"));

    //     // 验证方法调用
    //     verify(anyLogicModelService, times(1)).isModelFileExists();
    //     verify(anyLogicModelService, never()).createAndStartSimulation(anyString(), anyString(), anyString());
    // }

    @Test
    void testGetAllSimulationRuns() throws Exception {
        // Mock行为
        when(simulationDataService.getAllSimulationRuns())
            .thenReturn(Arrays.asList(testSimulationRun));

        // 执行测试
        mockMvc.perform(get("/api/simulation/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("获取成功"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].runId").value(1));

        // 验证方法调用
        verify(simulationDataService, times(1)).getAllSimulationRuns();
    }

    @Test
    void testGetSimulationRunById() throws Exception {
        // Mock行为
        when(simulationDataService.getSimulationRunById(1))
            .thenReturn(Optional.of(testSimulationRun));

        // 执行测试
        mockMvc.perform(get("/api/simulation/runs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.runId").value(1))
                .andExpect(jsonPath("$.data.modelName").value("TestModel"));

        // 验证方法调用
        verify(simulationDataService, times(1)).getSimulationRunById(1);
    }

    @Test
    void testGetSimulationRunByIdNotFound() throws Exception {
        // Mock行为
        when(simulationDataService.getSimulationRunById(999))
            .thenReturn(Optional.empty());

        // 执行测试
        mockMvc.perform(get("/api/simulation/runs/999"))
                .andExpect(status().isNotFound());

        // 验证方法调用
        verify(simulationDataService, times(1)).getSimulationRunById(999);
    }
}

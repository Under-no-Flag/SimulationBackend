package com.simulation.demo.controller;

import com.simulation.demo.entity.SimulationRun;
import com.simulation.demo.service.AnyLogicModelService;
import com.simulation.demo.service.SimulationDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/simulation")
@CrossOrigin(origins = "*")
public class SimulationController {

    private static final Logger logger = LoggerFactory.getLogger(SimulationController.class);

    @Autowired
    private AnyLogicModelService anyLogicModelService;

    @Autowired
    private SimulationDataService simulationDataService;

    /**
     * 启动新的模拟运行
     */
    @PostMapping("/start")
    public ResponseEntity<?> startSimulation(@RequestBody SimulationStartRequest request) {
        logger.info("收到启动模拟请求: {}", request.getModelName());

        try {
            // 检查模型文件是否存在
            if (!anyLogicModelService.isModelFileExists()) {
                return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "模型文件不存在", null));
            }

            SimulationRun simulationRun = anyLogicModelService.createAndStartSimulation(
                request.getModelName(),
                request.getParameters(),
                request.getDescription()
            );

            return ResponseEntity.ok(new ApiResponse(true, "模拟启动成功", simulationRun));

        } catch (Exception e) {
            logger.error("启动模拟失败", e);
            return ResponseEntity.internalServerError()
                .body(new ApiResponse(false, "启动模拟失败: " + e.getMessage(), null));
        }
    }

    /**
     * 停止指定的模拟运行
     */
    @PostMapping("/stop/{runId}")
    public ResponseEntity<?> stopSimulation(@PathVariable Integer runId) {
        logger.info("收到停止模拟请求，运行ID: {}", runId);

        try {
            boolean success = anyLogicModelService.stopSimulation(runId);
            if (success) {
                return ResponseEntity.ok(new ApiResponse(true, "模拟停止成功", null));
            } else {
                return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "模拟停止失败：找不到运行中的仿真", null));
            }
        } catch (Exception e) {
            logger.error("停止模拟失败，运行ID: {}", runId, e);
            return ResponseEntity.internalServerError()
                .body(new ApiResponse(false, "停止模拟失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取运行状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<?> getSimulationStatus() {
        logger.info("获取仿真状态信息");

        try {
            int runningCount = anyLogicModelService.getRunningSimulationCount();
            boolean modelExists = anyLogicModelService.isModelFileExists();
            
            return ResponseEntity.ok(new ApiResponse(true, "获取状态成功", 
                new SimulationStatusInfo(runningCount, modelExists)));
        } catch (Exception e) {
            logger.error("获取仿真状态失败", e);
            return ResponseEntity.internalServerError()
                .body(new ApiResponse(false, "获取状态失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取所有模拟运行记录
     */
    @GetMapping("/runs")
    public ResponseEntity<?> getAllSimulationRuns() {
        logger.info("获取所有模拟运行记录");

        try {
            List<SimulationRun> runs = simulationDataService.getAllSimulationRuns();
            logger.info("成功获取到 {} 条模拟运行记录", runs.size());
            return ResponseEntity.ok(new ApiResponse(true, "获取成功", runs));
        } catch (Exception e) {
            logger.error("获取模拟运行记录失败", e);
            return ResponseEntity.internalServerError()
                .body(new ApiResponse(false, "获取数据失败: " + e.getMessage(), null));
        }
    }

    /**
     * 根据ID获取模拟运行记录
     */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<?> getSimulationRunById(@PathVariable Integer runId) {
        logger.info("获取模拟运行记录，ID: {}", runId);

        try {
            Optional<SimulationRun> run = simulationDataService.getSimulationRunById(runId);
            if (run.isPresent()) {
                return ResponseEntity.ok(new ApiResponse(true, "获取成功", run.get()));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("获取模拟运行记录失败，ID: {}", runId, e);
            return ResponseEntity.internalServerError()
                .body(new ApiResponse(false, "获取数据失败: " + e.getMessage(), null));
        }
    }

    /**
     * 根据时间范围获取模拟运行记录
     */
    @GetMapping("/runs/date-range")
    public ResponseEntity<?> getSimulationRunsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        logger.info("获取时间范围内的模拟运行记录: {} - {}", startDate, endDate);

        try {
            List<SimulationRun> runs = simulationDataService.getSimulationRunsByDateRange(startDate, endDate);
            return ResponseEntity.ok(new ApiResponse(true, "获取成功", runs));
        } catch (Exception e) {
            logger.error("获取时间范围内的模拟运行记录失败", e);
            return ResponseEntity.internalServerError()
                .body(new ApiResponse(false, "获取数据失败: " + e.getMessage(), null));
        }
    }

    // 内部类：启动模拟请求
    public static class SimulationStartRequest {
        private String modelName;
        private String parameters;
        private String description;

        // Getters and setters
        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getParameters() {
            return parameters;
        }

        public void setParameters(String parameters) {
            this.parameters = parameters;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    // 内部类：仿真状态信息
    public static class SimulationStatusInfo {
        private int runningSimulationCount;
        private boolean modelFileExists;

        public SimulationStatusInfo(int runningSimulationCount, boolean modelFileExists) {
            this.runningSimulationCount = runningSimulationCount;
            this.modelFileExists = modelFileExists;
        }

        // Getters and setters
        public int getRunningSimulationCount() {
            return runningSimulationCount;
        }

        public void setRunningSimulationCount(int runningSimulationCount) {
            this.runningSimulationCount = runningSimulationCount;
        }

        public boolean isModelFileExists() {
            return modelFileExists;
        }

        public void setModelFileExists(boolean modelFileExists) {
            this.modelFileExists = modelFileExists;
        }
    }

    // 内部类：API响应
    public static class ApiResponse {
        private boolean success;
        private String message;
        private Object data;

        public ApiResponse(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        // Getters and setters
        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }
}

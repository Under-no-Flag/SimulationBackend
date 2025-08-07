package com.simulation.demo.controller;

import com.simulation.demo.entity.EventsLog;
import com.simulation.demo.entity.PedestrianData;
import com.simulation.demo.service.SimulationDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.simulation.demo.service.CoordinateConversionService;
import java.math.BigDecimal;
import java.util.List;
import util.GeoUtil;
@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*")
public class DataController {

    private static final Logger logger = LoggerFactory.getLogger(DataController.class);

    @Autowired
    private SimulationDataService simulationDataService;

    @Autowired
    private CoordinateConversionService coordinateConversionService;
    /**
     * 获取行人数据（分页）
     */
    @GetMapping("/pedestrians/{runId}")
    public ResponseEntity<?> getPedestrianData(
            @PathVariable Integer runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        logger.info("获取行人数据，运行ID: {}, 页码: {}, 大小: {}", runId, page, size);

        try {
            Page<PedestrianData> pedestrianData = simulationDataService.getPedestrianDataByRunId(runId, page, size);
            return ResponseEntity.ok(new SimulationController.ApiResponse(true, "获取成功", pedestrianData));
        } catch (Exception e) {
            logger.error("获取行人数据失败，运行ID: {}", runId, e);
            return ResponseEntity.internalServerError()
                .body(new SimulationController.ApiResponse(false, "获取数据失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取特定行人的数据
     */
    @GetMapping("/pedestrians/{runId}/{pedestrianId}")
    public ResponseEntity<?> getPedestrianDataByPedestrianId(
            @PathVariable Integer runId,
            @PathVariable Integer pedestrianId) {

        logger.info("获取特定行人数据，运行ID: {}, 行人ID: {}", runId, pedestrianId);

        try {
            List<PedestrianData> pedestrianData = simulationDataService.getPedestrianDataByRunIdAndPedestrianId(runId, pedestrianId);
            return ResponseEntity.ok(new SimulationController.ApiResponse(true, "获取成功", pedestrianData));
        } catch (Exception e) {
            logger.error("获取特定行人数据失败，运行ID: {}, 行人ID: {}", runId, pedestrianId, e);
            return ResponseEntity.internalServerError()
                .body(new SimulationController.ApiResponse(false, "获取数据失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取指定时间范围内的行人数据
     */
    @GetMapping("/pedestrians/{runId}/time-range")
    public ResponseEntity<?> getPedestrianDataByTimeRange(
            @PathVariable Integer runId,
            @RequestParam BigDecimal startTime,
            @RequestParam BigDecimal endTime) {

        logger.info("获取时间范围内的行人数据，运行ID: {}, 时间范围: {} - {}", runId, startTime, endTime);

        try {
            List<PedestrianData> pedestrianData = simulationDataService.getPedestrianDataByTimeRange(runId, startTime, endTime);
            return ResponseEntity.ok(new SimulationController.ApiResponse(true, "获取成功", pedestrianData));
        } catch (Exception e) {
            logger.error("获取时间范围内的行人数据失败", e);
            return ResponseEntity.internalServerError()
                .body(new SimulationController.ApiResponse(false, "获取数据失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取指定区域的行人数据
     */
    @GetMapping("/pedestrians/{runId}/area/{areaName}")
    public ResponseEntity<?> getPedestrianDataByArea(
            @PathVariable Integer runId,
            @PathVariable String areaName) {

        logger.info("获取区域行人数据，运行ID: {}, 区域: {}", runId, areaName);

        try {
            List<PedestrianData> pedestrianData = simulationDataService.getPedestrianDataByArea(runId, areaName);
            return ResponseEntity.ok(new SimulationController.ApiResponse(true, "获取成功", pedestrianData));
        } catch (Exception e) {
            logger.error("获取区域行人数据失败", e);
            return ResponseEntity.internalServerError()
                .body(new SimulationController.ApiResponse(false, "获取数据失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取指定仿真时间的行人数据
     */
    @GetMapping("/pedestrians/{runId}/simtime/{sim_time}")
    public ResponseEntity<?> getPedestrianDataBySimTime(
            @PathVariable Integer runId,
            @PathVariable("sim_time") String simTimeStr) {
        logger.info("获取指定仿真时间的行人数据，运行ID: {}, 仿真时间: {}", runId, simTimeStr);
        try {
            BigDecimal simTime = new BigDecimal(simTimeStr);
            List<PedestrianData> pedestrianData = simulationDataService.getPedestrianDataByRunIdAndSimTime(runId, simTime);
            return ResponseEntity.ok(new SimulationController.ApiResponse(true, "获取成功", pedestrianData));
        } catch (Exception e) {
            logger.error("获取指定仿真时间的行人数据失败，运行ID: {}, 仿真时间: {}", runId, simTimeStr, e);
            return ResponseEntity.internalServerError()
                .body(new SimulationController.ApiResponse(false, "获取数据失败: " + e.getMessage(), null));
        }
    }

    /**
     * 统计行人数量
     */
    @GetMapping("/pedestrians/{runId}/count")
    public ResponseEntity<?> countPedestrians(@PathVariable Integer runId) {
        logger.info("统计行人数量，运行ID: {}", runId);

        try {
            Long count = simulationDataService.countPedestriansByRunId(runId);
            return ResponseEntity.ok(new SimulationController.ApiResponse(true, "统计成功", count));
        } catch (Exception e) {
            logger.error("统计行人数量失败，运行ID: {}", runId, e);
            return ResponseEntity.internalServerError()
                .body(new SimulationController.ApiResponse(false, "统计失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取事件日志
     */
    @GetMapping("/events/{runId}")
    public ResponseEntity<?> getEventsLog(@PathVariable Integer runId) {
        logger.info("获取事件日志，运行ID: {}", runId);

        try {
            List<EventsLog> eventsLog = simulationDataService.getEventsLogByRunId(runId);
            return ResponseEntity.ok(new SimulationController.ApiResponse(true, "获取成功", eventsLog));
        } catch (Exception e) {
            logger.error("获取事件日志失败，运行ID: {}", runId, e);
            return ResponseEntity.internalServerError()
                .body(new SimulationController.ApiResponse(false, "获取数据失败: " + e.getMessage(), null));
        }
    }

    /**
     * 根据事件类型获取事件日志
     */
    @GetMapping("/events/{runId}/type/{eventType}")
    public ResponseEntity<?> getEventsLogByType(
            @PathVariable Integer runId,
            @PathVariable String eventType) {

        logger.info("获取特定类型事件日志，运行ID: {}, 事件类型: {}", runId, eventType);

        try {
            List<EventsLog> eventsLog = simulationDataService.getEventsLogByEventType(runId, eventType);
            return ResponseEntity.ok(new SimulationController.ApiResponse(true, "获取成功", eventsLog));
        } catch (Exception e) {
            logger.error("获取特定类型事件日志失败", e);
            return ResponseEntity.internalServerError()
                .body(new SimulationController.ApiResponse(false, "获取数据失败: " + e.getMessage(), null));
        }
    }

    /**
     * 获取事件类型统计
     */
    @GetMapping("/events/{runId}/statistics")
    public ResponseEntity<?> getEventTypeStatistics(@PathVariable Integer runId) {
        logger.info("获取事件类型统计，运行ID: {}", runId);

        try {
            List<Object[]> statistics = simulationDataService.getEventTypeStatistics(runId);
            return ResponseEntity.ok(new SimulationController.ApiResponse(true, "统计成功", statistics));
        } catch (Exception e) {
            logger.error("获取事件类型统计失败，运行ID: {}", runId, e);
            return ResponseEntity.internalServerError()
                .body(new SimulationController.ApiResponse(false, "统计失败: " + e.getMessage(), null));
        }
    }

    /**
     * 批量处理所有没有经纬度信息的行人数据
     */
    @PostMapping("/pedestrians/convert-all")
    public ResponseEntity<?> convertAllPedestrianData() {
        logger.info("开始批量转换所有行人数据的经纬度...");

        try {
            int processedCount = coordinateConversionService.processAllPedestrianDataWithoutLatLon();
            return ResponseEntity.ok(new SimulationController.ApiResponse(true,
                "成功处理 " + processedCount + " 条数据", processedCount));
        } catch (Exception e) {
            logger.error("批量转换行人数据失败", e);
            return ResponseEntity.internalServerError()
                .body(new SimulationController.ApiResponse(false, "转换失败: " + e.getMessage(), null));
        }
    }

    /**
     * 处理指定运行ID的行人数据经纬度转换
     */
    @PostMapping("/pedestrians/{runId}/convert")
    public ResponseEntity<?> convertRunPedestrianData(@PathVariable Integer runId) {
        logger.info("开始转换运行ID {} 的行人数据经纬度...", runId);

        try {
            int processedCount = coordinateConversionService.processRunPedestrianData(runId);
            return ResponseEntity.ok(new SimulationController.ApiResponse(true,
                "成功处理运行ID " + runId + " 的 " + processedCount + " 条数据", processedCount));
        } catch (Exception e) {
            logger.error("转换运行ID {} 的行人数据失败", runId, e);
            return ResponseEntity.internalServerError()
                .body(new SimulationController.ApiResponse(false, "转换失败: " + e.getMessage(), null));
        }
    }
}
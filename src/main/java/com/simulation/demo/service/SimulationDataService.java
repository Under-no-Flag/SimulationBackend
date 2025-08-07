package com.simulation.demo.service;

import com.simulation.demo.entity.EventsLog;
import com.simulation.demo.entity.PedestrianData;
import com.simulation.demo.entity.SimulationRun;
import com.simulation.demo.repository.EventsLogRepository;
import com.simulation.demo.repository.PedestrianDataRepository;
import com.simulation.demo.repository.SimulationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SimulationDataService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationDataService.class);

    @Autowired
    private SimulationRunRepository simulationRunRepository;

    @Autowired
    private PedestrianDataRepository pedestrianDataRepository;

    @Autowired
    private EventsLogRepository eventsLogRepository;

    /**
     * 获取所有模拟运行记录
     */
    public List<SimulationRun> getAllSimulationRuns() {
        logger.info("获取所有模拟运行记录");
        return simulationRunRepository.findAll(Sort.by(Sort.Direction.DESC, "startDate"));
    }

    /**
     * 根据ID获取模拟运行记录
     */
    public Optional<SimulationRun> getSimulationRunById(Integer runId) {
        logger.info("获取模拟运行记录，ID: {}", runId);
        return simulationRunRepository.findById(runId);
    }

    /**
     * 获取指定时间范围内的模拟运行记录
     */
    public List<SimulationRun> getSimulationRunsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        logger.info("获取时间范围内的模拟运行记录: {} - {}", startDate, endDate);
        return simulationRunRepository.findByDateRange(startDate, endDate);
    }

    /**
     * 获取指定运行的行人数据（分页）
     */
    public Page<PedestrianData> getPedestrianDataByRunId(Integer runId, int page, int size) {
        logger.info("获取行人数据，运行ID: {}, 页码: {}, 大小: {}", runId, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("simTime"));
        return pedestrianDataRepository.findByRunId(runId, pageable);
    }

    /**
     * 获取指定运行和行人ID的数据
     */
    public List<PedestrianData> getPedestrianDataByRunIdAndPedestrianId(Integer runId, Integer pedestrianId) {
        logger.info("获取特定行人数据，运行ID: {}, 行人ID: {}", runId, pedestrianId);
        return pedestrianDataRepository.findByRunIdAndPedestrianId(runId, pedestrianId);
    }

    /**
     * 获取指定时间范围内的行人数据
     */
    public List<PedestrianData> getPedestrianDataByTimeRange(Integer runId, BigDecimal startTime, BigDecimal endTime) {
        logger.info("获取时间范围内的行人数据，运行ID: {}, 时间范围: {} - {}", runId, startTime, endTime);
        return pedestrianDataRepository.findByRunIdAndTimeRange(runId, startTime, endTime);
    }

    /**
     * 获取指定区域的行人数据
     */
    public List<PedestrianData> getPedestrianDataByArea(Integer runId, String areaName) {
        logger.info("获取区域行人数据，运行ID: {}, 区域: {}", runId, areaName);
        return pedestrianDataRepository.findByRunIdAndAreaName(runId, areaName);
    }

    /**
     * 统计指定运行中的行人数量
     */
    public Long countPedestriansByRunId(Integer runId) {
        logger.info("统计行人数量，运行ID: {}", runId);
        return pedestrianDataRepository.countDistinctPedestriansByRunId(runId);
    }

    /**
     * 获取指定运行的事件日志
     */
    public List<EventsLog> getEventsLogByRunId(Integer runId) {
        logger.info("获取事件日志，运行ID: {}", runId);
        return eventsLogRepository.findByRunId(runId);
    }

    /**
     * 根据事件类型获取事件日志
     */
    public List<EventsLog> getEventsLogByEventType(Integer runId, String eventType) {
        logger.info("获取特定类型事件日志，运行ID: {}, 事件类型: {}", runId, eventType);
        return eventsLogRepository.findByRunIdAndEventType(runId, eventType);
    }

    /**
     * 获取指定行人的事件日志
     */
    public List<EventsLog> getEventsLogByPedestrianId(Integer runId, Integer pedestrianId) {
        logger.info("获取特定行人事件日志，运行ID: {}, 行人ID: {}", runId, pedestrianId);
        return eventsLogRepository.findByRunIdAndPedestrianId(runId, pedestrianId);
    }

    /**
     * 获取指定时间范围内的事件日志
     */
    public List<EventsLog> getEventsLogByTimeRange(Integer runId, BigDecimal startTime, BigDecimal endTime) {
        logger.info("获取时间范围内的事件日志，运行ID: {}, 时间范围: {} - {}", runId, startTime, endTime);
        return eventsLogRepository.findByRunIdAndTimeRange(runId, startTime, endTime);
    }

    /**
     * 统计事件类型数量
     */
    public List<Object[]> getEventTypeStatistics(Integer runId) {
        logger.info("统计事件类型数量，运行ID: {}", runId);
        return eventsLogRepository.countEventTypesByRunId(runId);
    }

    /**
     * 保存行人数据
     */
    public PedestrianData savePedestrianData(PedestrianData pedestrianData) {
        logger.debug("保存行人数据: {}", pedestrianData.getId());
        return pedestrianDataRepository.save(pedestrianData);
    }

    /**
     * 批量保存行人数据
     */
    public List<PedestrianData> savePedestrianDataBatch(List<PedestrianData> pedestrianDataList) {
        logger.info("批量保存行人数据，数量: {}", pedestrianDataList.size());
        return pedestrianDataRepository.saveAll(pedestrianDataList);
    }

    /**
     * 保存事件日志
     */
    public EventsLog saveEventsLog(EventsLog eventsLog) {
        logger.debug("保存事件日志: {}", eventsLog.getEventId());
        return eventsLogRepository.save(eventsLog);
    }

    /**
     * 批量保存事件日志
     */
    public List<EventsLog> saveEventsLogBatch(List<EventsLog> eventsLogList) {
        logger.info("批量保存事件日志，数量: {}", eventsLogList.size());
        return eventsLogRepository.saveAll(eventsLogList);
    }

    /**
     * 根据运行ID和仿真时间查询行人数据
     */
    public List<PedestrianData> getPedestrianDataByRunIdAndSimTime(Integer runId, BigDecimal simTime) {
        logger.info("获取指定仿真时间的行人数据，运行ID: {}, 仿真时间: {}", runId, simTime);
        return pedestrianDataRepository.findByRunIdAndSimTime(runId, simTime);
    }

    /**
     * 获取没有经纬度信息的行人数据
     */
    public List<PedestrianData> getPedestrianDataWithoutLatLon() {
        logger.info("获取没有经纬度信息的行人数据");
        return pedestrianDataRepository.findByLatIsNullOrLonIsNull();
    }

    /**
     * 批量更新行人数据的经纬度信息
     */
    public void updatePedestrianDataLatLon(List<PedestrianData> pedestrianDataList) {
        logger.info("批量更新行人数据经纬度信息，数量: {}", pedestrianDataList.size());
        pedestrianDataRepository.saveAll(pedestrianDataList);
    }
}

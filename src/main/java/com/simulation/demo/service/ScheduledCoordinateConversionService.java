package com.simulation.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 定时坐标转换服务 - 自动处理没有经纬度信息的行人数据
 */
@Service
@ConditionalOnProperty(name = "coordinate.conversion.scheduled.enabled", havingValue = "true", matchIfMissing = false)
public class ScheduledCoordinateConversionService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledCoordinateConversionService.class);

    @Autowired
    private CoordinateConversionService coordinateConversionService;

    /**
     * 每10秒检查一次是否有新的需要转换的数据
     */
    @Scheduled(fixedRate = 10000) // 10秒
    public void autoConvertCoordinates() {
        try {
            logger.debug("开始自动检查需要转换坐标的行人数据...");
            
            int processedCount = coordinateConversionService.processAllPedestrianDataWithoutLatLon();
            
            if (processedCount > 0) {
                logger.info("自动转换完成，处理了 {} 条行人数据", processedCount);
            } else {
                logger.debug("没有需要转换的数据");
            }
            
        } catch (Exception e) {
            logger.error("自动坐标转换失败", e);
        }
    }

    /**
     * 每5分钟执行一次更全面的检查（防止遗漏）
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void comprehensiveConvertCoordinates() {
        try {
            logger.info("开始全面检查需要转换坐标的行人数据...");
            
            int processedCount = coordinateConversionService.processAllPedestrianDataWithoutLatLon();
            
            if (processedCount > 0) {
                logger.info("全面转换完成，处理了 {} 条行人数据", processedCount);
            }
            
        } catch (Exception e) {
            logger.error("全面坐标转换失败", e);
        }
    }
}

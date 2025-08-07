package com.simulation.demo.service;

import com.simulation.demo.entity.PedestrianData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import util.GeoUtil;

import java.math.BigDecimal;
import java.util.List;

/**
 * 坐标转换服务 - 将像素坐标转换为经纬度
 */
@Service
public class CoordinateConversionService {

    private static final Logger logger = LoggerFactory.getLogger(CoordinateConversionService.class);

    @Autowired
    private SimulationDataService simulationDataService;

    // 校准参数（根据你提供的数据）
    private static final double[] PX = {97, 250, 906, 1923, 1920};
    private static final double[] PY = {783, 1285, 1185, 403, 872};
    private static final double[] LAT = {31.237720, 31.233763, 31.234596, 31.240663, 31.236981};
    private static final double[] LON = {121.473619, 121.475118, 121.481113, 121.490204, 121.490137};
    private static final double IMG_HEIGHT = 1521;

    private boolean isCalibrated = false;

    /**
     * 初始化校准参数
     */
    private void initializeCalibration() {
        if (!isCalibrated) {
            GeoUtil.calibrate(PX, PY, LAT, LON, IMG_HEIGHT);
            isCalibrated = true;
            logger.info("GeoUtil 校准完成");
        }
    }

    /**
     * 处理所有没有经纬度信息的行人数据
     */
    public int processAllPedestrianDataWithoutLatLon() {
        logger.info("开始处理没有经纬度信息的行人数据...");
        
        // 初始化校准参数
        initializeCalibration();
        
        // 获取没有经纬度信息的数据
        List<PedestrianData> dataList = simulationDataService.getPedestrianDataWithoutLatLon();
        
        if (dataList.isEmpty()) {
            logger.info("没有需要处理的数据");
            return 0;
        }
        
        logger.info("找到 {} 条需要处理的数据", dataList.size());
        
        // 批量转换坐标
        int processedCount = 0;
        for (PedestrianData data : dataList) {
            if (data.getPosX() != null && data.getPosY() != null) {
                try {
                    double posX = data.getPosX().doubleValue();
                    double posY = data.getPosY().doubleValue();
                    
                    // 转换坐标
                    double[] geo = GeoUtil.scr2geo(posX, posY);
                    
                    // 更新经纬度
                    data.setLat(geo[0]);
                    data.setLon(geo[1]);
                    
                    processedCount++;
                    
                    if (processedCount % 100 == 0) {
                        logger.debug("已处理 {} 条数据", processedCount);
                    }
                    
                } catch (Exception e) {
                    logger.error("转换坐标失败，数据ID: {}, posX: {}, posY: {}", 
                               data.getId(), data.getPosX(), data.getPosY(), e);
                }
            }
        }
        
        // 批量保存
        if (processedCount > 0) {
            simulationDataService.updatePedestrianDataLatLon(dataList);
            logger.info("成功处理并更新了 {} 条行人数据的经纬度信息", processedCount);
        }
        
        return processedCount;
    }

    /**
     * 处理指定运行ID的行人数据
     */
    public int processRunPedestrianData(Integer runId) {
        logger.info("开始处理运行ID {} 的行人数据...", runId);
        
        // 初始化校准参数
        initializeCalibration();
        
        // 获取指定运行的所有数据
        List<PedestrianData> dataList = simulationDataService.getPedestrianDataByRunId(runId, 0, Integer.MAX_VALUE).getContent();
        
        int processedCount = 0;
        for (PedestrianData data : dataList) {
            // 只处理没有经纬度信息的数据
            if ((data.getLat() == null || data.getLon() == null) && 
                data.getPosX() != null && data.getPosY() != null) {
                
                try {
                    double posX = data.getPosX().doubleValue();
                    double posY = data.getPosY().doubleValue();
                    
                    // 转换坐标
                    double[] geo = GeoUtil.scr2geo(posX, posY);
                    
                    // 更新经纬度
                    data.setLat(geo[0]);
                    data.setLon(geo[1]);
                    
                    processedCount++;
                    
                } catch (Exception e) {
                    logger.error("转换坐标失败，数据ID: {}, posX: {}, posY: {}", 
                               data.getId(), data.getPosX(), data.getPosY(), e);
                }
            }
        }
        
        // 批量保存
        if (processedCount > 0) {
            simulationDataService.updatePedestrianDataLatLon(dataList);
            logger.info("成功处理并更新了运行ID {} 的 {} 条行人数据", runId, processedCount);
        }
        
        return processedCount;
    }
}

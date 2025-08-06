package com.simulation.demo.repository;

import com.simulation.demo.entity.PedestrianData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PedestrianDataRepository extends JpaRepository<PedestrianData, Long> {
    
    /**
     * 根据运行ID查找行人数据
     */
    List<PedestrianData> findByRunId(Integer runId);
    
    /**
     * 根据运行ID分页查找行人数据
     */
    Page<PedestrianData> findByRunId(Integer runId, Pageable pageable);
    
    /**
     * 根据运行ID和行人ID查找数据
     */
    List<PedestrianData> findByRunIdAndPedestrianId(Integer runId, Integer pedestrianId);
    
    /**
     * 根据运行ID和时间范围查找数据
     */
    @Query("SELECT pd FROM PedestrianData pd WHERE pd.runId = :runId AND pd.simTime >= :startTime AND pd.simTime <= :endTime ORDER BY pd.simTime")
    List<PedestrianData> findByRunIdAndTimeRange(@Param("runId") Integer runId, 
                                                 @Param("startTime") BigDecimal startTime, 
                                                 @Param("endTime") BigDecimal endTime);
    
    /**
     * 根据区域名称查找数据
     */
    List<PedestrianData> findByRunIdAndAreaName(Integer runId, String areaName);
    
    /**
     * 统计指定运行中的行人数量
     */
    @Query("SELECT COUNT(DISTINCT pd.pedestrianId) FROM PedestrianData pd WHERE pd.runId = :runId")
    Long countDistinctPedestriansByRunId(@Param("runId") Integer runId);
}

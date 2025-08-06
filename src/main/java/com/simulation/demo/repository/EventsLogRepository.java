package com.simulation.demo.repository;

import com.simulation.demo.entity.EventsLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface EventsLogRepository extends JpaRepository<EventsLog, Long> {
    
    /**
     * 根据运行ID查找事件日志
     */
    List<EventsLog> findByRunId(Integer runId);
    
    /**
     * 根据运行ID和事件类型查找事件
     */
    List<EventsLog> findByRunIdAndEventType(Integer runId, String eventType);
    
    /**
     * 根据运行ID和行人ID查找事件
     */
    List<EventsLog> findByRunIdAndPedestrianId(Integer runId, Integer pedestrianId);
    
    /**
     * 根据时间范围查找事件
     */
    @Query("SELECT el FROM EventsLog el WHERE el.runId = :runId AND el.simTime >= :startTime AND el.simTime <= :endTime ORDER BY el.simTime")
    List<EventsLog> findByRunIdAndTimeRange(@Param("runId") Integer runId, 
                                           @Param("startTime") BigDecimal startTime, 
                                           @Param("endTime") BigDecimal endTime);
    
    /**
     * 统计指定运行中的事件类型数量
     */
    @Query("SELECT el.eventType, COUNT(el) FROM EventsLog el WHERE el.runId = :runId GROUP BY el.eventType")
    List<Object[]> countEventTypesByRunId(@Param("runId") Integer runId);
}

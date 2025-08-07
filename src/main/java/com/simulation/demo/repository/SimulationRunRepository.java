package com.simulation.demo.repository;

import com.simulation.demo.entity.SimulationRun;
import com.anylogic.engine.Experiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SimulationRunRepository extends JpaRepository<SimulationRun, Integer> {

    /**
     * 根据状态查找模拟运行
     */
    List<SimulationRun> findByState(Experiment.State state);

    /**
     * 根据模型名称查找模拟运行
     */
    List<SimulationRun> findByModelName(String modelName);

    /**
     * 查找指定时间范围内的模拟运行
     */
    @Query("SELECT sr FROM SimulationRun sr WHERE sr.startDate >= :startDate AND sr.startDate <= :endDate")
    List<SimulationRun> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);
}

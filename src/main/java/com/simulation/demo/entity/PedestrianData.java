package com.simulation.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedestrian_data")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "simulationRun"})
public class PedestrianData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Integer runId;

    @Column(name = "sim_time", precision = 10, scale = 3)
    private BigDecimal simTime;

    @Column(name = "model_date")
    private LocalDateTime modelDate;

    @Column(name = "pedestrian_id", nullable = false)
    private Integer pedestrianId;

    @Column(name = "pos_x", precision = 10, scale = 3)
    private BigDecimal posX;

    @Column(name = "pos_y", precision = 10, scale = 3)
    private BigDecimal posY;

    @Column(name = "pos_z", precision = 8, scale = 3)
    private BigDecimal posZ;

    @Column(name = "speed", precision = 8, scale = 3)
    private BigDecimal speed;

    @Column(name = "area_name", length = 100)
    private String areaName;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lon")
    private Double lon;

    // 关联到SimulationRun
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", insertable = false, updatable = false)
    private SimulationRun simulationRun;

    // 构造函数
    public PedestrianData() {}

    public PedestrianData(Integer runId, Integer pedestrianId) {
        this.runId = runId;
        this.pedestrianId = pedestrianId;
    }

    // Getter 和 Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getRunId() {
        return runId;
    }

    public void setRunId(Integer runId) {
        this.runId = runId;
    }

    public BigDecimal getSimTime() {
        return simTime;
    }

    public void setSimTime(BigDecimal simTime) {
        this.simTime = simTime;
    }

    public LocalDateTime getModelDate() {
        return modelDate;
    }

    public void setModelDate(LocalDateTime modelDate) {
        this.modelDate = modelDate;
    }

    public Integer getPedestrianId() {
        return pedestrianId;
    }

    public void setPedestrianId(Integer pedestrianId) {
        this.pedestrianId = pedestrianId;
    }

    public BigDecimal getPosX() {
        return posX;
    }

    public void setPosX(BigDecimal posX) {
        this.posX = posX;
    }

    public BigDecimal getPosY() {
        return posY;
    }

    public void setPosY(BigDecimal posY) {
        this.posY = posY;
    }

    public BigDecimal getPosZ() {
        return posZ;
    }

    public void setPosZ(BigDecimal posZ) {
        this.posZ = posZ;
    }

    public BigDecimal getSpeed() {
        return speed;
    }

    public void setSpeed(BigDecimal speed) {
        this.speed = speed;
    }

    public String getAreaName() {
        return areaName;
    }
    public Double getLat() {
        return lat;
    }
    public void setLat(Double lat) {
        this.lat = lat;
    }
    public Double getLon() {
        return lon;
    }
    public void setLon(Double lon) {
        this.lon = lon;
    }
    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public SimulationRun getSimulationRun() {
        return simulationRun;
    }

    public void setSimulationRun(SimulationRun simulationRun) {
        this.simulationRun = simulationRun;
    }
}

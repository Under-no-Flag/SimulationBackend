package com.simulation.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Entity
@Table(name = "events_log")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "simulationRun"})
public class EventsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "run_id", nullable = false)
    private Integer runId;

    @Column(name = "sim_time", precision = 10, scale = 3)
    private BigDecimal simTime;

    @Column(name = "pedestrian_id")
    private Integer pedestrianId;

    @NotBlank(message = "事件类型不能为空")
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_details", columnDefinition = "TEXT")
    private String eventDetails;

    // 关联到SimulationRun
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", insertable = false, updatable = false)
    private SimulationRun simulationRun;

    // 构造函数
    public EventsLog() {}

    public EventsLog(Integer runId, String eventType) {
        this.runId = runId;
        this.eventType = eventType;
    }

    // Getter 和 Setter
    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
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

    public Integer getPedestrianId() {
        return pedestrianId;
    }

    public void setPedestrianId(Integer pedestrianId) {
        this.pedestrianId = pedestrianId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventDetails() {
        return eventDetails;
    }

    public void setEventDetails(String eventDetails) {
        this.eventDetails = eventDetails;
    }

    public SimulationRun getSimulationRun() {
        return simulationRun;
    }

    public void setSimulationRun(SimulationRun simulationRun) {
        this.simulationRun = simulationRun;
    }
}

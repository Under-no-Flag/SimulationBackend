package com.simulation.demo.entity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.simulation.demo.entity.converter.SimulationStatusConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "simulation_runs")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SimulationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    private Integer runId;

    @NotBlank(message = "模型名称不能为空")
    @Column(name = "model_name", nullable = false, length = 255)
    private String modelName;

    @NotNull(message = "开始时间不能为空")
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "simulation_parameters", columnDefinition = "TEXT")
    private String simulationParameters;

    @Column(name = "engine_parameters", columnDefinition = "TEXT")
    private String engineParameters;

    @Column(name = "agent_parameters", columnDefinition = "TEXT")
    private String agentParameters;

    @Convert(converter = SimulationStatusConverter.class)
    @Column(name = "status")
    private SimulationStatus status;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "model_port")
    private Integer modelPort;

        // 修改关联关系，添加 @JsonIgnoreProperties
    @OneToMany(mappedBy = "simulationRun", fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "simulationRun"})
    private List<PedestrianData> pedestrianDataList;

    @OneToMany(mappedBy = "simulationRun", fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "simulationRun"})
    private List<EventsLog> eventsLogList;

    // 构造函数
    public SimulationRun() {}

    public SimulationRun(String modelName, LocalDateTime startDate) {
        this.modelName = modelName;
        this.startDate = startDate;
        this.status = SimulationStatus.PENDING;
    }

    // Getter 和 Setter
    public Integer getRunId() {
        return runId;
    }

    public void setRunId(Integer runId) {
        this.runId = runId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public String getSimulationParameters() {
        return simulationParameters;
    }

    public void setSimulationParameters(String simulationParameters) {
        this.simulationParameters = simulationParameters;
    }

    public String getEngineParameters() {
        return engineParameters;
    }

    public void setEngineParameters(String engineParameters) {
        this.engineParameters = engineParameters;
    }

    public String getAgentParameters() {
        return agentParameters;
    }

    public void setAgentParameters(String agentParameters) {
        this.agentParameters = agentParameters;
    }

    public SimulationStatus getStatus() {
        return status;
    }

    public void setStatus(SimulationStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getModelPort() {
        return modelPort;
    }

    public void setModelPort(Integer modelPort) {
        this.modelPort = modelPort;
    }

    public List<PedestrianData> getPedestrianDataList() {
        return pedestrianDataList;
    }

    public void setPedestrianDataList(List<PedestrianData> pedestrianDataList) {
        this.pedestrianDataList = pedestrianDataList;
    }

    public List<EventsLog> getEventsLogList() {
        return eventsLogList;
    }

    public void setEventsLogList(List<EventsLog> eventsLogList) {
        this.eventsLogList = eventsLogList;
    }
}

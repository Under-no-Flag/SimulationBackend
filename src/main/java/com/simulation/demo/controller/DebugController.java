package com.simulation.demo.controller;

import com.simulation.demo.entity.SimulationRun;
import com.simulation.demo.service.SimulationDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private SimulationDataService simulationDataService;

    @GetMapping("/raw-simulation-runs")
    public ResponseEntity<?> getRawSimulationRuns() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 1. 原始SQL查询
            List<Map<String, Object>> rawResults = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM simulation_runs")) {
                
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("run_id", rs.getInt("run_id"));
                    row.put("model_name", rs.getString("model_name"));
                    row.put("start_date", rs.getTimestamp("start_date"));
                    row.put("end_date", rs.getTimestamp("end_date"));
                    row.put("simulation_parameters", rs.getString("simulation_parameters"));
                    row.put("status", rs.getString("status"));
                    row.put("description", rs.getString("description"));
                    rawResults.add(row);
                }
            }
            response.put("rawSqlResults", rawResults);
            
            // 2. JPA查询
            try {
                List<SimulationRun> jpaResults = simulationDataService.getAllSimulationRuns();
                response.put("jpaResults", jpaResults);
                response.put("jpaResultCount", jpaResults.size());
            } catch (Exception e) {
                response.put("jpaError", e.getMessage());
                response.put("jpaErrorType", e.getClass().getSimpleName());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("type", e.getClass().getSimpleName());
            error.put("stackTrace", e.getStackTrace());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}

package com.simulation.demo.test.manual;

import com.simulation.demo.entity.PedestrianData;
import com.simulation.demo.entity.SimulationRun;
import com.simulation.demo.service.SimulationDataService;
import com.simulation.demo.test.utils.TestDataGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 手动测试脚本
 * 运行应用时会自动执行，用于手动测试数据服务功能
 */
@Component
public class ManualTestRunner implements CommandLineRunner {
    
    @Autowired
    private SimulationDataService simulationDataService;
    
    @Override
    public void run(String... args) throws Exception {
        // 只在包含 "manual-test" 参数时运行
        boolean runManualTest = false;
        for (String arg : args) {
            if ("manual-test".equals(arg)) {
                runManualTest = true;
                break;
            }
        }
        
        if (!runManualTest) {
            return;
        }
        
        System.out.println("=== 开始手动测试 SimulationDataService ===");
        
        try {
            // 1. 测试获取所有模拟运行
            System.out.println("\n1. 测试获取所有模拟运行:");
            List<SimulationRun> allRuns = simulationDataService.getAllSimulationRuns();
            System.out.println("找到 " + allRuns.size() + " 个模拟运行");
            allRuns.forEach(run -> 
                System.out.println("  - ID: " + run.getRunId() + ", 模型: " + run.getModelName() + 
                                  ", 状态: " + run.getStatus()));
            
            if (allRuns.isEmpty()) {
                System.out.println("没有找到现有的模拟运行，创建测试数据...");
                createTestData();
                allRuns = simulationDataService.getAllSimulationRuns();
            }
            
            if (!allRuns.isEmpty()) {
                SimulationRun testRun = allRuns.get(0);
                Integer runId = testRun.getRunId();
                
                // 2. 测试获取行人数据
                System.out.println("\n2. 测试获取行人数据 (运行ID: " + runId + "):");
                var pedestrianPage = simulationDataService.getPedestrianDataByRunId(runId, 0, 5);
                System.out.println("总共 " + pedestrianPage.getTotalElements() + " 条行人数据，显示前5条:");
                pedestrianPage.getContent().forEach(data ->
                    System.out.println("  - 行人ID: " + data.getPedestrianId() + 
                                      ", 位置: (" + data.getPosX() + ", " + data.getPosY() + ")" +
                                      ", 时间: " + data.getSimTime()));
                
                // 3. 测试统计功能
                System.out.println("\n3. 测试统计行人数量:");
                Long pedestrianCount = simulationDataService.countPedestriansByRunId(runId);
                System.out.println("运行ID " + runId + " 中共有 " + pedestrianCount + " 个不同的行人");
                
                // 4. 测试事件日志
                System.out.println("\n4. 测试获取事件日志:");
                var events = simulationDataService.getEventsLogByRunId(runId);
                System.out.println("找到 " + events.size() + " 个事件:");
                events.stream().limit(5).forEach(event ->
                    System.out.println("  - 事件类型: " + event.getEventType() + 
                                      ", 行人ID: " + event.getPedestrianId() +
                                      ", 时间: " + event.getSimTime()));
                
                // 5. 测试时间范围查询
                System.out.println("\n5. 测试时间范围查询:");
                var timeRangeData = simulationDataService.getPedestrianDataByTimeRange(
                    runId, new BigDecimal("0"), new BigDecimal("50"));
                System.out.println("时间范围 0-50 内的数据: " + timeRangeData.size() + " 条");
                
                // 6. 测试区域查询
                System.out.println("\n6. 测试区域查询:");
                if (!pedestrianPage.getContent().isEmpty()) {
                    String testArea = pedestrianPage.getContent().get(0).getAreaName();
                    if (testArea != null) {
                        var areaData = simulationDataService.getPedestrianDataByArea(runId, testArea);
                        System.out.println("区域 '" + testArea + "' 中的数据: " + areaData.size() + " 条");
                    }
                }
            }
            
            System.out.println("\n=== 手动测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("手动测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void createTestData() {
        System.out.println("创建测试数据...");
        
        // 创建测试模拟运行
        SimulationRun testRun = TestDataGenerator.createTestSimulationRun("ManualTestModel");
        // 注意：这里需要先保存模拟运行，但我们没有直接访问repository的权限
        // 在实际使用中，您可能需要通过其他方式创建测试数据
        
        System.out.println("请先通过API或其他方式创建一些测试数据，然后重新运行手动测试。");
    }
}

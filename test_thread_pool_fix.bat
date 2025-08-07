@echo off
echo 测试线程池配置修复效果
echo ================================

echo.
echo 1. 启动应用程序...
start /B mvn spring-boot:run

echo.
echo 等待应用程序启动...
timeout /t 10 /nobreak > nul

echo.
echo 2. 测试线程池配置...
curl -X GET "http://localhost:9527/api/simulation/test-thread-pool" -H "Content-Type: application/json"

echo.
echo 3. 检查服务健康状态...
curl -X GET "http://localhost:9527/api/simulation/health" -H "Content-Type: application/json"

echo.
echo 4. 启动一个仿真测试...
curl -X POST "http://localhost:9527/api/simulation/start" -H "Content-Type: application/json" -d "{\"modelName\":\"NanJingDong\",\"engineParameters\":{},\"agentParameters\":{\"runId\":1},\"description\":\"线程池修复测试\"}"

echo.
echo 测试完成！
echo 请检查输出结果，确认：
echo - simulationWorkerDaemon 应该为 false
echo - threadPoolShutdown 应该为 false
echo - testPassed 应该为 true
echo.
pause

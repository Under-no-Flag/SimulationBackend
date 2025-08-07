@echo off
echo ========================================
echo 测试仿真控制功能
echo ========================================

echo.
echo 1. 启动应用...
start /B java -jar target/demo-0.0.1-SNAPSHOT.jar

echo.
echo 等待应用启动...
timeout /t 10 /nobreak > nul

echo.
echo 2. 测试健康状态...
curl -X GET "http://localhost:8080/api/simulation/health" -H "Content-Type: application/json"

echo.
echo 3. 测试线程池配置...
curl -X GET "http://localhost:8080/api/simulation/test-thread-pool" -H "Content-Type: application/json"

echo.
echo 4. 启动仿真...
curl -X POST "http://localhost:8080/api/simulation/start" -H "Content-Type: application/json" -d "{\"modelName\":\"NanJingDong\",\"description\":\"测试仿真控制功能\",\"engineParameters\":{\"realTimeScale\":1000.0},\"agentParameters\":{\"simulTargetTime\":\"2025-05-31 11:30:00\"}}"

echo.
echo 等待仿真启动...
timeout /t 5 /nobreak > nul

echo.
echo 5. 获取仿真状态...
curl -X GET "http://localhost:8080/api/simulation/status" -H "Content-Type: application/json"

echo.
echo 6. 获取所有仿真记录...
curl -X GET "http://localhost:8080/api/simulation/runs" -H "Content-Type: application/json"

echo.
echo 7. 测试暂停仿真 (runId=1)...
curl -X POST "http://localhost:8080/api/simulation/pause/1" -H "Content-Type: application/json"

echo.
echo 等待暂停完成...
timeout /t 3 /nobreak > nul

echo.
echo 8. 测试恢复仿真 (runId=1)...
curl -X POST "http://localhost:8080/api/simulation/resume/1" -H "Content-Type: application/json"

echo.
echo 等待恢复完成...
timeout /t 3 /nobreak > nul

echo.
echo 9. 测试重置仿真 (runId=1)...
curl -X POST "http://localhost:8080/api/simulation/reset/1" -H "Content-Type: application/json"

echo.
echo 等待重置完成...
timeout /t 3 /nobreak > nul

echo.
echo 10. 测试停止仿真 (runId=1)...
curl -X POST "http://localhost:8080/api/simulation/stop/1" -H "Content-Type: application/json"

echo.
echo 11. 最终状态检查...
curl -X GET "http://localhost:8080/api/simulation/status" -H "Content-Type: application/json"

echo.
echo ========================================
echo 测试完成
echo ========================================

echo.
echo 按任意键退出...
pause > nul

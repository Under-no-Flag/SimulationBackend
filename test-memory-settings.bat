@echo off
echo ========================================
echo 测试内存设置是否生效
echo ========================================

echo.
echo 1. 检查当前JVM参数...
echo MAVEN_OPTS: %MAVEN_OPTS%

echo.
echo 2. 启动应用并测试内存设置...
echo 启动Spring Boot应用...

REM 设置内存参数
set "MAVEN_OPTS=-Xmx49152m -Xms1024m"
set "JAVA_OPTS=-Xmx49152m -Xms1024m"

echo 设置的内存参数: %MAVEN_OPTS%
echo JAVA_OPTS: %JAVA_OPTS%

REM 启动应用
start /B mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx49152m -Xms1024m"

echo.
echo 3. 等待应用启动...
timeout /t 30 /nobreak > nul

echo.
echo 4. 测试API端点...
curl -s http://localhost:9527/api/simulation/system-resources

echo.
echo 5. 检查进程内存使用情况...
wmic process where "name='java.exe'" get ProcessId,WorkingSetSize,PageFileUsage /format:table

echo.
echo ========================================
echo 测试完成
echo ========================================
pause

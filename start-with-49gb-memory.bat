@echo off
echo ========================================
echo 启动Spring Boot应用 - 49GB内存配置
echo ========================================

REM 设置环境变量
set "MAVEN_OPTS=-Xmx49152m -Xms8192m"
set "JAVA_OPTS=-Xmx49152m -Xms8192m"

echo 设置的内存参数:
echo MAVEN_OPTS: %MAVEN_OPTS%
echo JAVA_OPTS: %JAVA_OPTS%

echo.
echo 启动应用...
echo.

REM 启动Spring Boot应用
mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx49152m -Xms8192m"

pause

@echo off
REM 设置Maven内存参数set "MAVEN_OPTS=-Xmx49152m -Xms8192m"

echo 启动Spring Boot应用，内存设置: %MAVEN_OPTS%
echo 最大堆内存: 49GB
echo 初始堆内存: 1GB

REM 启动Spring Boot应用
mvnw.cmd spring-boot:run

pause

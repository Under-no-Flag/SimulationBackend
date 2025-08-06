@echo off
REM 设置环境变量，模拟命令行中的 lib/* 和 model.jar
set "AL_LIBS=%~dp0lib\*"
set "MODEL_JAR=%~dp0model.jar"
set "MAVEN_OPTS=-Xmx2g -cp %MODEL_JAR%;%AL_LIBS%"

echo 使用类路径: %MODEL_JAR%;%AL_LIBS%
echo MAVEN_OPTS: %MAVEN_OPTS%

REM 运行 Maven 测试
mvnw.cmd test

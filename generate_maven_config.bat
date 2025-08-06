@echo off
REM 生成Maven配置的脚本
echo 生成完整的Maven Surefire配置...

set "LIB_DIR=%~dp0lib"
echo ^<additionalClasspathElements^> > maven_classpath.xml

REM 添加model.jar
echo     ^<additionalClasspathElement^>%~dp0model.jar^</additionalClasspathElement^> >> maven_classpath.xml

REM 添加所有lib目录下的JAR文件
for %%f in ("%LIB_DIR%\*.jar") do (
    echo     ^<additionalClasspathElement^>%~dp0lib\%%~nxf^</additionalClasspathElement^> >> maven_classpath.xml
)

echo ^</additionalClasspathElements^> >> maven_classpath.xml

echo 配置已生成到 maven_classpath.xml
echo.
echo 命令行工作原理:
echo   java -cp "lib/*;model.jar" - 操作系统自动展开 lib/* 为所有JAR
echo.
echo Maven需要显式列出:
echo   需要在 pom.xml 中逐个声明每个 JAR 文件
echo.
echo 这就是命令行能工作但Maven不能的根本原因！

#!/bin/bash

# 设置Maven内存参数
export MAVEN_OPTS="-Xmx8g -Xms2g"

echo "启动Spring Boot应用，内存设置: $MAVEN_OPTS"
echo "最大堆内存: 8GB"
echo "初始堆内存: 2GB"

# 启动Spring Boot应用
./mvnw spring-boot:run

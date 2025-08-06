# AnyLogic 仿真项目依赖配置指南

## 概述

本项目已从使用外部进程启动 AnyLogic 模型改为直接使用 Java API 调用。这需要添加必要的 JAR 依赖项。

## 必需的文件

### 1. 模型文件
- **文件**: `model.jar`
- **位置**: 项目根目录
- **来源**: 从 AnyLogic 导出的模型 JAR 文件
- **说明**: 包含具体的仿真模型类（如 `nanjingdong.Simulation`）

### 2. AnyLogic 引擎库
需要将以下 JAR 文件放在 `lib/` 目录下：

- `com.anylogic.engine.jar` - AnyLogic 核心引擎
- `com.anylogic.engine.sa.jar` - AnyLogic 独立应用支持
- `Core.jar` - AnyLogic 核心库

### 3. 数据集文件
- **文件**: `dataset/selected_cameras_20250531.xlsx`
- **位置**: `dataset/` 目录
- **说明**: 仿真所需的输入数据

## 文件结构

```
项目根目录/
├── model.jar                           # 模型文件
├── dataset/
│   └── selected_cameras_20250531.xlsx  # 数据集
├── lib/
│   ├── com.anylogic.engine.jar         # AnyLogic 引擎
│   ├── com.anylogic.engine.sa.jar      # 独立应用支持
│   ├── Core.jar                        # 核心库
│   └── ... (其他库文件)
└── src/
    └── ... (源代码)
```

## Maven 依赖配置

项目的 `pom.xml` 已经配置了系统路径依赖：

```xml
<!-- AnyLogic Engine - 本地JAR依赖 -->
<dependency>
    <groupId>com.anylogic</groupId>
    <artifactId>engine</artifactId>
    <version>1.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/com.anylogic.engine.jar</systemPath>
</dependency>

<!-- AnyLogic Model JAR -->
<dependency>
    <groupId>com.simulation</groupId>
    <artifactId>model</artifactId>
    <version>1.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/model.jar</systemPath>
</dependency>

<!-- 其他必需的库... -->
```

## 获取依赖文件

### 1. 从 AnyLogic 安装目录获取
如果你已安装 AnyLogic，可以从安装目录复制必要的 JAR 文件：

```
AnyLogic安装目录/
├── lib/
│   ├── com.anylogic.engine.jar
│   ├── com.anylogic.engine.sa.jar
│   ├── Core.jar
│   └── ... (其他相关文件)
```

### 2. 从现有项目获取
如果你已经有现有的 AnyLogic 项目，可以从项目的 `lib/` 目录中复制。

### 3. 导出模型 JAR
在 AnyLogic 中：
1. 打开你的模型项目
2. 选择 "文件" -> "导出模型..."
3. 选择 "独立 Java 应用程序"
4. 导出为 JAR 文件，重命名为 `model.jar`

## 验证安装

### 1. 使用 Demo API 检查
启动应用后，访问以下端点检查依赖项：

```bash
GET http://localhost:9527/api/demo/check-dependencies
```

这将返回所有必需文件的状态信息。

### 2. 测试类加载
访问以下端点测试反射和类加载：

```bash
GET http://localhost:9527/api/demo/test-reflection  
```

### 3. 运行测试
执行以下命令运行依赖检查测试：

```bash
mvn test -Dtest=LongRunSimulationTest
```

## 应用配置

在 `application.yml` 中配置仿真参数：

```yaml
anylogic:
  model:
    dataset: "dataset/selected_cameras_20250531.xlsx"
  simulation:
    stoptime: 3600  # 默认仿真时间（秒）
```

## 使用示例

### 启动仿真
```bash
POST http://localhost:9527/api/simulation/start
Content-Type: application/json

{
  "modelName": "南京东路人流仿真",
  "parameters": "{\"pedestrianCount\": 100}",
  "description": "测试仿真运行"
}
```

### 停止仿真
```bash
POST http://localhost:9527/api/simulation/stop/{runId}
```

### 获取状态
```bash
GET http://localhost:9527/api/simulation/status
```

## 故障排除

### 常见问题

1. **ClassNotFoundException**: 检查 `model.jar` 是否存在且包含正确的类
2. **FileNotFoundException**: 检查数据集文件路径是否正确
3. **NoClassDefFoundError**: 检查 AnyLogic 库文件是否完整

### 日志级别
在开发阶段，建议启用详细日志：

```yaml
logging:
  level:
    com.simulation.demo.service.AnyLogicModelService: DEBUG
```

### 测试环境
运行长时间仿真测试：

```bash
mvn test -Dtest=LongRunSimulationTest
```

注意：此测试只有在所有必需文件存在时才会执行。

## 重要注意事项

1. **线程安全**: 仿真在专用线程池中运行，确保不会阻塞主应用
2. **资源管理**: 系统会自动清理已完成的仿真实例
3. **监控**: 提供了详细的日志记录和状态监控
4. **异步执行**: 仿真启动后立即返回，通过状态查询获取进度

## 许可证

请确保你有使用 AnyLogic 库的适当许可证。本项目仅用于演示目的。

# Java 项目知识图谱构建系统

## 项目简介

这是一个基于 Spoon 的 Java 项目静态分析工具，用于构建项目的知识图谱并导入到 Neo4j 数据库。该知识图谱包含丰富的语义信息，可用于代码理解、依赖分析、测试生成等场景。

## 主要特性

- **完整的代码结构分析**：提取类、方法、字段等代码元素
- **丰富的关系建模**：继承、实现、调用、依赖等多种关系
- **批量导入支持**：使用 Neo4j 批量导入，处理大型项目
- **灵活的配置管理**：支持配置文件和命令行参数
- **统一的日志系统**：完整的日志记录和进度追踪

## 快速开始

### 环境要求

- Java 11 或更高版本
- Maven 3.6+
- Neo4j 5.x

### 安装步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd TUGraph
```

2. **配置 Neo4j**

编辑 `config.properties` 文件，设置 Neo4j 相关配置：

```properties
# Neo4j 安装目录
neo4j.home=/usr/local/neo4j

# Neo4j 连接信息
neo4j.uri=bolt://localhost:7687
neo4j.username=neo4j
neo4j.password=12345678
neo4j.database=neo4j
```

3. **编译项目**
```bash
mvn clean package
```

### 使用方法

#### 方式一：使用配置文件

1. 编辑 `config.properties`，设置项目路径：
```properties
project.path=/path/to/your/java/project
project.name=my-project
```

2. 运行程序：
```bash
java -jar target/tugraph-1.0-SNAPSHOT.jar
```

#### 方式二：使用命令行参数

```bash
java -jar target/tugraph-1.0-SNAPSHOT.jar <projectPath> [projectName] [neo4jHome]
```

或使用 Maven：
```bash
mvn exec:java -Dexec.mainClass="com.github.Main" \
  -Dexec.args="/path/to/project project-name /path/to/neo4j"
```

### 导入流程

程序将自动执行以下步骤：

1. **阶段 1**：使用 Spoon 解析项目代码
2. **阶段 2**：导出为 Neo4j 批量导入格式的 CSV 文件
3. **阶段 3**：自动停止 Neo4j 服务
4. **阶段 4**：使用 neo4j-admin 命令批量导入数据
5. **阶段 5**：自动启动 Neo4j 服务

### 验证导入结果

导入完成后，访问 Neo4j 浏览器：http://localhost:7474

执行以下查询验证：

```cypher
// 统计节点数量
MATCH (n) RETURN count(n)

// 查看所有标签
CALL db.labels()

// 查看方法节点示例
MATCH (m:METHOD) RETURN m.name, labels(m) LIMIT 10

// 查看调用关系
MATCH (m1:METHOD)-[r:CALLS]->(m2:METHOD) 
RETURN m1.name, m2.name LIMIT 10
```

### 🎨 配置 Neo4j 可视化样式

为了获得更好的可视化效果，**强烈建议**应用我们提供的样式配置，让不同类型的节点和边显示不同的颜色！

#### 快速应用样式

```bash
# 运行样式配置助手
./apply-neo4j-style.sh
```

或者手动操作：

1. **打开 Neo4j Browser** (http://localhost:7474)
2. **点击左下角齿轮图标** ⚙️ 打开设置
3. **找到 "Browser Style" 编辑器**
4. **复制 `neo4j-style.grass` 文件的内容**并粘贴到编辑器中
5. **点击 "Apply"** 按钮应用样式
6. **刷新图谱视图**查看效果

#### 样式配置效果

应用样式后，你将看到：

**节点颜色**：
- 🟣 **PROJECT** - 紫色，项目根节点
- 🔵 **PACKAGE** - 蓝色，包节点
- 🟢 **FILE** - 青绿色，文件节点
- 🔵 **CLASS/INTERFACE/ENUM** - 蓝色系，类型节点
- 🟢 **METHOD/CONSTRUCTOR** - 绿色系，方法节点
- 🟠 **FIELD** - 橙色，字段节点

**边颜色**：
- 🟢 **CALLS** - 绿色，方法调用
- 🔵 **READS** - 蓝色，读取字段
- 🔴 **WRITES** - 红色，写入字段
- 🟣 **DECLARES** - 紫色，声明关系
- 🟠 **EXTENDS/IMPLEMENTS** - 橙黄色，继承关系
- 🟡 **EVOLVES_FROM** - 金色，演化关系

**演化状态标识**（通过边框颜色）：
- 🟢 **ADDED** - 绿色加粗边框
- 🔴 **DELETED** - 红色加粗边框
- 🟠 **MODIFIED** - 橙色加粗边框
- ⚪ **UNCHANGED** - 灰色细边框

📚 **详细文档**: [Neo4j 可视化样式配置指南](docs/NEO4J_STYLE_GUIDE.md)

## 配置说明

### 配置文件 (config.properties)

所有配置项都在 `config.properties` 文件中定义：

#### Neo4j 配置
- `neo4j.home`: Neo4j 安装目录
- `neo4j.uri`: Neo4j 连接地址
- `neo4j.username`: 用户名
- `neo4j.password`: 密码
- `neo4j.database`: 数据库名

#### 项目配置
- `project.path`: 待分析的项目路径（必填）
- `project.name`: 项目名称（可选，自动从路径提取）

#### Spoon 配置
- `spoon.autoImports`: 是否自动导入 (默认: true)
- `spoon.complianceLevel`: Java 版本 (默认: 11)
- `spoon.preserveFormatting`: 是否保留格式 (默认: false)

#### 导出配置
- `export.baseDir`: CSV 导出目录 (默认: ./csv_export)

#### 日志配置
- `log.level`: 日志级别 (DEBUG, INFO, WARN, ERROR，默认: INFO)
- `log.directory`: 日志目录 (默认: ./logs)

#### 演化配置 (EVOLUTION / MULTI_EVOLUTION)
- `evolution.commit`: 目标提交（自动与父提交比较）
- `evolution.useRefactoringMiner`: 是否启用 RefactoringMiner（默认: true）
- `evolution.historyWindow`: 多版本模式回溯的父提交数量（默认: 5）

#### 性能配置
- `performance.batchSize`: 批处理大小 (默认: 2000)
- `performance.maxRetries`: 最大重试次数 (默认: 3)

### 环境变量

也可以通过环境变量设置 Neo4j 路径：

```bash
export NEO4J_HOME=/path/to/neo4j
```

## 项目结构

```
TUGraph/
├── config.properties          # 配置文件
├── pom.xml                   # Maven 配置
├── ARCHITECTURE.md           # 架构文档
├── README.md                 # 本文件
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── github/
│                   ├── Main.java              # 主入口
│                   ├── config/                # 配置管理
│                   │   └── AppConfig.java
│                   ├── model/                 # 数据模型
│                   │   ├── KnowledgeGraph.java
│                   │   ├── Node.java
│                   │   ├── Edge.java
│                   │   ├── nodes/            # 节点类型
│                   │   └── edges/            # 关系类型
│                   ├── spoon/                # Spoon 分析引擎
│                   │   ├── ProjectAnalyzer.java
│                   │   ├── NodeExtractor.java
│                   │   ├── EdgeExtractor.java
│                   │   └── visitors/         # AST 访问者
│                   ├── neo4j/                # Neo4j 导入/导出
│                   │   ├── Neo4jServiceManager.java
│                   │   ├── Neo4jBulkImporter.java
│                   │   ├── Neo4jBulkCsvExporter.java
│                   │   └── Neo4jConfig.java
│                   ├── logging/              # 日志系统
│                   │   ├── GraphLogger.java
│                   │   └── LogLevel.java
│                   └── export/               # 导出工具
├── csv_export/               # CSV 导出目录
└── logs/                     # 日志目录
    └── application.log       # 应用日志
```

## 多版本演化模式

`MULTI_EVOLUTION` 模式会沿着第一父提交链回溯目标提交的历史版本，逐对执行演化分析并导出结果：

1. **收集时间线**：按 `evolution.historyWindow`（默认 5）回溯父提交，得到按时间顺序排列的提交列表。
2. **逐对分析**：对列表中每对相邻提交运行一次 `EvolutionAnalyzer`，生成包含演化信息的知识图谱。
3. **聚合导出**：将所有演化结果合并为一个时间线知识图谱，目录名形如 `<project>_timeline_<first>_<last>`。
4. **自动导入聚合结果**：流水线会对聚合后的 CSV 执行一次 Neo4j 离线导入，若失败可手动运行生成的 `import.sh`。

可根据项目大小调整 `evolution.historyWindow` 以平衡覆盖范围与运行时间；若关闭 `evolution.useRefactoringMiner`，则会跳过重构检测以缩短分析时间。

## 知识图谱模型

### 节点类型

- **PROJECT**: 项目节点
- **PACKAGE**: 包节点
- **FILE**: 文件节点
- **TYPE**: 类型节点（类、接口、枚举、注解）
- **METHOD**: 方法节点
- **FIELD**: 字段节点
- **PARAMETER**: 参数节点
- **ANNOTATION**: 注解节点

### 关系类型

- **DECLARES**: 声明关系
- **IN_PACKAGE**: 所属包关系
- **EXTENDS**: 继承关系
- **IMPLEMENTS**: 实现关系
- **OVERRIDES**: 重写关系
- **CALLS**: 方法调用关系
- **READS**: 读取字段关系
- **WRITES**: 写入字段关系
- **HAS_PARAM**: 参数关系
- **RETURN_TYPE**: 返回类型关系
- **THROWS**: 抛出异常关系
- **ANNOTATED_BY**: 注解关系
- **DECLARES_TYPE**: 文件声明类型关系
- **CONTAINS_FILE**: 项目包含文件关系
- **HAS_PACKAGE**: 项目包含包关系
- **TESTS**: 测试关系（方法测试方法）

## 常见问题

### Q: 导入时提示 NEO4J_HOME 不正确？

确保 Neo4j 路径正确设置，可以通过以下方式之一：
1. 在 `config.properties` 中设置 `neo4j.home`
2. 设置环境变量 `NEO4J_HOME`
3. 通过命令行参数传递

### Q: 导入失败如何手动导入？

如果自动导入失败，可以使用生成的导入脚本：

```bash
cd csv_export/<project-name>_<timestamp>
bash import.sh
```

### Q: 如何清空数据库重新导入？

批量导入会自动覆盖 `neo4j` 数据库。如果需要手动清空：

```cypher
// 删除所有节点和关系
MATCH (n) DETACH DELETE n
```

### Q: 支持哪些 Java 版本？

工具支持 Java 8-21 的项目分析，但运行工具本身需要 Java 11+。

## 开发指南

### 添加新的节点类型

1. 在 `model/nodes/` 下创建新的节点类，继承 `Node`
2. 在 `NodeExtractor.java` 中添加提取逻辑
3. 在 `Neo4jBulkCsvExporter.java` 中添加导出逻辑

### 添加新的关系类型

1. 在 `model/edges/` 下创建新的边类，继承 `Edge`
2. 在 `EdgeExtractor.java` 中添加提取逻辑
3. 在 `Neo4jBulkCsvExporter.java` 中添加导出逻辑

## 许可证

[请在此添加许可证信息]

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

[请在此添加联系方式]

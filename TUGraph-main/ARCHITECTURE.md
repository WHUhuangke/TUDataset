# Java 项目知识图谱构建系统

## 项目概述

这是一个基于 Spoon 的 Java 项目静态分析工具，用于构建项目的知识图谱。该知识图谱包含丰富的语义信息，特别针对 LLM 检索上下文和测试生成场景进行优化。

## 架构设计

### 核心思想：两遍遍历

1. **第一遍遍历（节点提取）**：扫描整个项目，提取所有实体并建立索引
2. **第二遍遍历（关系提取）**：基于已建立的索引，提取实体间的关系

### 目录结构

```
src/main/java/com/github/
├── model/                          # 图谱领域模型
│   ├── GraphElement.java          # 图元素基础接口
│   ├── Node.java                  # 节点抽象类（含语义信息）
│   ├── Edge.java                  # 边抽象类（含上下文）
│   ├── KnowledgeGraph.java        # 知识图谱容器
│   ├── nodes/                     # 节点实现
│   │   ├── ProjectNode.java       # 项目节点
│   │   ├── PackageNode.java       # 包节点
│   │   ├── FileNode.java          # 文件节点
│   │   ├── TypeNode.java          # 类型节点（类/接口/枚举/注解）
│   │   ├── MethodNode.java        # 方法节点
│   │   ├── FieldNode.java         # 字段节点
│   │   ├── ParameterNode.java     # 参数节点
│   │   └── AnnotationNode.java    # 注解节点
│   └── edges/                     # 边实现
│       ├── DeclaresEdge.java      # 声明关系
│       ├── InPackageEdge.java     # 所属包关系
│       ├── ExtendsEdge.java       # 继承关系
│       ├── ImplementsEdge.java    # 实现关系
│       ├── OverridesEdge.java     # 重写关系
│       ├── CallsEdge.java         # 调用关系
│       ├── ReadsEdge.java         # 读取字段关系
│       ├── WritesEdge.java        # 写入字段关系
│       ├── HasParamEdge.java      # 参数关系
│       ├── ReturnTypeEdge.java    # 返回类型关系
│       ├── ThrowsEdge.java        # 抛出异常关系
│       ├── AnnotatedByEdge.java   # 注解关系
│       ├── DeclaresTypeEdge.java  # 文件声明类型关系
│       ├── ContainsFileEdge.java  # 项目包含文件关系
│       └── HasPackageEdge.java    # 项目包含包关系
└── spoon/                         # Spoon 分析引擎
    ├── ProjectAnalyzer.java       # 主入口，协调整个分析流程
    ├── NodeExtractor.java         # 节点提取器（第一遍遍历）
    ├── EdgeExtractor.java         # 关系提取器（第二遍遍历）
    ├── index/
    │   └── NodeIndex.java         # 节点索引管理器
    ├── visitors/                  # Spoon 访问者（用于遍历 AST）
    │   ├── TypeVisitor.java       # 类型访问者
    │   ├── MethodVisitor.java     # 方法访问者
    │   ├── FieldVisitor.java      # 字段访问者
    │   ├── ParameterVisitor.java  # 参数访问者
    │   └── AnnotationVisitor.java # 注解访问者
    └── extractors/                # 信息提取器（深度分析）
        ├── CodeExtractor.java     # 代码提取器
        ├── JavadocExtractor.java  # 文档提取器
        ├── CallGraphExtractor.java # 调用图提取器
        └── DependencyExtractor.java# 依赖提取器
```

## 核心组件说明

### 1. NodeIndex（节点索引）

**作用**：
- 确保节点唯一性（防止重复创建）
- 提供 O(1) 时间复杂度的节点查找
- 统一管理所有节点的生命周期

**实现**：
- 使用多个 HashMap 分别存储不同类型的节点
- Key 为节点的唯一标识符（QualifiedName、Signature、Path 等）
- 提供 `add` 和 `get` 方法，支持快速插入和查找

### 2. Visitors（访问者）

**作用**：
- 遍历 Spoon 的 AST（抽象语法树）
- 识别特定类型的代码元素
- 提取基本信息并创建节点

**特点**：
- 继承 Spoon 的 `CtScanner`
- 重写 `visitXXX` 方法处理特定元素
- 专注于节点识别，不处理关系

**示例**：
- `TypeVisitor`：访问类、接口、枚举、注解
- `MethodVisitor`：访问方法和构造函数
- `FieldVisitor`：访问字段
- `ParameterVisitor`：访问参数

### 3. Extractors（提取器）

**作用**：
- 深度分析代码元素内部
- 提取复杂的关系和语义信息
- 基于已建立的索引创建边

**特点**：
- 需要完整的节点索引
- 处理跨元素的关系
- 提取上下文信息

**示例**：
- `CallGraphExtractor`：分析方法体，提取方法调用、字段读写
- `DependencyExtractor`：分析类型关系，提取继承、实现、覆盖
- `CodeExtractor`：提取源代码文本
- `JavadocExtractor`：提取文档注释

## 节点设计特点

每个节点包含：

1. **唯一标识**：确保全局唯一性
2. **源代码**：完整的代码文本
3. **文档注释**：Javadoc 内容
4. **语义摘要**：为 LLM 快速理解生成的简短描述
5. **属性映射**：灵活存储各种元数据

**示例：MethodNode**
```java
- signature: 方法签名（唯一标识）
- sourceCode: 完整方法代码
- documentation: Javadoc
- semanticSummary: "public String getName() - 返回用户名称"
- properties:
  - returnType: "String"
  - cyclomaticComplexity: 3
  - linesOfCode: 15
  - calledMethods: ["validateName", "logAccess"]
```

## 边设计特点

每条边包含：

1. **源节点 ID**：关系的起点
2. **目标节点 ID**：关系的终点
3. **上下文片段**：关系发生的代码片段
4. **描述信息**：关系的语义描述
5. **附加属性**：如行号、调用次数等

**示例：CallsEdge**
```java
- sourceId: "com.example.Service.processUser(User)"
- targetId: "com.example.UserValidator.validate(User)"
- contextSnippet: "validator.validate(user);"
- properties:
  - lineNumber: 42
  - callType: "virtual"
  - callCount: 1
```

## 使用方法

### 基本用法

```java
// 1. 创建分析器
ProjectAnalyzer analyzer = new ProjectAnalyzer("/path/to/project");

// 2. 执行分析（自动完成两遍遍历）
KnowledgeGraph kg = analyzer.analyze();

// 3. 使用知识图谱
// 获取项目摘要
String summary = kg.generateProjectSummary();

// 为特定类生成测试上下文
String testContext = kg.generateTestContext("com.example.UserService");

// 获取节点的上下文（包括邻居）
String context = kg.generateContext("com.example.UserService", 2);
```

### 高级用法

```java
// 查询特定类型的节点
List<Node> allMethods = kg.getNodesByType("METHOD");

// 查询特定类型的边
List<Edge> allCalls = kg.getEdgesByType("CALLS");

// 查找路径
List<List<Node>> paths = kg.findPaths(sourceId, targetId, maxDepth);

// 获取邻居节点
List<Node> neighbors = kg.getNeighbors(nodeId);
```

## 分析流程

```
项目路径
    ↓
初始化 Spoon MavenLauncher
    ↓
构建 AST 模型
    ↓
┌─────────────────────────┐
│  第一遍：节点提取         │
├─────────────────────────┤
│ 1. 创建 Project 节点     │
│ 2. 创建 Package 节点     │
│ 3. 创建 File 节点        │
│ 4. 使用 Visitors 遍历:   │
│    - TypeVisitor         │
│    - MethodVisitor       │
│    - FieldVisitor        │
│    - ParameterVisitor    │
│ 5. 所有节点注册到索引    │
└─────────────────────────┘
    ↓
NodeIndex（节点索引建立完成）
    ↓
┌─────────────────────────┐
│  第二遍：关系提取         │
├─────────────────────────┤
│ 1. 项目级关系:           │
│    - Project→Package     │
│    - Project→File        │
│ 2. 结构关系:             │
│    - File→Type           │
│    - Type→Package        │
│    - Type→Method/Field   │
│ 3. 继承关系:             │
│    - Extends             │
│    - Implements          │
│    - Overrides           │
│ 4. 调用关系:             │
│    - Calls               │
│    - Reads/Writes        │
│ 5. 类型关系:             │
│    - HasParam            │
│    - ReturnType          │
│    - Throws              │
└─────────────────────────┘
    ↓
KnowledgeGraph（知识图谱构建完成）
```

## 为 LLM 优化的特性

1. **丰富的语义信息**
   - 每个节点包含完整的源代码
   - 自动生成语义摘要
   - 保留文档注释

2. **上下文生成**
   - `toContextString()`: 生成节点的完整上下文
   - `generateContext()`: 生成节点及其邻居的上下文
   - `generateTestContext()`: 专门为测试生成优化的上下文

3. **灵活的查询**
   - 按类型查询节点和边
   - 路径查找
   - 邻居遍历

4. **代码片段保存**
   - 边包含关系发生的代码片段
   - 便于 LLM 理解具体的调用关系

## 扩展性

系统设计考虑了扩展性：

1. **添加新节点类型**：继承 `Node` 类
2. **添加新边类型**：继承 `Edge` 类
3. **添加新 Visitor**：继承 `CtScanner`
4. **添加新 Extractor**：创建新的提取器类

## 依赖

```xml
<dependency>
    <groupId>fr.inria.gforge.spoon</groupId>
    <artifactId>spoon-core</artifactId>
    <version>11.1.0</version>
</dependency>
```

## 注意事项

1. **内存占用**：大型项目可能产生大量节点，注意内存管理
2. **分析时间**：第一次分析需要构建完整的 AST，可能耗时较长
3. **跨项目依赖**：当前只分析项目内的代码，不包括外部库
4. **Java 版本**：配置为 Java 17，可根据项目调整

## 输出示例

```
=== Node Index Statistics ===
Projects: 1
Packages: 15
Files: 45
Types: 52
Methods: 234
Fields: 89
Parameters: 456
Annotations: 12
Total: 904

=== Edge Statistics ===
DECLARES: 323
IN_PACKAGE: 52
EXTENDS: 15
IMPLEMENTS: 23
CALLS: 567
READS: 234
WRITES: 189
HAS_PARAM: 456
...
Total Edges: 2145
```

## 未来优化

1. 支持增量分析（只分析变更部分）
2. 并行化处理提升性能
3. 支持导出到图数据库（Neo4j, TuGraph 等）
4. 添加更多语义分析（数据流、控制流等）

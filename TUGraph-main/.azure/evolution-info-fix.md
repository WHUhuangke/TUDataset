# 演化信息缺失问题修复报告

## 问题描述

**用户反馈**：演化分析得到的图谱中：
1. ❌ 没有演化相关的边（`REFACTORED_TO`）
2. ❌ 节点中没有版本等演化相关信息（`versionStatus`, `versions`, `firstVersion`, `lastVersion`）

## 根本原因分析

### 问题 1: 节点演化信息未导出 ✅ 已修复

**原因**：
演化信息字段存储在 `Node` 类的字段中，而不是在 `properties` Map 中：

```java
// Node 类中的字段（不在 properties Map 中）
private VersionStatus versionStatus;
private Set<String> versions = new HashSet<>();
private String firstVersion;
private String lastVersion;
```

CSV 导出器的 `collectAllPropertyKeys()` 方法只收集 `properties` Map 中的键，**不包括这些演化字段**。

**影响**：
- Neo4j 中的节点缺少 `versionStatus`, `versions`, `firstVersion`, `lastVersion` 属性
- 无法查询节点的演化状态（UNCHANGED/MODIFIED/ADDED/DELETED）
- 无法追踪节点的版本历史

### 问题 2: 演化边未创建 ⚠️ 待实现

**原因**：
`GraphMerger.createEvolutionEdgeFromRefactoring()` 方法中只有 TODO 注释，**没有实际的边创建逻辑**：

```java
private void createEvolutionEdgeFromRefactoring(...) {
    String type = refactoring.getType();
    
    // 根据重构类型创建相应的演化边
    // 这里简化处理，实际应该根据 leftSideLocations 和 rightSideLocations 找到对应节点
    
    logger.debug("检测到重构: " + type + " - " + refactoring.getDescription());
    
    // TODO: 在后续优化中，可以通过文件路径、类名、方法名等信息
    // 在 v1Graph 和 v2Graph 中查找对应的节点，然后创建演化边
}
```

**影响**：
- 图谱中没有 `REFACTORED_TO` 边
- 无法追踪重构操作（如 Rename Method, Extract Method 等）
- RefactoringMiner 检测到的 38 个重构信息没有被利用

---

## 修复方案

### 修复 1: 节点演化信息导出 ✅ 已完成

#### 修改文件
`/Users/mac/Desktop/TUGraph/src/main/java/com/github/neo4j/Neo4jBulkCsvExporter.java`

#### 修改内容

**1. 添加演化信息检测方法**：
```java
/**
 * 检查图谱是否包含演化信息
 * 通过检查节点是否有版本状态来判断
 */
private boolean checkIfEvolutionGraph(KnowledgeGraph graph) {
    for (var node : graph.getAllNodes()) {
        com.github.model.Node nodeObj = (com.github.model.Node) node;
        if (nodeObj.getVersionStatus() != null || !nodeObj.getVersions().isEmpty()) {
            return true;
        }
    }
    return false;
}
```

**2. 修改表头生成**（添加演化字段）：
```java
// 写入表头 - Neo4j import 格式
writer.write(":ID,:LABEL,name");

// 演化信息列（如果是演化分析的图谱）
boolean hasEvolutionInfo = checkIfEvolutionGraph(graph);
if (hasEvolutionInfo) {
    writer.write(",versionStatus:string,versions:string[],firstVersion:string,lastVersion:string");
}

// 动态属性列...
```

**3. 修改数据行生成**（输出演化字段值）：
```java
writer.write(String.format("%s,%s,%s", id, label, name));

// 演化信息列（如果是演化图谱）
if (hasEvolutionInfo) {
    // versionStatus
    String versionStatus = nodeObj.getVersionStatus() != null ? 
        nodeObj.getVersionStatus().name() : "";
    writer.write("," + escapeValue(versionStatus));
    
    // versions (数组格式)
    String versions = nodeObj.getVersions().isEmpty() ? "" : 
        String.join(";", nodeObj.getVersions());
    writer.write("," + escapeValue(versions));
    
    // firstVersion
    String firstVersion = nodeObj.getFirstVersion() != null ? 
        nodeObj.getFirstVersion() : "";
    writer.write("," + escapeValue(firstVersion));
    
    // lastVersion
    String lastVersion = nodeObj.getLastVersion() != null ? 
        nodeObj.getLastVersion() : "";
    writer.write("," + escapeValue(lastVersion));
}
```

#### 效果

修复后，CSV 文件的表头将包含演化字段：
```csv
:ID,:LABEL,name,versionStatus:string,versions:string[],firstVersion:string,lastVersion:string,...
```

数据行示例：
```csv
node123,METHOD,toString,UNCHANGED,"V1;V2",7835c1c,ea9e408,...
node456,METHOD,parse,MODIFIED,"V1;V2",7835c1c,ea9e408,...
node789,METHOD,validate,ADDED,"V2",,ea9e408,...
```

---

### 修复 2: 演化边创建 ⚠️ 待实现

#### 问题分析

创建演化边需要：
1. 解析 `RefactoringInfo` 中的 `leftSideLocations` 和 `rightSideLocations`
2. 根据文件路径、类名、方法签名等信息在图谱中查找对应的节点
3. 创建 `REFACTORED_TO` 边连接这些节点

#### 实现难点

1. **节点查找**：RefactoringInfo 中的位置信息（文件路径、行号）需要映射到 Node ID
2. **粒度匹配**：重构可能涉及方法、类、字段等不同粒度的节点
3. **批量处理**：一个重构可能涉及多个节点

#### 建议的实现方案

**方案 A：基于签名匹配（推荐）**

```java
private void createEvolutionEdgeFromRefactoring(
        RefactoringInfo refactoring,
        KnowledgeGraph mergedGraph,
        Map<String, String> v1ToMergedId,
        Map<String, String> v2ToMergedId) {
    
    // 1. 提取左侧（V1）和右侧（V2）的代码元素
    List<RefactoringInfo.CodeLocation> leftSide = refactoring.getLeftSideLocations();
    List<RefactoringInfo.CodeLocation> rightSide = refactoring.getRightSideLocations();
    
    if (leftSide.isEmpty() || rightSide.isEmpty()) {
        return; // 没有足够信息
    }
    
    // 2. 查找对应的节点
    for (CodeLocation left : leftSide) {
        for (CodeLocation right : rightSide) {
            Node leftNode = findNodeByLocation(mergedGraph, left, v1ToMergedId);
            Node rightNode = findNodeByLocation(mergedGraph, right, v2ToMergedId);
            
            if (leftNode != null && rightNode != null) {
                // 3. 创建演化边
                RefactoredToEdge edge = new RefactoredToEdge(
                    leftNode.getId(), 
                    rightNode.getId()
                );
                edge.setProperty("refactoringType", refactoring.getType());
                edge.setProperty("description", refactoring.getDescription());
                
                mergedGraph.addEdge(edge);
                evolutionEdgesCount++;
            }
        }
    }
}

private Node findNodeByLocation(
        KnowledgeGraph graph, 
        CodeLocation location,
        Map<String, String> versionToMergedId) {
    
    String codeElement = location.getCodeElement();
    String filePath = location.getFilePath();
    
    // 策略 1: 通过 signature 精确匹配（对于方法）
    for (Node node : graph.getAllNodes()) {
        if (node instanceof MethodNode) {
            String signature = (String) node.getProperty("signature");
            if (signature != null && signature.contains(codeElement)) {
                return node;
            }
        }
    }
    
    // 策略 2: 通过 qualifiedName 匹配（对于类、字段）
    // ...
    
    return null;
}
```

**方案 B：简化方案（快速实现）**

如果节点匹配太复杂，可以先创建一个简化版本：
- 只为 MODIFIED 节点创建自指向的 `REFACTORED_TO` 边
- 或者创建虚拟的 `RefactoringEvent` 节点，记录重构信息

---

## 验证方法

### 1. 检查 CSV 文件

```bash
cd csv_export/*/
head -1 nodes_bulk.csv  # 查看表头
head -5 nodes_bulk.csv | tail -4  # 查看数据样例
```

**预期表头**（包含演化字段）：
```
:ID,:LABEL,name,versionStatus:string,versions:string[],firstVersion:string,lastVersion:string,...
```

### 2. 查询 Neo4j

导入后，在 Neo4j Browser 中执行：

```cypher
// 检查演化状态分布
MATCH (n) 
WHERE n.versionStatus IS NOT NULL
RETURN n.versionStatus, count(*) 
ORDER BY count(*) DESC

// 查看 UNCHANGED 节点
MATCH (n {versionStatus: 'UNCHANGED'}) 
RETURN n.name, n.versions, n.firstVersion, n.lastVersion 
LIMIT 10

// 查看 MODIFIED 节点
MATCH (n {versionStatus: 'MODIFIED'}) 
RETURN n.name, n.versions 
LIMIT 10

// 查看 ADDED 节点
MATCH (n {versionStatus: 'ADDED'}) 
RETURN n.name, n.lastVersion 
LIMIT 10

// 查看 DELETED 节点
MATCH (n {versionStatus: 'DELETED'}) 
RETURN n.name, n.firstVersion 
LIMIT 10

// 检查演化边（如果已实现）
MATCH ()-[r:REFACTORED_TO]->() 
RETURN type(r), r.refactoringType, count(*) 
ORDER BY count(*) DESC
```

---

## 当前状态

### ✅ 已完成
1. **节点演化信息导出**
   - 添加 `versionStatus` 字段（UNCHANGED/MODIFIED/ADDED/DELETED）
   - 添加 `versions` 数组字段（V1, V2 等）
   - 添加 `firstVersion` 字段（首次出现的 commit）
   - 添加 `lastVersion` 字段（最后出现的 commit）
   - 自动检测图谱是否包含演化信息

### ⚠️ 待完成
2. **演化边创建**
   - 实现 `createEvolutionEdgeFromRefactoring()` 方法
   - 根据 RefactoringInfo 查找对应节点
   - 创建 `REFACTORED_TO` 边
   - 添加重构类型和描述信息

---

## 下一步计划

### 优先级 1：验证节点演化信息
```bash
mvn clean package -DskipTests
java -jar target/TUGraph-jar-with-dependencies.jar
# 检查 CSV 和 Neo4j 中的演化字段
```

### 优先级 2：实现演化边创建
- 设计节点查找策略
- 实现 `findNodeByLocation()` 方法
- 创建 `RefactoredToEdge` 类（如果不存在）
- 测试验证

### 优先级 3：完善文档
- 更新 README 说明演化字段
- 添加查询示例
- 完善 ARCHITECTURE 文档

---

**修复状态**：
- 节点演化信息：✅ 已修复
- 演化边创建：⚠️ 待实现

**预计影响**：
- 修复后节点将包含完整的演化信息
- 可以查询和分析代码演化状态
- 演化边需要后续实现才能追踪重构关系

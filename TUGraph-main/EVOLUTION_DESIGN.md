# 版本演化知识图谱设计方案

## 📋 需求总结

基于讨论,明确以下核心需求:

1. **版本粒度**: Git commit 级别
2. **节点策略**: 保留两个版本的节点,使用细粒度演化边连接(如 RENAMED_TO, MOVED_TO, UPDATED_TO等)
3. **版本管理**: 版本信息作为节点属性,不创建独立的版本节点
4. **存储策略**: 只存储差异,未变更的节点保持唯一性
5. **核心用例**: 影响分析 - 版本变更影响的函数追踪,用于测试演化

---

## 🏗️ 核心架构设计

### 1. 图谱模型设计

#### 1.1 节点版本属性扩展

为所有现有节点类型添加版本相关属性:

```java
public abstract class Node {
    // 现有字段...
    
    // 新增版本相关属性
    protected Set<String> versions;        // 节点存在的版本集合,如 ["commit-abc123", "commit-def456"]
    protected String firstVersion;         // 首次出现的版本
    protected String lastVersion;          // 最后出现的版本
    protected VersionStatus versionStatus; // UNCHANGED, MODIFIED, ADDED, DELETED
    
    public enum VersionStatus {
        UNCHANGED,  // 两版本间完全相同
        MODIFIED,   // 有修改
        ADDED,      // 新增(仅在v2)
        DELETED     // 删除(仅在v1)
    }
}
```

**版本属性详解**:
- `versions`: 节点存在于哪些版本中
  - 未变化的节点: `["v1", "v2"]` 
  - v1中删除的: `["v1"]`
  - v2中新增的: `["v2"]`
- `versionStatus`: 快速标识节点状态,用于影响分析查询优化

#### 1.2 演化边类型设计

创建细粒度的演化边,所有演化边都继承自 `EvolutionEdge`:

```java
/**
 * 演化边基类 - 表示节点在版本间的演化关系
 */
public abstract class EvolutionEdge extends Edge {
    protected String fromVersion;      // 源版本(如 "commit-abc123")
    protected String toVersion;        // 目标版本
    protected RefactoringType refactoringType;  // 重构类型
    protected Map<String, Object> changeDetails; // 变更详情
    
    public EvolutionEdge(String sourceId, String targetId, 
                         String fromVersion, String toVersion) {
        super(sourceId, targetId);
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.changeDetails = new HashMap<>();
    }
    
    @Override
    public String getEdgeType() {
        return "EVOLUTION";
    }
    
    public abstract RefactoringType getRefactoringType();
}
```

**具体演化边类型**:

1. **RenamedToEdge** - 重命名
```java
public class RenamedToEdge extends EvolutionEdge {
    public RenamedToEdge(String oldNodeId, String newNodeId, 
                         String fromVersion, String toVersion,
                         String oldName, String newName) {
        super(oldNodeId, newNodeId, fromVersion, toVersion);
        changeDetails.put("oldName", oldName);
        changeDetails.put("newName", newName);
    }
    
    @Override
    public RefactoringType getRefactoringType() {
        return RefactoringType.RENAME;
    }
}
```

2. **MovedToEdge** - 移动(类/方法移动到其他位置)
```java
public class MovedToEdge extends EvolutionEdge {
    public MovedToEdge(String oldNodeId, String newNodeId,
                       String fromVersion, String toVersion,
                       String oldLocation, String newLocation) {
        super(oldNodeId, newNodeId, fromVersion, toVersion);
        changeDetails.put("oldLocation", oldLocation);
        changeDetails.put("newLocation", newLocation);
    }
    
    @Override
    public RefactoringType getRefactoringType() {
        return RefactoringType.MOVE;
    }
}
```

3. **SignatureChangedEdge** - 方法签名变更
```java
public class SignatureChangedEdge extends EvolutionEdge {
    public SignatureChangedEdge(String oldMethodId, String newMethodId,
                                 String fromVersion, String toVersion,
                                 String oldSignature, String newSignature) {
        super(oldMethodId, newMethodId, fromVersion, toVersion);
        changeDetails.put("oldSignature", oldSignature);
        changeDetails.put("newSignature", newSignature);
        changeDetails.put("parameterChanges", extractParamChanges(oldSig, newSig));
        changeDetails.put("returnTypeChanged", extractReturnTypeChange(oldSig, newSig));
    }
    
    @Override
    public RefactoringType getRefactoringType() {
        return RefactoringType.CHANGE_SIGNATURE;
    }
}
```

4. **BodyModifiedEdge** - 方法体修改
```java
public class BodyModifiedEdge extends EvolutionEdge {
    public BodyModifiedEdge(String oldMethodId, String newMethodId,
                            String fromVersion, String toVersion) {
        super(oldMethodId, newMethodId, fromVersion, toVersion);
        // 记录代码变更
        changeDetails.put("linesAdded", 0);
        changeDetails.put("linesDeleted", 0);
        changeDetails.put("modifiedStatements", new ArrayList<>());
    }
    
    @Override
    public RefactoringType getRefactoringType() {
        return RefactoringType.MODIFY_BODY;
    }
    
    public void setCodeDiff(int linesAdded, int linesDeleted, 
                           List<String> modifiedStatements) {
        changeDetails.put("linesAdded", linesAdded);
        changeDetails.put("linesDeleted", linesDeleted);
        changeDetails.put("modifiedStatements", modifiedStatements);
    }
}
```

5. **ExtractedFromEdge** - 提取方法
```java
public class ExtractedFromEdge extends EvolutionEdge {
    public ExtractedFromEdge(String newMethodId, String sourceMethodId,
                             String fromVersion, String toVersion) {
        super(newMethodId, sourceMethodId, fromVersion, toVersion);
        changeDetails.put("extractedStatements", new ArrayList<>());
    }
    
    @Override
    public RefactoringType getRefactoringType() {
        return RefactoringType.EXTRACT_METHOD;
    }
}
```

6. **InlinedIntoEdge** - 内联方法
```java
public class InlinedIntoEdge extends EvolutionEdge {
    public InlinedIntoEdge(String oldMethodId, String targetMethodId,
                           String fromVersion, String toVersion) {
        super(oldMethodId, targetMethodId, fromVersion, toVersion);
    }
    
    @Override
    public RefactoringType getRefactoringType() {
        return RefactoringType.INLINE_METHOD;
    }
}
```

7. **DeletedEdge** - 删除(单向边,target为null或特殊标记)
```java
public class DeletedEdge extends EvolutionEdge {
    public DeletedEdge(String deletedNodeId, String version) {
        super(deletedNodeId, "DELETED", version, null);
        changeDetails.put("deletionReason", ""); // 可能从commit message提取
    }
    
    @Override
    public RefactoringType getRefactoringType() {
        return RefactoringType.DELETE;
    }
}
```

#### 1.3 RefactoringType 枚举

```java
public enum RefactoringType {
    // 基础操作
    RENAME("Rename"),
    MOVE("Move"),
    DELETE("Delete"),
    ADD("Add"),
    
    // 方法级重构
    EXTRACT_METHOD("Extract Method"),
    INLINE_METHOD("Inline Method"),
    CHANGE_SIGNATURE("Change Method Signature"),
    MODIFY_BODY("Modify Method Body"),
    SPLIT_METHOD("Split Method"),
    MERGE_METHOD("Merge Method"),
    
    // 类级重构
    EXTRACT_CLASS("Extract Class"),
    EXTRACT_INTERFACE("Extract Interface"),
    MOVE_CLASS("Move Class"),
    RENAME_CLASS("Rename Class"),
    EXTRACT_SUPERCLASS("Extract Superclass"),
    
    // 字段级重构
    MOVE_FIELD("Move Field"),
    RENAME_FIELD("Rename Field"),
    CHANGE_FIELD_TYPE("Change Field Type"),
    
    // 其他
    UNCHANGED("Unchanged");
    
    private final String displayName;
    
    RefactoringType(String displayName) {
        this.displayName = displayName;
    }
}
```

---

### 2. RefactoringMiner 集成层

#### 2.1 EvolutionAnalyzer - 核心协调器

```java
/**
 * 演化分析器
 * 职责:
 * 1. 调用 RefactoringMiner 检测两个 commit 间的重构
 * 2. 分别构建两个版本的知识图谱
 * 3. 执行图谱合并
 * 4. 生成演化知识图谱
 */
public class EvolutionAnalyzer {
    
    private final String repoPath;
    private final GitService gitService;
    private final GraphLogger logger = GraphLogger.getInstance();
    
    public EvolutionAnalyzer(String repoPath) {
        this.repoPath = repoPath;
        this.gitService = new GitServiceImpl();
    }
    
    /**
     * 分析两个版本之间的演化
     * @param commitV1 旧版本 commit hash
     * @param commitV2 新版本 commit hash
     * @return 合并后的演化知识图谱
     */
    public EvolutionResult analyzeEvolution(String commitV1, String commitV2) 
            throws Exception {
        
        logger.info("========================================");
        logger.info("开始版本演化分析");
        logger.info("========================================");
        logger.info("仓库路径: " + repoPath);
        logger.info("版本 1: " + commitV1);
        logger.info("版本 2: " + commitV2);
        
        // 阶段 1: 使用 RefactoringMiner 检测重构
        logger.startPhase("检测重构操作");
        List<Refactoring> refactorings = detectRefactorings(commitV1, commitV2);
        logger.info(String.format("✓ 检测到 %d 个重构操作", refactorings.size()));
        logger.endPhase();
        
        // 阶段 2: 构建 V1 版本知识图谱
        logger.startPhase("构建版本1知识图谱");
        KnowledgeGraph v1Graph = buildGraphAtCommit(commitV1);
        logger.info(String.format("✓ V1: %d 节点, %d 边", 
            v1Graph.getAllNodes().size(), v1Graph.getAllEdges().size()));
        logger.endPhase();
        
        // 阶段 3: 构建 V2 版本知识图谱
        logger.startPhase("构建版本2知识图谱");
        KnowledgeGraph v2Graph = buildGraphAtCommit(commitV2);
        logger.info(String.format("✓ V2: %d 节点, %d 边", 
            v2Graph.getAllNodes().size(), v2Graph.getAllEdges().size()));
        logger.endPhase();
        
        // 阶段 4: 合并图谱
        logger.startPhase("合并演化图谱");
        GraphMerger merger = new GraphMerger(commitV1, commitV2);
        KnowledgeGraph evolutionGraph = merger.merge(v1Graph, v2Graph, refactorings);
        logger.info(String.format("✓ 合并后: %d 节点, %d 边", 
            evolutionGraph.getAllNodes().size(), evolutionGraph.getAllEdges().size()));
        logger.endPhase();
        
        // 阶段 5: 构建影响分析索引
        logger.startPhase("构建影响分析索引");
        ImpactAnalyzer impactAnalyzer = new ImpactAnalyzer(evolutionGraph);
        ImpactIndex impactIndex = impactAnalyzer.buildImpactIndex();
        logger.endPhase();
        
        return new EvolutionResult(evolutionGraph, refactorings, impactIndex);
    }
    
    /**
     * 使用 RefactoringMiner 检测重构
     */
    private List<Refactoring> detectRefactorings(String commitV1, String commitV2) 
            throws Exception {
        return gitService.detectRefactorings(repoPath, commitV1, commitV2);
    }
    
    /**
     * 检出特定 commit 并构建知识图谱
     */
    private KnowledgeGraph buildGraphAtCommit(String commit) throws Exception {
        // 创建临时目录
        File tempDir = Files.createTempDirectory("tugraph_version_").toFile();
        
        try {
            // 检出指定版本到临时目录
            gitService.checkout(repoPath, commit, tempDir.getAbsolutePath());
            
            // 使用现有的 ProjectAnalyzer 构建图谱
            ProjectAnalyzer analyzer = new ProjectAnalyzer(tempDir.getAbsolutePath());
            KnowledgeGraph graph = analyzer.analyze();
            
            // 为所有节点标记版本
            tagNodesWithVersion(graph, commit);
            
            return graph;
            
        } finally {
            // 清理临时目录
            FileUtils.deleteDirectory(tempDir);
        }
    }
    
    /**
     * 为图谱中的所有节点添加版本标签
     */
    private void tagNodesWithVersion(KnowledgeGraph graph, String version) {
        for (Node node : graph.getAllNodes()) {
            Set<String> versions = new HashSet<>();
            versions.add(version);
            node.setProperty("versions", versions);
            node.setProperty("firstVersion", version);
            node.setProperty("lastVersion", version);
        }
    }
}
```

#### 2.2 GitService - Git 操作封装

```java
/**
 * Git 服务接口
 */
public interface GitService {
    
    /**
     * 检测两个 commit 间的重构操作
     */
    List<Refactoring> detectRefactorings(String repoPath, 
                                         String commitV1, 
                                         String commitV2) throws Exception;
    
    /**
     * 检出指定 commit 到目标目录
     */
    void checkout(String repoPath, String commit, String targetDir) throws Exception;
    
    /**
     * 获取 commit 信息
     */
    CommitInfo getCommitInfo(String repoPath, String commit) throws Exception;
}

/**
 * GitService 实现 - 使用 RefactoringMiner
 */
public class GitServiceImpl implements GitService {
    
    @Override
    public List<Refactoring> detectRefactorings(String repoPath, 
                                                 String commitV1, 
                                                 String commitV2) throws Exception {
        GitService gitService = new GitServiceImpl();
        List<Refactoring> refactorings = new ArrayList<>();
        
        // 使用 RefactoringMiner API
        gitService.detectBetweenCommits(
            repoPath, 
            commitV1, 
            commitV2,
            new RefactoringHandler() {
                @Override
                public void handle(String commitId, List<Refactoring> refs) {
                    refactorings.addAll(refs);
                }
            }
        );
        
        return refactorings;
    }
    
    @Override
    public void checkout(String repoPath, String commit, String targetDir) 
            throws Exception {
        // 使用 JGit 检出指定版本
        try (Git git = Git.open(new File(repoPath))) {
            git.checkout()
               .setName(commit)
               .setStartPoint(commit)
               .call();
               
            // 复制文件到目标目录
            FileUtils.copyDirectory(new File(repoPath), new File(targetDir));
        }
    }
}
```

---

### 3. 图谱合并算法 - GraphMerger

这是整个设计的**核心算法**,负责智能合并两个版本的图谱:

```java
/**
 * 图谱合并器
 * 核心职责:
 * 1. 节点匹配与去重 - 未变化的节点保持唯一
 * 2. 创建演化边 - 根据 RefactoringMiner 结果和代码对比
 * 3. 处理新增/删除节点
 * 4. 保持原有边关系的完整性
 */
public class GraphMerger {
    
    private final String v1Version;
    private final String v2Version;
    private final GraphLogger logger = GraphLogger.getInstance();
    
    public GraphMerger(String v1Version, String v2Version) {
        this.v1Version = v1Version;
        this.v2Version = v2Version;
    }
    
    /**
     * 合并两个版本的知识图谱
     */
    public KnowledgeGraph merge(KnowledgeGraph v1Graph, 
                                KnowledgeGraph v2Graph,
                                List<Refactoring> refactorings) {
        
        KnowledgeGraph mergedGraph = new KnowledgeGraph();
        
        // 步骤 1: 构建节点映射关系
        NodeMapping nodeMapping = buildNodeMapping(v1Graph, v2Graph, refactorings);
        
        // 步骤 2: 处理节点
        processNodes(mergedGraph, v1Graph, v2Graph, nodeMapping);
        
        // 步骤 3: 处理边(保留原有关系边)
        processEdges(mergedGraph, v1Graph, v2Graph, nodeMapping);
        
        // 步骤 4: 创建演化边
        createEvolutionEdges(mergedGraph, nodeMapping, refactorings);
        
        // 步骤 5: 生成统计报告
        generateMergeReport(mergedGraph, nodeMapping);
        
        return mergedGraph;
    }
    
    /**
     * 步骤1: 构建节点映射关系
     * 
     * 映射类型:
     * - IDENTICAL: V1和V2中完全相同的节点
     * - RENAMED: 重命名的节点
     * - MOVED: 移动的节点
     * - MODIFIED: 内容修改的节点
     * - ADDED: V2新增的节点
     * - DELETED: V1中删除的节点
     */
    private NodeMapping buildNodeMapping(KnowledgeGraph v1Graph,
                                         KnowledgeGraph v2Graph,
                                         List<Refactoring> refactorings) {
        
        NodeMapping mapping = new NodeMapping();
        
        // 1. 先根据 RefactoringMiner 结果建立明确的映射
        buildRefactoringBasedMapping(mapping, refactorings);
        
        // 2. 对未映射的节点进行相似度匹配
        buildSimilarityBasedMapping(mapping, v1Graph, v2Graph);
        
        // 3. 识别完全相同的节点
        identifyIdenticalNodes(mapping, v1Graph, v2Graph);
        
        // 4. 标记新增和删除的节点
        markAddedAndDeletedNodes(mapping, v1Graph, v2Graph);
        
        return mapping;
    }
    
    /**
     * 基于 RefactoringMiner 结果建立映射
     */
    private void buildRefactoringBasedMapping(NodeMapping mapping,
                                              List<Refactoring> refactorings) {
        for (Refactoring ref : refactorings) {
            switch (ref.getRefactoringType()) {
                case RENAME_METHOD:
                    handleRenameMethod(mapping, (RenameOperationRefactoring) ref);
                    break;
                case MOVE_METHOD:
                    handleMoveMethod(mapping, (MoveOperationRefactoring) ref);
                    break;
                case CHANGE_METHOD_SIGNATURE:
                    handleChangeSignature(mapping, (ChangeMethodSignatureRefactoring) ref);
                    break;
                case EXTRACT_METHOD:
                    handleExtractMethod(mapping, (ExtractOperationRefactoring) ref);
                    break;
                case INLINE_METHOD:
                    handleInlineMethod(mapping, (InlineOperationRefactoring) ref);
                    break;
                case MOVE_CLASS:
                    handleMoveClass(mapping, (MoveClassRefactoring) ref);
                    break;
                case RENAME_CLASS:
                    handleRenameClass(mapping, (RenameClassRefactoring) ref);
                    break;
                // ... 处理其他重构类型
            }
        }
    }
    
    /**
     * 处理方法重命名
     */
    private void handleRenameMethod(NodeMapping mapping, 
                                    RenameOperationRefactoring ref) {
        // 获取重构前后的方法签名
        String v1Signature = generateMethodSignature(ref.getOriginalOperation());
        String v2Signature = generateMethodSignature(ref.getRenamedOperation());
        
        // 建立映射关系
        mapping.addMapping(v1Signature, v2Signature, MappingType.RENAMED);
        mapping.addRefactoring(v1Signature, ref);
    }
    
    /**
     * 基于代码相似度的节点匹配
     * 用于 RefactoringMiner 未检测到的变更
     */
    private void buildSimilarityBasedMapping(NodeMapping mapping,
                                            KnowledgeGraph v1Graph,
                                            KnowledgeGraph v2Graph) {
        
        // 获取未映射的方法节点
        List<MethodNode> v1UnmappedMethods = getUnmappedMethods(v1Graph, mapping);
        List<MethodNode> v2UnmappedMethods = getUnmappedMethods(v2Graph, mapping);
        
        // 计算相似度矩阵
        for (MethodNode v1Method : v1UnmappedMethods) {
            MethodNode bestMatch = null;
            double bestSimilarity = 0.0;
            
            for (MethodNode v2Method : v2UnmappedMethods) {
                double similarity = calculateSimilarity(v1Method, v2Method);
                if (similarity > bestSimilarity && similarity > 0.8) { // 阈值
                    bestSimilarity = similarity;
                    bestMatch = v2Method;
                }
            }
            
            if (bestMatch != null) {
                mapping.addMapping(
                    v1Method.getId(), 
                    bestMatch.getId(), 
                    MappingType.MODIFIED
                );
            }
        }
    }
    
    /**
     * 计算两个方法节点的相似度
     * 综合考虑: 名称、签名、代码结构、调用关系等
     */
    private double calculateSimilarity(MethodNode m1, MethodNode m2) {
        double nameSim = calculateNameSimilarity(m1, m2);          // 30%
        double signatureSim = calculateSignatureSimilarity(m1, m2); // 20%
        double codeSim = calculateCodeSimilarity(m1, m2);           // 30%
        double callSim = calculateCallGraphSimilarity(m1, m2);      // 20%
        
        return 0.3 * nameSim + 0.2 * signatureSim + 
               0.3 * codeSim + 0.2 * callSim;
    }
    
    /**
     * 识别完全相同的节点
     */
    private void identifyIdenticalNodes(NodeMapping mapping,
                                       KnowledgeGraph v1Graph,
                                       KnowledgeGraph v2Graph) {
        
        // 遍历 V1 的所有节点
        for (Node v1Node : v1Graph.getAllNodes()) {
            // 跳过已映射的节点
            if (mapping.hasMappingForV1(v1Node.getId())) {
                continue;
            }
            
            // 尝试在 V2 中找到相同 ID 的节点
            Node v2Node = v2Graph.getNode(v1Node.getId());
            
            if (v2Node != null && areNodesIdentical(v1Node, v2Node)) {
                // 完全相同,添加映射
                mapping.addMapping(
                    v1Node.getId(), 
                    v2Node.getId(), 
                    MappingType.IDENTICAL
                );
            }
        }
    }
    
    /**
     * 判断两个节点是否完全相同
     */
    private boolean areNodesIdentical(Node n1, Node n2) {
        // 1. 类型必须相同
        if (!n1.getNodeType().equals(n2.getNodeType())) {
            return false;
        }
        
        // 2. 源代码必须相同
        if (!Objects.equals(n1.getSourceCode(), n2.getSourceCode())) {
            return false;
        }
        
        // 3. 关键属性必须相同(根据节点类型检查)
        if (n1 instanceof MethodNode) {
            MethodNode m1 = (MethodNode) n1;
            MethodNode m2 = (MethodNode) n2;
            return Objects.equals(m1.getSignature(), m2.getSignature());
        }
        
        // 其他类型的节点比较逻辑...
        
        return true;
    }
    
    /**
     * 步骤2: 处理节点
     */
    private void processNodes(KnowledgeGraph mergedGraph,
                             KnowledgeGraph v1Graph,
                             KnowledgeGraph v2Graph,
                             NodeMapping mapping) {
        
        // 处理映射关系
        for (NodeMappingEntry entry : mapping.getAllMappings()) {
            String v1NodeId = entry.getV1NodeId();
            String v2NodeId = entry.getV2NodeId();
            MappingType mappingType = entry.getMappingType();
            
            Node v1Node = v1Graph.getNode(v1NodeId);
            Node v2Node = v2Graph.getNode(v2NodeId);
            
            switch (mappingType) {
                case IDENTICAL:
                    // 完全相同,保持唯一节点
                    addSharedNode(mergedGraph, v1Node, v2Node);
                    break;
                    
                case RENAMED:
                case MOVED:
                case MODIFIED:
                    // 有变化,保留两个版本
                    addVersionedNodes(mergedGraph, v1Node, v2Node);
                    break;
            }
        }
        
        // 处理新增节点(只在 V2)
        for (String addedNodeId : mapping.getAddedNodes()) {
            Node v2Node = v2Graph.getNode(addedNodeId);
            addNewNode(mergedGraph, v2Node);
        }
        
        // 处理删除节点(只在 V1)
        for (String deletedNodeId : mapping.getDeletedNodes()) {
            Node v1Node = v1Graph.getNode(deletedNodeId);
            addDeletedNode(mergedGraph, v1Node);
        }
    }
    
    /**
     * 添加共享节点(两版本完全相同)
     */
    private void addSharedNode(KnowledgeGraph graph, Node v1Node, Node v2Node) {
        // 使用 V2 的节点(更新)
        Node sharedNode = v2Node;
        
        // 设置版本信息
        Set<String> versions = new HashSet<>();
        versions.add(v1Version);
        versions.add(v2Version);
        sharedNode.setProperty("versions", versions);
        sharedNode.setProperty("versionStatus", VersionStatus.UNCHANGED);
        sharedNode.setProperty("firstVersion", v1Version);
        sharedNode.setProperty("lastVersion", v2Version);
        
        graph.addNode(sharedNode);
    }
    
    /**
     * 添加版本化节点(有变化,保留两个版本)
     */
    private void addVersionedNodes(KnowledgeGraph graph, Node v1Node, Node v2Node) {
        // 添加 V1 节点
        Set<String> v1Versions = new HashSet<>();
        v1Versions.add(v1Version);
        v1Node.setProperty("versions", v1Versions);
        v1Node.setProperty("versionStatus", VersionStatus.MODIFIED);
        v1Node.setProperty("isOldVersion", true);
        graph.addNode(v1Node);
        
        // 添加 V2 节点
        Set<String> v2Versions = new HashSet<>();
        v2Versions.add(v2Version);
        v2Node.setProperty("versions", v2Versions);
        v2Node.setProperty("versionStatus", VersionStatus.MODIFIED);
        v2Node.setProperty("isNewVersion", true);
        graph.addNode(v2Node);
    }
    
    /**
     * 步骤3: 处理边(保留原有关系边)
     */
    private void processEdges(KnowledgeGraph mergedGraph,
                             KnowledgeGraph v1Graph,
                             KnowledgeGraph v2Graph,
                             NodeMapping mapping) {
        
        // 处理 V1 的边
        for (Edge v1Edge : v1Graph.getAllEdges()) {
            String v1SourceId = v1Edge.getSourceId();
            String v1TargetId = v1Edge.getTargetId();
            
            // 检查源节点和目标节点是否在合并图中
            if (mergedGraph.getNode(v1SourceId) != null && 
                mergedGraph.getNode(v1TargetId) != null) {
                
                // 添加边并标记版本
                Edge copiedEdge = copyEdge(v1Edge);
                copiedEdge.setProperty("version", v1Version);
                mergedGraph.addEdge(copiedEdge);
            }
        }
        
        // 处理 V2 的边
        for (Edge v2Edge : v2Graph.getAllEdges()) {
            String v2SourceId = v2Edge.getSourceId();
            String v2TargetId = v2Edge.getTargetId();
            
            // 检查是否已经添加了相同的边(对于 IDENTICAL 节点)
            if (!isDuplicateEdge(mergedGraph, v2Edge)) {
                if (mergedGraph.getNode(v2SourceId) != null && 
                    mergedGraph.getNode(v2TargetId) != null) {
                    
                    Edge copiedEdge = copyEdge(v2Edge);
                    copiedEdge.setProperty("version", v2Version);
                    mergedGraph.addEdge(copiedEdge);
                }
            }
        }
    }
    
    /**
     * 步骤4: 创建演化边
     */
    private void createEvolutionEdges(KnowledgeGraph mergedGraph,
                                     NodeMapping mapping,
                                     List<Refactoring> refactorings) {
        
        // 根据映射关系创建演化边
        for (NodeMappingEntry entry : mapping.getAllMappings()) {
            MappingType type = entry.getMappingType();
            
            if (type == MappingType.IDENTICAL) {
                continue; // 完全相同的节点不需要演化边
            }
            
            String v1NodeId = entry.getV1NodeId();
            String v2NodeId = entry.getV2NodeId();
            Refactoring refactoring = entry.getRefactoring();
            
            // 根据映射类型创建相应的演化边
            EvolutionEdge evolutionEdge = createEvolutionEdge(
                v1NodeId, v2NodeId, type, refactoring
            );
            
            mergedGraph.addEdge(evolutionEdge);
        }
        
        // 为删除的节点创建 DeletedEdge
        for (String deletedNodeId : mapping.getDeletedNodes()) {
            DeletedEdge deletedEdge = new DeletedEdge(deletedNodeId, v1Version);
            mergedGraph.addEdge(deletedEdge);
        }
    }
    
    /**
     * 根据映射类型和重构信息创建演化边
     */
    private EvolutionEdge createEvolutionEdge(String v1NodeId, 
                                             String v2NodeId,
                                             MappingType type,
                                             Refactoring refactoring) {
        
        if (refactoring != null) {
            // 根据 RefactoringMiner 检测的重构类型创建边
            return createEdgeFromRefactoring(v1NodeId, v2NodeId, refactoring);
        }
        
        // 根据映射类型创建边
        switch (type) {
            case RENAMED:
                return new RenamedToEdge(v1NodeId, v2NodeId, 
                    v1Version, v2Version, 
                    extractOldName(v1NodeId), extractNewName(v2NodeId));
            case MOVED:
                return new MovedToEdge(v1NodeId, v2NodeId,
                    v1Version, v2Version,
                    extractOldLocation(v1NodeId), extractNewLocation(v2NodeId));
            case MODIFIED:
                return new BodyModifiedEdge(v1NodeId, v2NodeId,
                    v1Version, v2Version);
            default:
                throw new IllegalArgumentException("Unknown mapping type: " + type);
        }
    }
}

/**
 * 节点映射数据结构
 */
class NodeMapping {
    private Map<String, NodeMappingEntry> v1ToV2Map = new HashMap<>();
    private Map<String, NodeMappingEntry> v2ToV1Map = new HashMap<>();
    private Set<String> addedNodes = new HashSet<>();
    private Set<String> deletedNodes = new HashSet<>();
    
    public void addMapping(String v1NodeId, String v2NodeId, MappingType type) {
        NodeMappingEntry entry = new NodeMappingEntry(v1NodeId, v2NodeId, type);
        v1ToV2Map.put(v1NodeId, entry);
        v2ToV1Map.put(v2NodeId, entry);
    }
    
    public void addRefactoring(String v1NodeId, Refactoring refactoring) {
        NodeMappingEntry entry = v1ToV2Map.get(v1NodeId);
        if (entry != null) {
            entry.setRefactoring(refactoring);
        }
    }
    
    // ... 其他方法
}

enum MappingType {
    IDENTICAL,   // 完全相同
    RENAMED,     // 重命名
    MOVED,       // 移动
    MODIFIED     // 修改
}
```

---

### 4. 影响分析模块

这是你最关心的核心功能:

```java
/**
 * 影响分析器
 * 用于测试演化场景:
 * - 找出被修改的方法
 * - 分析这些修改影响了哪些调用者
 * - 追踪影响链
 */
public class ImpactAnalyzer {
    
    private final KnowledgeGraph evolutionGraph;
    private final GraphLogger logger = GraphLogger.getInstance();
    
    public ImpactAnalyzer(KnowledgeGraph evolutionGraph) {
        this.evolutionGraph = evolutionGraph;
    }
    
    /**
     * 构建影响分析索引
     */
    public ImpactIndex buildImpactIndex() {
        ImpactIndex index = new ImpactIndex();
        
        // 1. 识别所有变更的方法
        List<MethodNode> modifiedMethods = findModifiedMethods();
        index.setModifiedMethods(modifiedMethods);
        
        // 2. 对每个变更方法,找出其影响范围
        for (MethodNode modifiedMethod : modifiedMethods) {
            ImpactChain impactChain = analyzeImpact(modifiedMethod);
            index.addImpactChain(modifiedMethod.getId(), impactChain);
        }
        
        return index;
    }
    
    /**
     * 找出所有被修改的方法
     */
    private List<MethodNode> findModifiedMethods() {
        List<MethodNode> modifiedMethods = new ArrayList<>();
        
        for (Node node : evolutionGraph.getAllNodes()) {
            if (!(node instanceof MethodNode)) {
                continue;
            }
            
            VersionStatus status = (VersionStatus) node.getProperty("versionStatus");
            if (status == VersionStatus.MODIFIED) {
                modifiedMethods.add((MethodNode) node);
            }
        }
        
        return modifiedMethods;
    }
    
    /**
     * 分析单个方法的影响范围
     * @param modifiedMethod 被修改的方法(V2版本)
     * @return 影响链
     */
    public ImpactChain analyzeImpact(MethodNode modifiedMethod) {
        ImpactChain chain = new ImpactChain(modifiedMethod);
        
        // 1. 找出直接调用者
        List<MethodNode> directCallers = findDirectCallers(modifiedMethod);
        chain.setDirectCallers(directCallers);
        
        // 2. 递归找出间接调用者(可配置深度)
        Map<Integer, List<MethodNode>> indirectCallers = 
            findIndirectCallers(modifiedMethod, 3); // 最多3层
        chain.setIndirectCallers(indirectCallers);
        
        // 3. 分析影响类型
        ChangeImpact impact = analyzeChangeImpact(modifiedMethod);
        chain.setChangeImpact(impact);
        
        // 4. 找出需要重新测试的测试方法
        List<MethodNode> affectedTests = findAffectedTests(modifiedMethod);
        chain.setAffectedTests(affectedTests);
        
        return chain;
    }
    
    /**
     * 找出直接调用该方法的方法
     */
    private List<MethodNode> findDirectCallers(MethodNode targetMethod) {
        List<MethodNode> callers = new ArrayList<>();
        
        // 获取所有 CALLS 边,其中 target 是该方法
        List<Edge> incomingCalls = evolutionGraph.getIncomingEdges(targetMethod.getId())
            .stream()
            .filter(edge -> edge.getEdgeType().equals("CALLS"))
            .collect(Collectors.toList());
        
        for (Edge callEdge : incomingCalls) {
            Node callerNode = evolutionGraph.getNode(callEdge.getSourceId());
            if (callerNode instanceof MethodNode) {
                callers.add((MethodNode) callerNode);
            }
        }
        
        return callers;
    }
    
    /**
     * 递归找出间接调用者
     */
    private Map<Integer, List<MethodNode>> findIndirectCallers(
            MethodNode targetMethod, int maxDepth) {
        
        Map<Integer, List<MethodNode>> result = new HashMap<>();
        Set<String> visited = new HashSet<>();
        
        findIndirectCallersRecursive(
            targetMethod, 1, maxDepth, visited, result
        );
        
        return result;
    }
    
    private void findIndirectCallersRecursive(
            MethodNode currentMethod,
            int currentDepth,
            int maxDepth,
            Set<String> visited,
            Map<Integer, List<MethodNode>> result) {
        
        if (currentDepth > maxDepth) {
            return;
        }
        
        visited.add(currentMethod.getId());
        
        List<MethodNode> callers = findDirectCallers(currentMethod);
        result.computeIfAbsent(currentDepth, k -> new ArrayList<>()).addAll(callers);
        
        for (MethodNode caller : callers) {
            if (!visited.contains(caller.getId())) {
                findIndirectCallersRecursive(
                    caller, currentDepth + 1, maxDepth, visited, result
                );
            }
        }
    }
    
    /**
     * 分析变更的影响类型
     */
    private ChangeImpact analyzeChangeImpact(MethodNode modifiedMethod) {
        ChangeImpact impact = new ChangeImpact();
        
        // 找到该方法的演化边
        List<EvolutionEdge> evolutionEdges = evolutionGraph.getOutgoingEdges(
            modifiedMethod.getId()
        ).stream()
        .filter(edge -> edge instanceof EvolutionEdge)
        .map(edge -> (EvolutionEdge) edge)
        .collect(Collectors.toList());
        
        if (evolutionEdges.isEmpty()) {
            return impact;
        }
        
        EvolutionEdge evolutionEdge = evolutionEdges.get(0);
        RefactoringType refactoringType = evolutionEdge.getRefactoringType();
        
        // 根据重构类型判断影响
        switch (refactoringType) {
            case CHANGE_SIGNATURE:
                impact.setSignatureChanged(true);
                impact.setImpactLevel(ImpactLevel.HIGH);
                impact.setDescription("方法签名变更,所有调用者需要更新");
                break;
            case MODIFY_BODY:
                impact.setBodyChanged(true);
                impact.setImpactLevel(ImpactLevel.MEDIUM);
                impact.setDescription("方法体修改,可能影响行为");
                break;
            case RENAME:
                impact.setNameChanged(true);
                impact.setImpactLevel(ImpactLevel.HIGH);
                impact.setDescription("方法重命名,所有调用者需要更新");
                break;
            // ... 其他类型
        }
        
        return impact;
    }
    
    /**
     * 找出受影响的测试方法
     */
    private List<MethodNode> findAffectedTests(MethodNode modifiedMethod) {
        List<MethodNode> affectedTests = new ArrayList<>();
        
        // 1. 找出直接测试该方法的测试
        List<Edge> testEdges = evolutionGraph.getIncomingEdges(modifiedMethod.getId())
            .stream()
            .filter(edge -> edge.getEdgeType().equals("TESTS"))
            .collect(Collectors.toList());
        
        for (Edge testEdge : testEdges) {
            Node testNode = evolutionGraph.getNode(testEdge.getSourceId());
            if (testNode instanceof MethodNode) {
                affectedTests.add((MethodNode) testNode);
            }
        }
        
        // 2. 找出调用该方法的方法的测试(间接)
        List<MethodNode> callers = findDirectCallers(modifiedMethod);
        for (MethodNode caller : callers) {
            affectedTests.addAll(findAffectedTests(caller));
        }
        
        return affectedTests.stream()
            .distinct()
            .collect(Collectors.toList());
    }
}

/**
 * 影响链数据结构
 */
class ImpactChain {
    private MethodNode modifiedMethod;
    private List<MethodNode> directCallers;
    private Map<Integer, List<MethodNode>> indirectCallers;
    private ChangeImpact changeImpact;
    private List<MethodNode> affectedTests;
    
    // 构造函数和getter/setter...
    
    /**
     * 生成影响报告
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== 影响分析报告 ===\n");
        report.append("修改的方法: ").append(modifiedMethod.getSignature()).append("\n");
        report.append("变更类型: ").append(changeImpact.getDescription()).append("\n\n");
        
        report.append("直接调用者 (").append(directCallers.size()).append("):\n");
        for (MethodNode caller : directCallers) {
            report.append("  - ").append(caller.getSignature()).append("\n");
        }
        
        if (!indirectCallers.isEmpty()) {
            report.append("\n间接调用者:\n");
            for (Map.Entry<Integer, List<MethodNode>> entry : indirectCallers.entrySet()) {
                report.append("  Level ").append(entry.getKey()).append(" (")
                      .append(entry.getValue().size()).append("):\n");
                for (MethodNode caller : entry.getValue()) {
                    report.append("    - ").append(caller.getSignature()).append("\n");
                }
            }
        }
        
        report.append("\n受影响的测试 (").append(affectedTests.size()).append("):\n");
        for (MethodNode test : affectedTests) {
            report.append("  - ").append(test.getSignature()).append("\n");
        }
        
        return report.toString();
    }
}

/**
 * 变更影响详情
 */
class ChangeImpact {
    private boolean signatureChanged;
    private boolean bodyChanged;
    private boolean nameChanged;
    private ImpactLevel impactLevel;
    private String description;
    
    // getter/setter...
}

enum ImpactLevel {
    LOW,      // 低影响,如注释修改
    MEDIUM,   // 中影响,如方法体修改
    HIGH      // 高影响,如签名变更
}
```

---

### 5. 使用示例

```java
/**
 * 示例: 分析两个版本之间的演化并进行影响分析
 */
public class EvolutionAnalysisExample {
    
    public static void main(String[] args) throws Exception {
        String repoPath = "/path/to/your/java/project";
        String commitV1 = "abc123";  // 旧版本
        String commitV2 = "def456";  // 新版本
        
        // 1. 创建演化分析器
        EvolutionAnalyzer analyzer = new EvolutionAnalyzer(repoPath);
        
        // 2. 执行演化分析
        EvolutionResult result = analyzer.analyzeEvolution(commitV1, commitV2);
        
        // 3. 获取演化图谱
        KnowledgeGraph evolutionGraph = result.getEvolutionGraph();
        
        // 4. 获取影响分析索引
        ImpactIndex impactIndex = result.getImpactIndex();
        
        // 5. 查询特定方法的影响
        String targetMethodSignature = "com.example.UserService#updateUser(User)";
        MethodNode targetMethod = (MethodNode) evolutionGraph.getNode(targetMethodSignature);
        
        if (targetMethod != null) {
            ImpactChain impactChain = impactIndex.getImpactChain(targetMethod.getId());
            System.out.println(impactChain.generateReport());
        }
        
        // 6. 导出到 Neo4j
        Neo4jBulkCsvExporter exporter = new Neo4jBulkCsvExporter();
        String exportDir = exporter.exportForBulkImport(
            evolutionGraph, 
            "my-project-evolution"
        );
        
        // 7. 导入 Neo4j
        Neo4jBulkImporter importer = new Neo4jBulkImporter(config);
        importer.importData(exportDir);
    }
}
```

---

### 6. Neo4j 查询示例

```cypher
// 1. 找出所有被修改的方法
MATCH (m:METHOD)
WHERE m.versionStatus = 'MODIFIED'
RETURN m.signature, m.versions

// 2. 找出某个方法的演化路径
MATCH (m1:METHOD)-[e:EVOLUTION]->(m2:METHOD)
WHERE m1.signature CONTAINS 'updateUser'
RETURN m1.signature AS old, 
       e.refactoringType AS change,
       m2.signature AS new,
       e.changeDetails

// 3. 影响分析: 找出被修改方法的所有调用者
MATCH (modified:METHOD)-[:EVOLUTION]->(new:METHOD)
WHERE modified.signature = 'com.example.UserService#updateUser(User)'
MATCH (caller:METHOD)-[:CALLS]->(new)
RETURN caller.signature, caller.relativePath

// 4. 多层影响分析: 找出3层调用链
MATCH path = (modified:METHOD)-[:EVOLUTION]->(:METHOD)<-[:CALLS*1..3]-(caller:METHOD)
WHERE modified.signature = 'com.example.UserService#updateUser(User)'
RETURN caller.signature, length(path) AS callDepth

// 5. 找出需要重新测试的测试方法
MATCH (modified:METHOD)-[:EVOLUTION]->(:METHOD)<-[:CALLS*1..3]-(affected:METHOD)
WHERE modified.versionStatus = 'MODIFIED'
MATCH (test:METHOD)-[:TESTS]->(affected)
WHERE test.kind = 'TEST_METHOD'
RETURN DISTINCT test.signature, test.relativePath

// 6. 统计变更类型分布
MATCH ()-[e:EVOLUTION]->()
RETURN e.refactoringType, count(*) AS count
ORDER BY count DESC

// 7. 找出高影响变更(签名变更或重命名)
MATCH (m1:METHOD)-[e:EVOLUTION]->(m2:METHOD)
WHERE e.refactoringType IN ['CHANGE_SIGNATURE', 'RENAME']
MATCH (caller:METHOD)-[:CALLS]->(m2)
RETURN m1.signature AS oldMethod,
       m2.signature AS newMethod,
       e.refactoringType AS changeType,
       count(caller) AS affectedCallers
ORDER BY affectedCallers DESC

// 8. 找出未变化的核心方法(被调用次数多但未修改)
MATCH (m:METHOD)
WHERE m.versionStatus = 'UNCHANGED'
  AND size((m)<-[:CALLS]-()) > 10
RETURN m.signature, 
       size((m)<-[:CALLS]-()) AS callCount,
       m.versions
ORDER BY callCount DESC

// 9. 版本对比: V1和V2的方法数量变化
MATCH (m:METHOD)
WHERE 'v1' IN m.versions
WITH count(m) AS v1Count
MATCH (m:METHOD)
WHERE 'v2' IN m.versions
RETURN v1Count, count(m) AS v2Count, count(m) - v1Count AS delta

// 10. 找出被提取的方法(Extract Method重构)
MATCH (new:METHOD)-[e:EVOLUTION]->(source:METHOD)
WHERE e.refactoringType = 'EXTRACT_METHOD'
RETURN new.signature AS extractedMethod,
       source.signature AS sourceMethod,
       e.extractedStatements
```

---

## 📊 实现计划

### 阶段 1: 基础设施 (第1周)

**任务**:
1. 添加 RefactoringMiner 依赖到 `pom.xml`
2. 扩展 `Node` 类,添加版本属性
3. 创建 `EvolutionEdge` 基类
4. 创建具体的演化边类型 (RenamedToEdge, MovedToEdge, etc.)
5. 创建 `RefactoringType` 枚举

**可交付成果**:
- 更新的 `Node.java`
- 新的演化边类 (7-8个)
- 单元测试

### 阶段 2: RefactoringMiner 集成 (第2周)

**任务**:
1. 创建 `GitService` 接口和实现
2. 实现 `EvolutionAnalyzer` 核心逻辑
3. 实现 Git checkout 到临时目录
4. 测试 RefactoringMiner API

**可交付成果**:
- `GitService.java`, `GitServiceImpl.java`
- `EvolutionAnalyzer.java`
- 集成测试(在真实 Git 仓库上测试)

### 阶段 3: 图谱合并算法 (第3-4周)

**任务**:
1. 实现 `NodeMapping` 数据结构
2. 实现 RefactoringMiner 结果到节点映射的转换
3. 实现代码相似度计算
4. 实现 `GraphMerger` 核心逻辑
5. 处理各种边界情况

**可交付成果**:
- `GraphMerger.java`
- `NodeMapping.java`
- 全面的单元测试和集成测试

### 阶段 4: 影响分析模块 (第5周)

**任务**:
1. 实现 `ImpactAnalyzer`
2. 实现影响链追踪算法
3. 实现测试方法识别
4. 生成影响报告

**可交付成果**:
- `ImpactAnalyzer.java`
- `ImpactChain.java`, `ChangeImpact.java`
- 影响分析报告示例

### 阶段 5: Neo4j 导出适配 (第6周)

**任务**:
1. 更新 `Neo4jBulkCsvExporter` 支持版本属性
2. 更新 CSV 格式支持演化边
3. 创建版本查询的 Cypher 模板
4. 性能优化

**可交付成果**:
- 更新的 `Neo4jBulkCsvExporter.java`
- Cypher 查询示例文档
- 性能测试报告

### 阶段 6: 端到端测试和文档 (第7周)

**任务**:
1. 在真实项目上进行端到端测试
2. 编写用户文档
3. 创建示例和教程
4. 性能调优

**可交付成果**:
- 完整的用户文档
- 示例项目和教程
- 性能基准测试

---

## 🎯 关键技术挑战和解决方案

### 挑战 1: RefactoringMiner 漏检

**问题**: RefactoringMiner 可能无法检测所有重构

**解决方案**:
- 实现代码相似度算法作为补充
- 使用多种相似度度量 (名称、签名、代码结构、调用图)
- 设置合理的相似度阈值

### 挑战 2: 节点 ID 一致性

**问题**: 重构后节点 ID 可能变化,导致匹配困难

**解决方案**:
- 使用语义化的 ID 生成策略
- 方法: `{包名}.{类名}#{方法名}({参数类型})`
- 结合位置信息和代码哈希

### 挑战 3: 大型项目性能

**问题**: 分析大型项目可能很慢

**解决方案**:
- 增量分析: 只分析变更的文件
- 并行处理: 多线程构建图谱
- 缓存机制: 缓存已分析的节点

### 挑战 4: 复杂重构链

**问题**: 一个节点可能经历多次重构

**解决方案**:
- 记录完整的演化历史
- 使用演化边的链式结构
- 支持查询完整演化路径

---

## 💡 后续扩展方向

1. **多版本支持**: 扩展到支持3个以上版本
2. **可视化**: 开发演化图谱的可视化工具
3. **智能推荐**: 基于影响分析推荐需要更新的测试
4. **变更预测**: 基于历史演化预测未来变更
5. **代码审查**: 集成到 CI/CD 流程,自动生成变更影响报告

---

## 📝 总结

这个设计方案:

✅ **满足版本粒度需求**: Git commit 级别  
✅ **满足节点策略**: 保留两版本,细粒度演化边  
✅ **满足存储策略**: 只存储差异,未变节点唯一  
✅ **满足核心用例**: 强大的影响分析能力  

核心优势:
- **精确的重构检测**: 基于 RefactoringMiner 的 AST 分析
- **灵活的节点匹配**: 结合规则和相似度算法
- **细粒度的演化边**: 7+ 种演化关系类型
- **强大的影响分析**: 多层调用链追踪
- **测试演化支持**: 自动识别受影响的测试

你觉得这个方案怎么样?有什么需要调整或补充的地方吗? 🤔

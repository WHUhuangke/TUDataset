package com.github.evolution;

import com.github.logging.GraphLogger;
import com.github.model.*;
import com.github.model.nodes.*;
import com.github.model.edges.evolution.RefactoredEdge;
import com.github.model.edges.evolution.UnchangedEdge;
import com.github.refactoring.RefactoringInfo;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图合并器 - 合并两个版本的知识图谱
 * 
 * 核心功能：
 * 1. 基于 NodeMapping 合并 V1 和 V2 图
 * 2. 标记节点状态：UNCHANGED, MODIFIED, ADDED, DELETED
 * 3. 基于 RefactoringInfo 创建演化边
 * 4. 合并结构性边（CALLS, DECLARES等）
 * 
 * 合并策略：
 * - UNCHANGED (confidence=1.0): 合并为单个节点，保留 V1 信息
 * - MODIFIED (mapped but different): 保留两个版本，创建演化边
 * - ADDED (only in V2): 直接添加到合并图
 * - DELETED (only in V1): 直接添加到合并图
 */
public class GraphMerger {
    private final GraphLogger logger;
    
    // 合并过程中的统计信息
    private int unchangedCount = 0;
    private int modifiedCount = 0;
    private int addedCount = 0;
    private int deletedCount = 0;
    private int evolutionEdgesCount = 0;
    private int structuralEdgesCount = 0;

    private DiffChangeSet diffChangeSet;
    private final Set<String> diffPairTracker = new HashSet<>();
    private final List<DiffNodePair> diffNodePairs = new ArrayList<>();

    private static class DiffNodePair {
        private final String v1Id;
        private final String v2Id;

        private DiffNodePair(String v1Id, String v2Id) {
            this.v1Id = v1Id;
            this.v2Id = v2Id;
        }
    }

    private static class VersionMetadata {
        private final String commitId;
        private final String shortId;
        private final String message;
        private final String author;
        private final long commitTime;

        private VersionMetadata(String commitId, String shortId, String message, String author, long commitTime) {
            this.commitId = commitId;
            this.shortId = shortId;
            this.message = message;
            this.author = author;
            this.commitTime = commitTime;
        }
    }
    private String activeV1Label = "V1";
    private String activeV2Label = "V2";
    private static final Set<String> trackedBaseNodeIds =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<String, VersionMetadata> versionMetadataRegistry =
            new ConcurrentHashMap<>();

    public GraphMerger(GraphLogger logger) {
        this.logger = logger;
    }

    public static void resetTrackedNodes() {
        trackedBaseNodeIds.clear();
        versionMetadataRegistry.clear();
    }

    public static void registerVersionMetadata(String label,
                                               String commitId,
                                               String shortId,
                                               String message,
                                               String author,
                                               long commitTime) {
        versionMetadataRegistry.put(label, new VersionMetadata(commitId, shortId, message, author, commitTime));
    }
    
    /**
     * 合并两个版本的图
     * 
     * @param v1Graph V1 版本的图
     * @param v2Graph V2 版本的图
     * @param mapping 节点映射关系
     * @param refactorings 重构信息列表
     * @return 合并后的图
     */
    public KnowledgeGraph merge(
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            NodeMapping mapping,
            List<RefactoringInfo> refactorings,
            DiffChangeSet diffChangeSet) {
        return mergeWithLabels(v1Graph, v2Graph, mapping, refactorings, diffChangeSet, "V1", "V2");
    }

    public KnowledgeGraph mergeWithLabels(
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            NodeMapping mapping,
            List<RefactoringInfo> refactorings,
            DiffChangeSet diffChangeSet,
            String v1Label,
            String v2Label) {

        this.activeV1Label = v1Label;
        this.activeV2Label = v2Label;
        
        logger.info("开始合并图谱...");
        logger.info("V1 图: " + v1Graph.getAllNodes().size() + " 节点, " + v1Graph.getAllEdges().size() + " 边");
        logger.info("V2 图: " + v2Graph.getAllNodes().size() + " 节点, " + v2Graph.getAllEdges().size() + " 边");
        logger.info("映射关系: " + mapping.size() + " 对");
        
        // 重置统计
        resetStatistics();
        this.diffPairTracker.clear();
        this.diffNodePairs.clear();
        this.diffChangeSet = diffChangeSet;
        
        // 创建合并后的图
        KnowledgeGraph mergedGraph = new KnowledgeGraph();
        mergedGraph.setFromVersion(v1Graph.getFromVersion());
        mergedGraph.setToVersion(v2Graph.getToVersion());
        
        // 映射：V1/V2 节点ID -> 合并图中的节点ID
        Map<String, String> v1ToMergedId = new HashMap<>();
        Map<String, String> v2ToMergedId = new HashMap<>();
        
        // Step 1: 处理映射的节点
        processMappedNodes(v1Graph, v2Graph, mapping, mergedGraph, v1ToMergedId, v2ToMergedId);
        
        // Step 2: 处理未映射的 V1 节点（DELETED）
        processDeletedNodes(v1Graph, mapping, mergedGraph, v1ToMergedId);
        
        // Step 3: 处理未映射的 V2 节点（ADDED）
        processAddedNodes(v2Graph, mapping, mergedGraph, v2ToMergedId);
        
        // Step 4: 创建演化边
        createEvolutionEdges(v1Graph, v2Graph, mapping, refactorings, mergedGraph, v1ToMergedId, v2ToMergedId);
        createDiffEdgesFromGit(mergedGraph, v1Graph, v2Graph, v1ToMergedId, v2ToMergedId);

        // Step 5: 合并结构性边
        mergeStructuralEdges(v1Graph, v2Graph, mergedGraph, v1ToMergedId, v2ToMergedId);
        
        // 打印统计
        printStatistics(mergedGraph);
        
        return mergedGraph;
    }
    
    /**
     * 处理有映射关系的节点
     */
    private void processMappedNodes(
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            NodeMapping mapping,
            KnowledgeGraph mergedGraph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {
        
        logger.info("处理映射节点...");
        
        // 获取所有 V1 节点 ID
        Set<String> v1NodeIds = new java.util.HashSet<>();
        for (Node v1Node : v1Graph.getAllNodes()) {
            if (mapping.hasMappingForV1(v1Node.getId())) {
                v1NodeIds.add(v1Node.getId());
            }
        }
        
        for (String v1Id : v1NodeIds) {
            String v2Id = mapping.getMappedNode(v1Id);
            if (v2Id == null) continue;
            
            double confidence = mapping.getConfidence(v1Id);
            boolean forcedModified = diffChangeSet != null && diffChangeSet.isChanged(v1Id, v2Id);
            Node v1Node = v1Graph.getNode(v1Id);
            Node v2Node = v2Graph.getNode(v2Id);
            
            if (v1Node == null || v2Node == null) continue;
            
            boolean changed = confidence < 1.0 || forcedModified;

            if (changed) {
                markTracked(v1Node, v2Node);
                processModifiedNode(v1Node, v2Node, mergedGraph, v1ToMergedId, v2ToMergedId);
                if (forcedModified) {
                    recordDiffPair(v1Node.getId(), v2Node.getId());
                }
                continue;
            }

            if (shouldTrack(v1Node) || shouldTrack(v2Node)) {
                processTrackedUnchangedNode(v1Node, v2Node, mergedGraph, v1ToMergedId, v2ToMergedId);
            } else {
                processUnchangedNode(v1Node, v2Node, mergedGraph, v1ToMergedId, v2ToMergedId);
            }
        }
        
        logger.info("映射节点处理完成: UNCHANGED=" + unchangedCount + ", MODIFIED=" + modifiedCount);
    }
    
    /**
     * 处理完全相同的节点（confidence = 1.0）
     */
    private void processUnchangedNode(
            Node v1Node,
            Node v2Node,
            KnowledgeGraph mergedGraph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {
        
        // 克隆 V1 节点
        Node mergedNode = cloneNode(v1Node);
        
        // 标记为 UNCHANGED
        mergedNode.setVersionStatus(VersionStatus.UNCHANGED);
        mergedNode.addVersion(activeV1Label);
        mergedNode.addVersion(activeV2Label);
        mergedNode.setFirstVersion(activeV1Label);
        mergedNode.setLastVersion(activeV2Label);
        
        // 添加到合并图
        mergedGraph.addNode(mergedNode);
        
        // 记录映射关系（两个版本的节点都映射到同一个合并节点）
        v1ToMergedId.put(v1Node.getId(), mergedNode.getId());
        v2ToMergedId.put(v2Node.getId(), mergedNode.getId());
        
        unchangedCount++;
    }

    private void processTrackedUnchangedNode(
            Node v1Node,
            Node v2Node,
            KnowledgeGraph mergedGraph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {

        markTracked(v1Node, v2Node);
        Node v1VersionNode = cloneNode(v1Node, createVersionedId(v1Node.getId(), activeV1Label));
        initializeVersionSnapshot(v1VersionNode, activeV1Label, VersionStatus.UNCHANGED);
        mergedGraph.addNode(v1VersionNode);
        v1ToMergedId.put(v1Node.getId(), v1VersionNode.getId());

        Node v2VersionNode = cloneNode(v2Node, createVersionedId(v2Node.getId(), activeV2Label));
        initializeVersionSnapshot(v2VersionNode, activeV2Label, VersionStatus.UNCHANGED);
        mergedGraph.addNode(v2VersionNode);
        v2ToMergedId.put(v2Node.getId(), v2VersionNode.getId());

        UnchangedEdge edge = new UnchangedEdge(
                v1VersionNode.getId(),
                v2VersionNode.getId(),
                activeV1Label,
                activeV2Label
        );
        edge.setDescription("No change detected between " + activeV1Label + " and " + activeV2Label);
        mergedGraph.addEvolutionEdge(edge);
        evolutionEdgesCount++;
        unchangedCount++;
    }
    
    /**
     * 处理有变化的节点（confidence < 1.0）
     */
    private void processModifiedNode(
            Node v1Node,
            Node v2Node,
            KnowledgeGraph mergedGraph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {
        
        // 克隆 V1 版本
        Node v1Merged = cloneNode(v1Node, createVersionedId(v1Node.getId(), activeV1Label));
        initializeVersionSnapshot(v1Merged, activeV1Label, VersionStatus.MODIFIED);
        mergedGraph.addNode(v1Merged);
        v1ToMergedId.put(v1Node.getId(), v1Merged.getId());
        
        // 克隆 V2 版本
        Node v2Merged = cloneNode(v2Node, createVersionedId(v2Node.getId(), activeV2Label));
        initializeVersionSnapshot(v2Merged, activeV2Label, VersionStatus.MODIFIED);
        mergedGraph.addNode(v2Merged);
        v2ToMergedId.put(v2Node.getId(), v2Merged.getId());
        
        modifiedCount++;
    }

    private void recordDiffPair(String v1Id, String v2Id) {
        if (v1Id == null || v2Id == null) {
            return;
        }
        String key = v1Id + "->" + v2Id;
        if (diffPairTracker.add(key)) {
            diffNodePairs.add(new DiffNodePair(v1Id, v2Id));
        }
    }

    /**
     * 处理只在 V1 中存在的节点（DELETED）
     */
    private void processDeletedNodes(
            KnowledgeGraph v1Graph,
            NodeMapping mapping,
            KnowledgeGraph mergedGraph,
            Map<String, String> v1ToMergedId) {
        
        logger.info("处理删除节点...");
        
        for (Node v1Node : v1Graph.getAllNodes()) {
            if (!mapping.hasMappingForV1(v1Node.getId())) {
                // 克隆节点
                Node deletedNode = cloneNode(v1Node, createVersionedId(v1Node.getId(), activeV1Label));
                initializeVersionSnapshot(deletedNode, activeV1Label, VersionStatus.DELETED);
                
                // 添加到合并图
                mergedGraph.addNode(deletedNode);
                v1ToMergedId.put(v1Node.getId(), deletedNode.getId());
                
                deletedCount++;
                markTracked(v1Node, null);
            }
        }

        logger.info("删除节点处理完成: " + deletedCount + " 个");
    }
    
    /**
     * 处理只在 V2 中存在的节点（ADDED）
     */
    private void processAddedNodes(
            KnowledgeGraph v2Graph,
            NodeMapping mapping,
            KnowledgeGraph mergedGraph,
            Map<String, String> v2ToMergedId) {
        
        logger.info("处理新增节点...");
        
        for (Node v2Node : v2Graph.getAllNodes()) {
            if (!mapping.hasMappingForV2(v2Node.getId())) {
                // 克隆节点
                Node addedNode = cloneNode(v2Node, createVersionedId(v2Node.getId(), activeV2Label));
                initializeVersionSnapshot(addedNode, activeV2Label, VersionStatus.ADDED);
                
                // 添加到合并图
                mergedGraph.addNode(addedNode);
                v2ToMergedId.put(v2Node.getId(), addedNode.getId());
                
                addedCount++;
                markTracked(null, v2Node);
            }
        }
        
        logger.info("新增节点处理完成: " + addedCount + " 个");
    }
    
    /**
     * 创建演化边（基于 RefactoringInfo）
     */
    private void createEvolutionEdges(
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            NodeMapping mapping,
            List<RefactoringInfo> refactorings,
            KnowledgeGraph mergedGraph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {
        
        logger.info("创建演化边...");
        
        if (refactorings == null || refactorings.isEmpty()) {
            logger.info("无重构信息，跳过演化边创建");
            return;
        }
        
        for (RefactoringInfo refactoring : refactorings) {
            createEvolutionEdgeFromRefactoring(
                    refactoring,
                    v1Graph,
                    v2Graph,
                    mergedGraph, 
                    v1ToMergedId, 
                    v2ToMergedId
            );
        }
        
        logger.info("演化边创建完成: " + evolutionEdgesCount + " 条");
    }

    private void createDiffEdgesFromGit(
            KnowledgeGraph mergedGraph,
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {

        if (diffNodePairs.isEmpty()) {
            return;
        }

        String fromVersion = v1Graph.getFromVersion() != null ? v1Graph.getFromVersion() : v1Graph.getToVersion();
        String toVersion = v2Graph.getToVersion() != null ? v2Graph.getToVersion() : v2Graph.getFromVersion();

        for (DiffNodePair pair : diffNodePairs) {
            String mergedV1 = v1ToMergedId.get(pair.v1Id);
            String mergedV2 = v2ToMergedId.get(pair.v2Id);

            if (mergedV1 == null || mergedV2 == null) {
                continue;
            }

            if (hasEvolutionEdge(mergedGraph, mergedV1, mergedV2)) {
                continue;
            }

            RefactoredEdge edge = new RefactoredEdge(mergedV1, mergedV2, fromVersion, toVersion);
            edge.setRefactoringType("CODE_DIFF");
            edge.setDescription("Git diff 检测到的代码改动");
            edge.setConfidence(0.7);
            edge.setProperty("detectedBy", "GitDiff");

            mergedGraph.addEvolutionEdge(edge);
            evolutionEdgesCount++;

            Node fromNode = findNodeInMergedGraph(mergedGraph, mergedV1);
            Node toNode = findNodeInMergedGraph(mergedGraph, mergedV2);

            if (fromNode != null && toNode != null) {
                RefactoringInfo syntheticInfo = new RefactoringInfo();
                syntheticInfo.setType("CODE_DIFF");
                syntheticInfo.setDescription(edge.getDescription());

                propagateMemberEvolutionToTypes(
                        syntheticInfo,
                        fromNode,
                        toNode,
                        edge,
                        mergedGraph,
                        v1Graph,
                        v2Graph,
                        v1ToMergedId,
                        v2ToMergedId
                );
            }
        }
    }

    private boolean hasEvolutionEdge(KnowledgeGraph mergedGraph, String fromId, String toId) {
        for (Edge edge : mergedGraph.getOutgoingEdges(fromId)) {
            if (edge instanceof EvolutionEdge && edge.getTargetId().equals(toId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 从单个重构信息创建演化边
     * 
     * <p>策略：
     * 1. 根据 leftSideLocations 在 V1 图中查找源节点（基于文件路径+行号）
     * 2. 根据 rightSideLocations 在 V2 图中查找目标节点
     * 3. 将 V1/V2 节点 ID 映射到合并图中的节点 ID
     * 4. **检测自连问题**：如果 fromNode == toNode（节点被错误合并），则拆分
     * 5. 使用 EvolutionEdgeFactory 创建对应类型的演化边
     * 6. 将边添加到合并图中
     */
    private void createEvolutionEdgeFromRefactoring(
            RefactoringInfo refactoring,
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            KnowledgeGraph mergedGraph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {
        
        String type = refactoring.getType();
        logger.debug("处理重构: " + type + " - " + refactoring.getDescription());
        
        // 1. 从 leftSideLocations 查找源节点（V1）
        List<Node> fromNodes = findNodesFromLocations(
                refactoring.getLeftSideLocations(),
                v1Graph,
                v1ToMergedId,
                mergedGraph
        );
        
        // 2. 从 rightSideLocations 查找目标节点（V2）
        List<Node> toNodes = findNodesFromLocations(
                refactoring.getRightSideLocations(),
                v2Graph,
                v2ToMergedId,
                mergedGraph
        );
        
        // 3. 创建演化边（处理一对一、一对多、多对多的情况）
        if (fromNodes.isEmpty() || toNodes.isEmpty()) {
            logger.debug("  ⚠ 无法找到对应节点，跳过此重构");
            return;
        }
        
        // 4. 检测并处理自连问题（fromNode == toNode）
        //    这种情况发生在：节点被 ExactMatcher 判定为 confidence=1.0（签名相同），
        //    但 RefactoringMiner 检测到内部有重构操作（如重命名局部变量）
        detectAndFixSelfLoopIssue(fromNodes, toNodes, v1Graph, v2Graph, mergedGraph, 
                                   v1ToMergedId, v2ToMergedId, refactoring);
        
        // 5. 针对不同重构类型处理边的创建逻辑
        if (type.startsWith("EXTRACT_")) {
            // EXTRACT_METHOD: 1个源节点 -> 多个目标节点
            createOneToManyEdges(refactoring, fromNodes, toNodes, mergedGraph, v1Graph, v2Graph, v1ToMergedId, v2ToMergedId);
        } else if (type.startsWith("INLINE_")) {
            // INLINE_METHOD: 多个源节点 -> 1个目标节点
            createManyToOneEdges(refactoring, fromNodes, toNodes, mergedGraph, v1Graph, v2Graph, v1ToMergedId, v2ToMergedId);
        } else {
            // 其他重构：1对1（或取第一个匹配）
            createOneToOneEdge(refactoring, fromNodes, toNodes, mergedGraph, v1Graph, v2Graph, v1ToMergedId, v2ToMergedId);
        }
    }
    
    /**
     * 检测并修复自连问题
     * 
     * <p>当检测到 fromNode == toNode 时，说明这两个节点被错误地合并了。
     * 我们需要将合并的节点拆分成 V1 和 V2 两个版本。
     * 
     * @param fromNodes 源节点列表（会被修改）
     * @param toNodes 目标节点列表（会被修改）
     * @param v1Graph V1 图
     * @param v2Graph V2 图
     * @param mergedGraph 合并图
     * @param v1ToMergedId V1 到合并图的映射（会被修改）
     * @param v2ToMergedId V2 到合并图的映射（会被修改）
     * @param refactoring 重构信息（用于日志）
     */
    private void detectAndFixSelfLoopIssue(
            List<Node> fromNodes,
            List<Node> toNodes,
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            KnowledgeGraph mergedGraph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId,
            RefactoringInfo refactoring) {
        
        // 检查是否存在自连
        for (int i = 0; i < fromNodes.size(); i++) {
            Node fromNode = fromNodes.get(i);
            
            for (int j = 0; j < toNodes.size(); j++) {
                Node toNode = toNodes.get(j);
                
                if (fromNode.getId().equals(toNode.getId())) {
                    // 发现自连！需要拆分节点
                    logger.debug("  ⚠ 检测到自连问题: " + fromNode.getLabel() + 
                                " (该节点被错误合并，现在拆分)");
                    
                    // 拆分节点：将原本合并的节点拆分成 V1 和 V2 两个版本
                    Node[] splitNodes = splitMergedNode(
                            fromNode, 
                            v1Graph, 
                            v2Graph, 
                            mergedGraph,
                            v1ToMergedId,
                            v2ToMergedId
                    );
                    
                    // 更新列表中的引用
                    fromNodes.set(i, splitNodes[0]);  // V1 版本
                    toNodes.set(j, splitNodes[1]);    // V2 版本
                    
                    logger.debug("  ✓ 节点已拆分: " + 
                                splitNodes[0].getLabel() + " (V1) <---> " + 
                                splitNodes[1].getLabel() + " (V2)");
                }
            }
        }
    }
    
    /**
     * 拆分被错误合并的节点
     * 
     * <p>策略：不删除原合并节点（因为 KnowledgeGraph 没有 removeNode 方法），
     * 而是将原节点修改为 V1 版本，然后创建新的 V2 版本节点。
     * 
     * @param mergedNode 被合并的节点
     * @param v1Graph V1 图
     * @param v2Graph V2 图
     * @param mergedGraph 合并图
     * @param v1ToMergedId V1 到合并图的映射
     * @param v2ToMergedId V2 到合并图的映射
     * @return [V1版本节点, V2版本节点]
     */
    private Node[] splitMergedNode(
            Node mergedNode,
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            KnowledgeGraph mergedGraph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {
        
        // 1. 找到原始的 V1 和 V2 节点
        //    通过反向查找映射关系
        Node v1OriginalNode = null;
        Node v2OriginalNode = null;
        
        for (Map.Entry<String, String> entry : v1ToMergedId.entrySet()) {
            if (entry.getValue().equals(mergedNode.getId())) {
                v1OriginalNode = v1Graph.getNode(entry.getKey());
                break;
            }
        }
        
        for (Map.Entry<String, String> entry : v2ToMergedId.entrySet()) {
            if (entry.getValue().equals(mergedNode.getId())) {
                v2OriginalNode = v2Graph.getNode(entry.getKey());
                break;
            }
        }
        
        if (v1OriginalNode == null || v2OriginalNode == null) {
            logger.warn("  ⚠ 无法找到原始节点，跳过拆分");
            // 返回原节点（虽然会自连，但总比崩溃好）
            return new Node[]{mergedNode, mergedNode};
        }
        
        // 2. 将原合并节点修改为 V1 版本
        //    重新复制 V1 的所有属性
        copyAllProperties(v1OriginalNode, mergedNode);
        mergedNode.setVersionStatus(VersionStatus.MODIFIED);
        mergedNode.getVersions().clear();
        mergedNode.addVersion(activeV1Label);
        mergedNode.setFirstVersion(activeV1Label);
        mergedNode.setLastVersion(activeV1Label);
        
        Node v1Merged = mergedNode;  // 复用原节点作为 V1
        
        // 3. 创建新的 V2 版本节点
        Node v2Merged = cloneNode(v2OriginalNode, createVersionedId(v2OriginalNode.getId(), activeV2Label));
        v2Merged.setVersionStatus(VersionStatus.MODIFIED);
        v2Merged.getVersions().clear();
        v2Merged.addVersion(activeV2Label);
        v2Merged.setFirstVersion(activeV2Label);
        v2Merged.setLastVersion(activeV2Label);
        mergedGraph.addNode(v2Merged);
        
        // 4. 更新映射关系
        //    v1ToMergedId 保持不变（仍指向原节点）
        v2ToMergedId.put(v2OriginalNode.getId(), v2Merged.getId());
        
        // 5. 更新统计
        unchangedCount--;  // 原来算作 UNCHANGED
        modifiedCount++;   // 现在算作 MODIFIED
        
        return new Node[]{v1Merged, v2Merged};
    }
    
    /**
     * 根据代码位置列表查找对应的节点
     * 
     * @param locations 代码位置列表
     * @param sourceGraph 源图（V1 或 V2）
     * @param idMapping 源图 ID 到合并图 ID 的映射
     * @param mergedGraph 合并图
     * @return 合并图中的节点列表
     */
    private List<Node> findNodesFromLocations(
            List<RefactoringInfo.CodeLocation> locations,
            KnowledgeGraph sourceGraph,
            Map<String, String> idMapping,
            KnowledgeGraph mergedGraph) {
        
        List<Node> result = new ArrayList<>();
        NodeLocationMatcher matcher = new NodeLocationMatcher(sourceGraph);
        
        for (RefactoringInfo.CodeLocation location : locations) {
            // 在源图中查找匹配的节点
            List<Node> candidates = matcher.findNodesByLocation(location);
            
            if (candidates.isEmpty()) {
                continue;
            }
            
            // 选择最佳匹配
            Node bestMatch = matcher.findBestMatch(candidates, location);
            
            if (bestMatch != null) {
                // 将源图节点 ID 映射到合并图节点 ID
                String mergedNodeId = idMapping.get(bestMatch.getId());
                
                if (mergedNodeId != null) {
                    // 在合并图中查找节点
                    Node mergedNode = findNodeInMergedGraph(mergedGraph, mergedNodeId);
                    if (mergedNode != null) {
                        result.add(mergedNode);
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 在合并图中查找节点
     */
    private Node findNodeInMergedGraph(KnowledgeGraph mergedGraph, String nodeId) {
        for (Node node : mergedGraph.getAllNodes()) {
            if (node.getId().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }
    
    /**
     * 创建一对一的演化边
     */
    private void createOneToOneEdge(
            RefactoringInfo refactoring,
            List<Node> fromNodes,
            List<Node> toNodes,
            KnowledgeGraph mergedGraph,
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {
        
        Node fromNode = fromNodes.get(0);
        Node toNode = toNodes.get(0);
        
        String fromVersion = v1Graph.getFromVersion() != null ? v1Graph.getFromVersion() : v1Graph.getToVersion();
        String toVersion = v2Graph.getToVersion() != null ? v2Graph.getToVersion() : v2Graph.getFromVersion();
        
        EvolutionEdge edge = EvolutionEdgeFactory.createEdge(
                refactoring,
                fromNode,
                toNode,
                fromVersion,
                toVersion
        );
        
        if (edge != null) {
            mergedGraph.addEvolutionEdge(edge);
            evolutionEdgesCount++;
            logger.debug("  ✓ 创建 " + edge.getEdgeType() + " 边: " + 
                        fromNode.getLabel() + " -> " + toNode.getLabel());
            propagateMemberEvolutionToTypes(
                    refactoring,
                    fromNode,
                    toNode,
                    edge,
                    mergedGraph,
                    v1Graph,
                    v2Graph,
                    v1ToMergedId,
                    v2ToMergedId
            );
        }
    }
    
    /**
     * 创建一对多的演化边（例如 EXTRACT_METHOD）
     */
    private void createOneToManyEdges(
            RefactoringInfo refactoring,
            List<Node> fromNodes,
            List<Node> toNodes,
            KnowledgeGraph mergedGraph,
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {
        
        Node fromNode = fromNodes.get(0);
        
        String fromVersion = v1Graph.getFromVersion() != null ? v1Graph.getFromVersion() : v1Graph.getToVersion();
        String toVersion = v2Graph.getToVersion() != null ? v2Graph.getToVersion() : v2Graph.getFromVersion();
        
        for (Node toNode : toNodes) {
            EvolutionEdge edge = EvolutionEdgeFactory.createEdge(
                    refactoring,
                    fromNode,
                    toNode,
                    fromVersion,
                    toVersion
            );
            
            if (edge != null) {
                mergedGraph.addEvolutionEdge(edge);
                evolutionEdgesCount++;
                logger.debug("  ✓ 创建 " + edge.getEdgeType() + " 边: " + 
                            fromNode.getLabel() + " -> " + toNode.getLabel());
                propagateMemberEvolutionToTypes(
                        refactoring,
                        fromNode,
                        toNode,
                        edge,
                        mergedGraph,
                        v1Graph,
                        v2Graph,
                        v1ToMergedId,
                        v2ToMergedId
                );
            }
        }
    }
    
    private void propagateMemberEvolutionToTypes(
            RefactoringInfo refactoring,
            Node fromMember,
            Node toMember,
            EvolutionEdge memberEdge,
            KnowledgeGraph mergedGraph,
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {
        
        if (fromMember == null || toMember == null) {
            return;
        }
        
        String memberType = fromMember.getNodeType();
        if (!"METHOD".equals(memberType) && !"FIELD".equals(memberType)) {
            return;
        }
        
        Node fromType = findEnclosingTypeNode(fromMember, v1Graph, v1ToMergedId, mergedGraph);
        Node toType = findEnclosingTypeNode(toMember, v2Graph, v2ToMergedId, mergedGraph);
        
        if (fromType == null || toType == null) {
            return;
        }
        
        if (fromType.getId().equals(toType.getId())) {
            Node[] splitNodes = splitMergedNode(
                    fromType,
                    v1Graph,
                    v2Graph,
                    mergedGraph,
                    v1ToMergedId,
                    v2ToMergedId
            );
            if (splitNodes.length < 2 || splitNodes[0].getId().equals(splitNodes[1].getId())) {
                return;
            }
            fromType = splitNodes[0];
            toType = splitNodes[1];
        }
        
        String fromVersion = memberEdge.getFromVersion();
        if (fromVersion == null || fromVersion.isBlank()) {
            fromVersion = v1Graph.getFromVersion() != null ? v1Graph.getFromVersion() : v1Graph.getToVersion();
        }
        
        String toVersion = memberEdge.getToVersion();
        if (toVersion == null || toVersion.isBlank()) {
            toVersion = v2Graph.getToVersion() != null ? v2Graph.getToVersion() : v2Graph.getFromVersion();
        }
        
        RefactoredEdge typeEdge = new RefactoredEdge(
                fromType.getId(),
                toType.getId(),
                fromVersion,
                toVersion
        );
        typeEdge.setRefactoringType("MEMBER_CHANGED");
        typeEdge.setDescription("Type changed due to member refactoring: " + refactoring.getDescription());
        typeEdge.setConfidence(memberEdge.getConfidence());
        typeEdge.setProperty("propagatedMemberType", memberType);
        typeEdge.setProperty("propagatedMemberRefactoring", refactoring.getType());
        
        mergedGraph.addEvolutionEdge(typeEdge);
        evolutionEdgesCount++;
        logger.debug("    ↳ 同步创建类型演化边: " + fromType.getLabel() + " -> " + toType.getLabel());
    }
    
    private Node findEnclosingTypeNode(
            Node memberNode,
            KnowledgeGraph sourceGraph,
            Map<String, String> idMapping,
            KnowledgeGraph mergedGraph) {
        
        if (memberNode == null) {
            return null;
        }
        
        String originalId = null;
        Object originalIdObj = memberNode.getProperty("originalId");
        if (originalIdObj instanceof String && !((String) originalIdObj).isBlank()) {
            originalId = (String) originalIdObj;
        }
        if (originalId == null || originalId.isBlank()) {
            originalId = memberNode.getId();
        }
        
        List<Edge> incomingEdges = sourceGraph.getIncomingEdges(originalId);
        if (incomingEdges == null) {
            return null;
        }
        
        for (Edge edge : incomingEdges) {
            if ("DECLARES".equals(edge.getEdgeType())) {
                String typeId = edge.getSourceId();
                String mergedTypeId = idMapping.get(typeId);
                if (mergedTypeId != null) {
                    Node typeNode = findNodeInMergedGraph(mergedGraph, mergedTypeId);
                    if (typeNode != null) {
                        return typeNode;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 创建多对一的演化边（例如 INLINE_METHOD）
     */
    private void createManyToOneEdges(
            RefactoringInfo refactoring,
            List<Node> fromNodes,
            List<Node> toNodes,
            KnowledgeGraph mergedGraph,
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {
        
        Node toNode = toNodes.get(0);
        
        String fromVersion = v1Graph.getFromVersion() != null ? v1Graph.getFromVersion() : v1Graph.getToVersion();
        String toVersion = v2Graph.getToVersion() != null ? v2Graph.getToVersion() : v2Graph.getFromVersion();
        
        for (Node fromNode : fromNodes) {
            EvolutionEdge edge = EvolutionEdgeFactory.createEdge(
                    refactoring,
                    fromNode,
                    toNode,
                    fromVersion,
                    toVersion
            );
            
            if (edge != null) {
                mergedGraph.addEvolutionEdge(edge);
                evolutionEdgesCount++;
                logger.debug("  ✓ 创建 " + edge.getEdgeType() + " 边: " + 
                            fromNode.getLabel() + " -> " + toNode.getLabel());
                propagateMemberEvolutionToTypes(
                        refactoring,
                        fromNode,
                        toNode,
                        edge,
                        mergedGraph,
                        v1Graph,
                        v2Graph,
                        v1ToMergedId,
                        v2ToMergedId
                );
            }
        }
    }
    
    /**
     * 合并结构性边（CALLS, DECLARES等）
     */
    private void mergeStructuralEdges(
            KnowledgeGraph v1Graph,
            KnowledgeGraph v2Graph,
            KnowledgeGraph mergedGraph,
            Map<String, String> v1ToMergedId,
            Map<String, String> v2ToMergedId) {
        
        logger.info("合并结构边...");
        
        // 使用 Set 来记录已添加的边，避免重复
        // 边的唯一标识：sourceId + edgeType + targetId
        Set<String> addedEdges = new HashSet<>();
        
        // 合并 V1 的边
        for (Edge v1Edge : v1Graph.getAllEdges()) {
            String sourceId = v1ToMergedId.get(v1Edge.getSourceId());
            String targetId = v1ToMergedId.get(v1Edge.getTargetId());
            
            if (sourceId != null && targetId != null) {
                String edgeKey = sourceId + "|" + v1Edge.getEdgeType() + "|" + targetId;
                if (!addedEdges.contains(edgeKey)) {
                    // 创建新边（使用新的source和target ID）
                    Edge mergedEdge = createEdge(v1Edge, sourceId, targetId);
                    if (mergedEdge != null) {
                        mergedGraph.addEdge(mergedEdge);
                        addedEdges.add(edgeKey);
                        structuralEdgesCount++;
                    }
                }
            }
        }
        
        // 合并 V2 的边（去重）
        for (Edge v2Edge : v2Graph.getAllEdges()) {
            String sourceId = v2ToMergedId.get(v2Edge.getSourceId());
            String targetId = v2ToMergedId.get(v2Edge.getTargetId());
            
            if (sourceId != null && targetId != null) {
                String edgeKey = sourceId + "|" + v2Edge.getEdgeType() + "|" + targetId;
                if (!addedEdges.contains(edgeKey)) {
                    Edge mergedEdge = createEdge(v2Edge, sourceId, targetId);
                    if (mergedEdge != null) {
                        mergedGraph.addEdge(mergedEdge);
                        addedEdges.add(edgeKey);
                        structuralEdgesCount++;
                    }
                }
            }
        }
        
        logger.info("结构边合并完成: " + structuralEdgesCount + " 条（已去重）");
    }
    
    /**
     * 创建边的副本（使用新的source和target ID）
     */
    private Edge createEdge(Edge original, String newSourceId, String newTargetId) {
        try {
            // 使用反射创建新边实例
            Edge newEdge = original.getClass()
                    .getDeclaredConstructor(String.class, String.class)
                    .newInstance(newSourceId, newTargetId);
            
            // 复制属性
            for (Map.Entry<String, Object> entry : original.getProperties().entrySet()) {
                newEdge.setProperty(entry.getKey(), entry.getValue());
            }
            
            // 复制语义信息
            newEdge.setContextSnippet(original.getContextSnippet());
            newEdge.setDescription(original.getDescription());
            
            return newEdge;
        } catch (Exception e) {
            logger.error("创建边副本失败: " + original.getEdgeType(), e);
            return null;
        }
    }
    /**
     * 为版本节点生成唯一ID
     */
    private String createVersionedId(String baseId, String versionTag) {
        if (baseId == null || baseId.isEmpty()) {
            return UUID.randomUUID().toString() + "@" + versionTag;
        }
        String suffix = "@" + versionTag;
        if (baseId.endsWith(suffix)) {
            return baseId;
        }
        return baseId + suffix;
    }

    /**
     * 克隆节点（深拷贝）
     * 注意：Node的id是final的，所以我们创建新节点时会生成新ID
     */
    private Node cloneNode(Node node) {
        return cloneNode(node, null);
    }

    private Node cloneNode(Node node, String overrideId) {
        // 根据节点类型克隆
        if (node instanceof MethodNode) {
            MethodNode method = (MethodNode) node;
            String signature = method.getSignature();
            MethodNode cloned = overrideId != null
                    ? new MethodNode(overrideId, signature, method.getKind())
                    : new MethodNode(signature, method.getKind());
            copyAllProperties(method, cloned);
            return cloned;
        } else if (node instanceof FieldNode) {
            FieldNode field = (FieldNode) node;
            // FieldNode构造函数: (qualifiedName, fieldName, fieldType)
            String qualifiedName = (String) field.getProperty("qualifiedName");
            FieldNode cloned = overrideId != null
                    ? new FieldNode(overrideId, qualifiedName, field.getName(), field.getFieldType())
                    : new FieldNode(qualifiedName, field.getName(), field.getFieldType());
            copyAllProperties(field, cloned);
            return cloned;
        } else if (node instanceof TypeNode) {
            TypeNode type = (TypeNode) node;
            String qualifiedName = (String) type.getProperty("qualifiedName");
            TypeNode cloned = overrideId != null
                    ? new TypeNode(overrideId, qualifiedName, type.getKind())
                    : new TypeNode(qualifiedName, type.getKind());
            copyAllProperties(type, cloned);
            return cloned;
        } else if (node instanceof FileNode) {
            FileNode file = (FileNode) node;
            // FileNode构造函数: (absolutePath, relativePath, fileName)
            String absolutePath = file.getAbsolutePath();
            String relativePath = file.getRelativePath();
            String fileName = (String) file.getProperty("name");
            FileNode cloned = overrideId != null
                    ? new FileNode(overrideId, absolutePath, relativePath, fileName)
                    : new FileNode(absolutePath, relativePath, fileName);
            copyAllProperties(file, cloned);
            return cloned;
        } else if (node instanceof PackageNode) {
            PackageNode pkg = (PackageNode) node;
            String qualifiedName = (String) pkg.getProperty("qualifiedName");
            PackageNode cloned = overrideId != null
                    ? new PackageNode(overrideId, qualifiedName)
                    : new PackageNode(qualifiedName);
            copyAllProperties(pkg, cloned);
            return cloned;
        } else if (node instanceof ProjectNode) {
            ProjectNode proj = (ProjectNode) node;
            String projectName = (String) proj.getProperty("name");
            String version = (String) proj.getProperty("version");
            String groupId = (String) proj.getProperty("groupId");
            String artifactId = (String) proj.getProperty("artifactId");
            
            // 使用正确的构造函数
            ProjectNode cloned = new ProjectNode(projectName, version, groupId, artifactId);
            copyAllProperties(proj, cloned);
            return cloned;
        }
        
        // 默认返回原节点（不应该到这里）
        logger.warn("未知节点类型，无法克隆: " + node.getClass().getName());
        return node;
    }
    
    /**
     * 复制节点所有属性（包括语义信息和版本信息）
     */
    private void copyAllProperties(Node source, Node target) {
        // 复制properties map
        // 注意：getProperties() 返回副本，需要逐个设置属性
        for (Map.Entry<String, Object> entry : source.getProperties().entrySet()) {
            target.setProperty(entry.getKey(), entry.getValue());
        }
        target.setProperty("originalId", source.getId());
        
        // 复制语义信息
        target.setSourceCode(source.getSourceCode());
        target.setDocumentation(source.getDocumentation());
        target.setComments(source.getComments());
        target.setSemanticSummary(source.getSemanticSummary());
        
        // 复制位置信息
        target.setAbsolutePath(source.getAbsolutePath());
        target.setRelativePath(source.getRelativePath());
        
        // 版本信息由调用者单独设置，这里不复制
    }

    private void initializeVersionSnapshot(Node node, String versionLabel, VersionStatus status) {
        node.replaceVersions(Collections.singleton(versionLabel));
        node.setVersionStatus(status);
        node.setFirstVersion(versionLabel);
        node.setLastVersion(versionLabel);
        node.addVersion(versionLabel);
        applyVersionMetadata(node, versionLabel);
    }

    private void applyVersionMetadata(Node node, String versionLabel) {
        VersionMetadata metadata = versionMetadataRegistry.get(versionLabel);
        if (metadata == null) {
            return;
        }
        if (metadata.commitId != null && !metadata.commitId.isBlank()) {
            node.setProperty("commitId", metadata.commitId);
        }
        if (metadata.shortId != null && !metadata.shortId.isBlank()) {
            node.setProperty("commitShortId", metadata.shortId);
        }
        if (metadata.message != null && !metadata.message.isBlank()) {
            node.setProperty("commitMessage", metadata.message);
        }
        if (metadata.author != null && !metadata.author.isBlank()) {
            node.setProperty("commitAuthor", metadata.author);
        }
        node.setProperty("commitTime", metadata.commitTime);
    }

    private void markTracked(Node v1Node, Node v2Node) {
        if (v1Node != null) {
            trackedBaseNodeIds.add(normalizeBaseId(v1Node.getId()));
        }
        if (v2Node != null) {
            trackedBaseNodeIds.add(normalizeBaseId(v2Node.getId()));
        }
    }

    private boolean shouldTrack(Node node) {
        if (node == null) {
            return false;
        }
        return trackedBaseNodeIds.contains(normalizeBaseId(node.getId()));
    }

    private String normalizeBaseId(String nodeId) {
        if (nodeId == null) {
            return "";
        }
        int idx = nodeId.indexOf('@');
        return idx >= 0 ? nodeId.substring(0, idx) : nodeId;
    }
    
    /**
     * 重置统计信息
     */
    private void resetStatistics() {
        unchangedCount = 0;
        modifiedCount = 0;
        addedCount = 0;
        deletedCount = 0;
        evolutionEdgesCount = 0;
        structuralEdgesCount = 0;
    }
    
    /**
     * 打印统计信息
     */
    private void printStatistics(KnowledgeGraph mergedGraph) {
        logger.info("========== 图合并统计 ==========");
        
        // 统计实际节点状态
        int actualUnchanged = 0;
        int actualModified = 0;
        int actualAdded = 0;
        int actualDeleted = 0;
        
        for (Node node : mergedGraph.getAllNodes()) {
            VersionStatus status = node.getVersionStatus();
            if (status == VersionStatus.UNCHANGED) {
                actualUnchanged++;
            } else if (status == VersionStatus.MODIFIED) {
                actualModified++;
            } else if (status == VersionStatus.ADDED) {
                actualAdded++;
            } else if (status == VersionStatus.DELETED) {
                actualDeleted++;
            }
        }
        
        logger.info("节点统计:");
        logger.info("  UNCHANGED: " + actualUnchanged);
        logger.info("  MODIFIED:  " + actualModified);
        logger.info("  ADDED:     " + actualAdded);
        logger.info("  DELETED:   " + actualDeleted);
        logger.info("  总计:      " + mergedGraph.getAllNodes().size());
        logger.info("");
        logger.info("处理统计:");
        logger.info("  processUnchangedNode() 调用: " + unchangedCount + " 次");
        logger.info("  processModifiedNode() 调用:  " + modifiedCount + " 次");
        logger.info("  processAddedNode() 调用:     " + addedCount + " 次");
        logger.info("  processDeletedNode() 调用:   " + deletedCount + " 次");
        logger.info("边统计:");
        logger.info("  结构边:    " + structuralEdgesCount);
        logger.info("  演化边:    " + evolutionEdgesCount);
        logger.info("  总计:      " + mergedGraph.getAllEdges().size());
        logger.info("================================");
    }
}

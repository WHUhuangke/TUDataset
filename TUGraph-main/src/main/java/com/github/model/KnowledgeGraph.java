package com.github.model;

import com.github.model.nodes.*;
import com.github.model.edges.*;
import java.util.*;

/**
 * 知识图谱容器 - 存储和管理所有节点和边
 * 为LLM提供上下文检索功能
 */
public class KnowledgeGraph {
    
    private final Map<String, Node> nodes;
    private final Map<String, Edge> edges;
    private final Map<String, List<Edge>> outgoingEdges;  // nodeId -> 出边列表
    private final Map<String, List<Edge>> incomingEdges;  // nodeId -> 入边列表
    
    // ==================== 版本演化信息字段 (Phase 2) ====================
    // 以下字段用于支持版本演化分析，仅在 EVOLUTION 模式下使用
    
    /**
     * 源版本（Git commit hash）
     * 单版本模式下为 null
     */
    private String fromVersion;
    
    /**
     * 目标版本（Git commit hash）
     * 单版本模式下为 null
     */
    private String toVersion;
    
    /**
     * 演化边集合（RENAMED, MOVED, EXTRACTED 等）
     * 单版本模式下为空集合
     */
    private final Map<String, EvolutionEdge> evolutionEdges;
    private final Map<String, String> evolutionEdgeIndex;
    
    // ==================== 版本演化信息字段结束 ====================
    
    public KnowledgeGraph() {
        this.nodes = new HashMap<>();
        this.edges = new HashMap<>();
        this.outgoingEdges = new HashMap<>();
        this.incomingEdges = new HashMap<>();
        this.evolutionEdges = new HashMap<>();  // 初始化演化边集合
        this.evolutionEdgeIndex = new HashMap<>();
    }
    
    // ========== 节点操作 ==========
    
    public void addNode(Node node) {
        nodes.put(node.getId(), node);
        outgoingEdges.putIfAbsent(node.getId(), new ArrayList<>());
        incomingEdges.putIfAbsent(node.getId(), new ArrayList<>());
    }
    
    public Node getNode(String nodeId) {
        return nodes.get(nodeId);
    }
    
    public Collection<Node> getAllNodes() {
        return nodes.values();
    }
    
    public List<Node> getNodesByType(String nodeType) {
        List<Node> result = new ArrayList<>();
        for (Node node : nodes.values()) {
            if (node.getNodeType().equals(nodeType)) {
                result.add(node);
            }
        }
        return result;
    }
    
    // ========== 边操作 ==========
    
    public void addEdge(Edge edge) {
        edges.put(edge.getId(), edge);
        
        // 更新出边和入边索引
        outgoingEdges.computeIfAbsent(edge.getSourceId(), k -> new ArrayList<>()).add(edge);
        incomingEdges.computeIfAbsent(edge.getTargetId(), k -> new ArrayList<>()).add(edge);
    }
    
    public Edge getEdge(String edgeId) {
        return edges.get(edgeId);
    }
    
    public Collection<Edge> getAllEdges() {
        return edges.values();
    }
    
    public List<Edge> getOutgoingEdges(String nodeId) {
        return outgoingEdges.getOrDefault(nodeId, new ArrayList<>());
    }
    
    public List<Edge> getIncomingEdges(String nodeId) {
        return incomingEdges.getOrDefault(nodeId, new ArrayList<>());
    }
    
    public List<Edge> getEdgesByType(String edgeType) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edges.values()) {
            if (edge.getEdgeType().equals(edgeType)) {
                result.add(edge);
            }
        }
        return result;
    }
    
    // ========== 图遍历和查询 ==========
    
    /**
     * 获取节点的所有邻居（通过出边连接的节点）
     */
    public List<Node> getNeighbors(String nodeId) {
        List<Node> neighbors = new ArrayList<>();
        for (Edge edge : getOutgoingEdges(nodeId)) {
            Node neighbor = getNode(edge.getTargetId());
            if (neighbor != null) {
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }
    
    /**
     * 获取指定类型的邻居节点
     */
    public List<Node> getNeighborsByEdgeType(String nodeId, String edgeType) {
        List<Node> neighbors = new ArrayList<>();
        for (Edge edge : getOutgoingEdges(nodeId)) {
            if (edge.getEdgeType().equals(edgeType)) {
                Node neighbor = getNode(edge.getTargetId());
                if (neighbor != null) {
                    neighbors.add(neighbor);
                }
            }
        }
        return neighbors;
    }
    
    /**
     * 查找从source到target的所有路径（深度优先搜索，限制最大深度）
     */
    public List<List<Node>> findPaths(String sourceId, String targetId, int maxDepth) {
        List<List<Node>> paths = new ArrayList<>();
        List<Node> currentPath = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        Node sourceNode = getNode(sourceId);
        if (sourceNode != null) {
            currentPath.add(sourceNode);
            visited.add(sourceId);
            dfsPath(sourceId, targetId, currentPath, visited, paths, maxDepth, 0);
        }
        
        return paths;
    }
    
    private void dfsPath(String currentId, String targetId, List<Node> currentPath,
                        Set<String> visited, List<List<Node>> paths, int maxDepth, int depth) {
        if (depth > maxDepth) return;
        
        if (currentId.equals(targetId)) {
            paths.add(new ArrayList<>(currentPath));
            return;
        }
        
        for (Edge edge : getOutgoingEdges(currentId)) {
            String nextId = edge.getTargetId();
            if (!visited.contains(nextId)) {
                Node nextNode = getNode(nextId);
                if (nextNode != null) {
                    visited.add(nextId);
                    currentPath.add(nextNode);
                    dfsPath(nextId, targetId, currentPath, visited, paths, maxDepth, depth + 1);
                    currentPath.remove(currentPath.size() - 1);
                    visited.remove(nextId);
                }
            }
        }
    }
    
    // ========== LLM上下文生成 ==========
    
    /**
     * 为指定节点生成LLM上下文
     * 包括节点本身和其直接邻居的信息
     */
    public String generateContext(String nodeId, int neighborDepth) {
        StringBuilder context = new StringBuilder();
        Node node = getNode(nodeId);
        
        if (node == null) {
            return "Node not found: " + nodeId;
        }
        
        // 主节点信息
        context.append("========== MAIN NODE ==========\n");
        context.append(node.toContextString());
        context.append("\n");
        
        // 邻居信息
        if (neighborDepth > 0) {
            context.append("========== NEIGHBORS ==========\n");
            Set<String> visitedNeighbors = new HashSet<>();
            collectNeighborContext(nodeId, neighborDepth, 0, visitedNeighbors, context);
        }
        
        return context.toString();
    }
    
    private void collectNeighborContext(String nodeId, int maxDepth, int currentDepth,
                                       Set<String> visited, StringBuilder context) {
        if (currentDepth >= maxDepth) return;
        
        for (Edge edge : getOutgoingEdges(nodeId)) {
            String neighborId = edge.getTargetId();
            if (!visited.contains(neighborId)) {
                visited.add(neighborId);
                Node neighbor = getNode(neighborId);
                if (neighbor != null) {
                    context.append("\n--- ").append(edge.getEdgeType())
                           .append(" --> ").append(neighbor.getLabel()).append(" ---\n");
                    
                    if (edge.getDescription() != null) {
                        context.append("Relationship: ").append(edge.getDescription()).append("\n");
                    }
                    
                    if (neighbor.getSemanticSummary() != null) {
                        context.append(neighbor.getSemanticSummary()).append("\n");
                    }
                    
                    // 递归收集更深层邻居
                    collectNeighborContext(neighborId, maxDepth, currentDepth + 1, visited, context);
                }
            }
        }
    }
    
    /**
     * 为测试生成任务生成相关上下文
     * 包括类的所有方法、字段、依赖关系等
     */
    public String generateTestContext(String typeId) {
        StringBuilder context = new StringBuilder();
        TypeNode typeNode = (TypeNode) getNode(typeId);
        
        if (typeNode == null) {
            return "Type not found: " + typeId;
        }
        
        context.append("========== TEST CONTEXT FOR: ").append(typeNode.getLabel()).append(" ==========\n\n");
        
        // 1. 类的完整信息
        context.append("=== CLASS DEFINITION ===\n");
        context.append(typeNode.toContextString()).append("\n");
        
        // 2. 继承和实现关系
        context.append("=== INHERITANCE ===\n");
        for (Edge edge : getOutgoingEdges(typeId)) {
            if (edge.getEdgeType().equals("EXTENDS") || edge.getEdgeType().equals("IMPLEMENTS")) {
                Node relatedType = getNode(edge.getTargetId());
                if (relatedType != null) {
                    context.append(edge.getEdgeType()).append(": ").append(relatedType.getLabel()).append("\n");
                    if (relatedType.getSemanticSummary() != null) {
                        context.append("  ").append(relatedType.getSemanticSummary()).append("\n");
                    }
                }
            }
        }
        context.append("\n");
        
        // 3. 字段信息
        context.append("=== FIELDS ===\n");
        for (Edge edge : getOutgoingEdges(typeId)) {
            if (edge.getEdgeType().equals("DECLARES") && edge.getProperty("memberKind").equals("field")) {
                Node field = getNode(edge.getTargetId());
                if (field != null) {
                    context.append(field.getSemanticSummary()).append("\n");
                }
            }
        }
        context.append("\n");
        
        // 4. 方法信息
        context.append("=== METHODS ===\n");
        for (Edge edge : getOutgoingEdges(typeId)) {
            if (edge.getEdgeType().equals("DECLARES") && 
                (edge.getProperty("memberKind").equals("method") || 
                 edge.getProperty("memberKind").equals("constructor"))) {
                MethodNode method = (MethodNode) getNode(edge.getTargetId());
                if (method != null) {
                    context.append("\n--- Method: ").append(method.getProperty("name")).append(" ---\n");
                    context.append(method.toContextString());
                    
                    // 方法调用的其他方法
                    context.append("  Calls: ");
                    List<String> calledMethods = new ArrayList<>();
                    for (Edge callEdge : getOutgoingEdges(method.getId())) {
                        if (callEdge.getEdgeType().equals("CALLS")) {
                            Node calledMethod = getNode(callEdge.getTargetId());
                            if (calledMethod != null) {
                                calledMethods.add(calledMethod.getLabel());
                            }
                        }
                    }
                    context.append(calledMethods.isEmpty() ? "none" : String.join(", ", calledMethods));
                    context.append("\n");
                }
            }
        }
        
        return context.toString();
    }
    
    /**
     * 生成整个项目的摘要，用于LLM快速理解项目结构
     */
    public String generateProjectSummary() {
        StringBuilder summary = new StringBuilder();
        
        // 项目信息
        List<Node> projects = getNodesByType("PROJECT");
        if (!projects.isEmpty()) {
            ProjectNode project = (ProjectNode) projects.get(0);
            summary.append("========== PROJECT: ").append(project.getName()).append(" ==========\n");
            summary.append("Version: ").append(project.getVersion()).append("\n\n");
        }
        
        // 统计信息
        summary.append("=== STATISTICS ===\n");
        summary.append("Total Packages: ").append(getNodesByType("PACKAGE").size()).append("\n");
        summary.append("Total Files: ").append(getNodesByType("FILE").size()).append("\n");
        summary.append("Total Types: ").append(getNodesByType("TYPE").size()).append("\n");
        summary.append("Total Methods: ").append(getNodesByType("METHOD").size()).append("\n");
        summary.append("Total Fields: ").append(getNodesByType("FIELD").size()).append("\n\n");
        
        // 包结构
        summary.append("=== PACKAGE STRUCTURE ===\n");
        for (Node packageNode : getNodesByType("PACKAGE")) {
            PackageNode pkg = (PackageNode) packageNode;
            summary.append(pkg.getQualifiedName());
            summary.append(" (").append(pkg.getProperty("typeCount")).append(" types)\n");
        }
        
        return summary.toString();
    }
    
    // ========== 统计信息 ==========
    
    public int getNodeCount() {
        return nodes.size();
    }
    
    public int getEdgeCount() {
        return edges.size();
    }
    
    public Map<String, Integer> getNodeStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        for (Node node : nodes.values()) {
            String type = node.getNodeType();
            stats.put(type, stats.getOrDefault(type, 0) + 1);
        }
        return stats;
    }
    
    public Map<String, Integer> getEdgeStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        for (Edge edge : edges.values()) {
            String type = edge.getEdgeType();
            stats.put(type, stats.getOrDefault(type, 0) + 1);
        }
        return stats;
    }
    
    // ==================== 版本演化相关方法 (Phase 2) ====================
    
    /**
     * 获取源版本
     */
    public String getFromVersion() {
        return fromVersion;
    }
    
    /**
     * 设置源版本
     */
    public void setFromVersion(String fromVersion) {
        this.fromVersion = fromVersion;
    }
    
    /**
     * 获取目标版本
     */
    public String getToVersion() {
        return toVersion;
    }
    
    /**
     * 设置目标版本
     */
    public void setToVersion(String toVersion) {
        this.toVersion = toVersion;
    }
    
    /**
     * 添加演化边
     */
    public void addEvolutionEdge(EvolutionEdge edge) {
        String key = buildEvolutionEdgeKey(edge);
        if (key != null) {
            String existingId = evolutionEdgeIndex.get(key);
            if (existingId != null) {
                EvolutionEdge existing = evolutionEdges.get(existingId);
                if (existing != null) {
                    mergeEvolutionEdge(existing, edge);
                    return;
                }
            }
        }
        
        initializeEvolutionEdgeAggregation(edge);
        evolutionEdges.put(edge.getId(), edge);
        if (key != null) {
            evolutionEdgeIndex.put(key, edge.getId());
        }
        // 演化边也是普通边，添加到边集合中
        addEdge(edge);
    }
    
    private String buildEvolutionEdgeKey(EvolutionEdge edge) {
        if (edge == null) {
            return null;
        }
        String sourceId = edge.getSourceId();
        String targetId = edge.getTargetId();
        String edgeType = edge.getEdgeType();
        
        if (sourceId == null || targetId == null || edgeType == null) {
            return null;
        }
        return sourceId + "|" + edgeType + "|" + targetId;
    }
    
    private void initializeEvolutionEdgeAggregation(EvolutionEdge edge) {
        edge.setProperty("occurrences", 1);
        
        List<String> descriptionList = new ArrayList<>();
        if (edge.getDescription() != null && !edge.getDescription().isBlank()) {
            descriptionList.add(edge.getDescription());
        }
        edge.setProperty("descriptions", descriptionList);
        
        List<String> refactoringTypes = ensureStringListProperty(edge, "refactoringTypes");
        String refType = edge.getRefactoringType();
        if (refType != null) {
            String trimmed = refType.trim();
            if (!trimmed.isEmpty() && !refactoringTypes.contains(trimmed)) {
                refactoringTypes.add(trimmed);
            }
        }
        
        // 确保列表属性是可变列表
        ensureStringListProperty(edge, "leftLocations");
        ensureStringListProperty(edge, "rightLocations");
        ensureStringListProperty(edge, "leftElements");
        ensureStringListProperty(edge, "rightElements");
    }
    
    private void mergeEvolutionEdge(EvolutionEdge target, EvolutionEdge incoming) {
        int occurrences = 1;
        Object count = target.getProperty("occurrences");
        if (count instanceof Number) {
            occurrences = ((Number) count).intValue();
        }
        target.setProperty("occurrences", occurrences + 1);
        
        if (incoming.getConfidence() > target.getConfidence()) {
            target.setConfidence(incoming.getConfidence());
        }
        
        mergeDescriptions(target, incoming);
        mergeStringListProperty(target, incoming, "leftLocations");
        mergeStringListProperty(target, incoming, "rightLocations");
        mergeStringListProperty(target, incoming, "leftElements");
        mergeStringListProperty(target, incoming, "rightElements");
        mergeStringListProperty(target, incoming, "refactoringTypes");
        
        String incomingType = incoming.getRefactoringType();
        if (incomingType != null && !incomingType.isBlank()) {
            List<String> types = ensureStringListProperty(target, "refactoringTypes");
            String trimmed = incomingType.trim();
            if (!trimmed.isEmpty() && !types.contains(trimmed)) {
                types.add(trimmed);
            }
        }
    }
    
    private void mergeDescriptions(EvolutionEdge target, EvolutionEdge incoming) {
        List<String> descriptions = ensureStringListProperty(target, "descriptions");
        if (target.getDescription() != null && !target.getDescription().isBlank() && descriptions.isEmpty()) {
            descriptions.add(target.getDescription());
        }
        
        if (incoming.getDescription() != null && !incoming.getDescription().isBlank() &&
                !descriptions.contains(incoming.getDescription())) {
            descriptions.add(incoming.getDescription());
        }
        
        Object incomingListObj = incoming.getProperty("descriptions");
        if (incomingListObj instanceof List) {
            mergeStringList(descriptions, extractStringList(incomingListObj));
        }
    }
    
    private void mergeStringListProperty(EvolutionEdge target, EvolutionEdge incoming, String propertyName) {
        List<String> targetList = ensureStringListProperty(target, propertyName);
        Object incomingValue = incoming.getProperty(propertyName);
        List<String> incomingList = extractStringList(incomingValue);
        mergeStringList(targetList, incomingList);
    }
    
    private void mergeStringList(List<String> targetList, List<String> incomingList) {
        if (incomingList.isEmpty()) {
            return;
        }
        Set<String> set = new LinkedHashSet<>(targetList);
        for (String value : incomingList) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty() && set.add(trimmed)) {
                    targetList.add(trimmed);
                }
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<String> ensureStringListProperty(EvolutionEdge edge, String propertyName) {
        Object value = edge.getProperty(propertyName);
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> mutable = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    String val = item.toString().trim();
                    if (!val.isEmpty()) {
                        mutable.add(val);
                    }
                }
            }
            edge.setProperty(propertyName, mutable);
            return mutable;
        }
        List<String> newList = new ArrayList<>();
        edge.setProperty(propertyName, newList);
        return newList;
    }
    
    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null) {
                    String val = item.toString().trim();
                    if (!val.isEmpty()) {
                        result.add(val);
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * 获取演化边
     */
    public EvolutionEdge getEvolutionEdge(String edgeId) {
        return evolutionEdges.get(edgeId);
    }
    
    /**
     * 获取所有演化边
     */
    public Collection<EvolutionEdge> getAllEvolutionEdges() {
        return evolutionEdges.values();
    }
    
    /**
     * 获取指定类型的演化边
     */
    public List<EvolutionEdge> getEvolutionEdgesByType(String edgeType) {
        List<EvolutionEdge> result = new ArrayList<>();
        for (EvolutionEdge edge : evolutionEdges.values()) {
            if (edge.getEdgeType().equals(edgeType)) {
                result.add(edge);
            }
        }
        return result;
    }
    
    /**
     * 获取指定版本状态的节点
     */
    public List<Node> getNodesByVersionStatus(VersionStatus status) {
        List<Node> result = new ArrayList<>();
        for (Node node : nodes.values()) {
            if (node.getVersionStatus() == status) {
                result.add(node);
            }
        }
        return result;
    }
    
    /**
     * 获取在指定版本中存在的节点
     */
    public List<Node> getNodesInVersion(String version) {
        List<Node> result = new ArrayList<>();
        for (Node node : nodes.values()) {
            if (node.existsInVersion(version)) {
                result.add(node);
            }
        }
        return result;
    }
    
    /**
     * 判断是否为演化图谱
     */
    public boolean isEvolutionGraph() {
        return fromVersion != null || toVersion != null || !evolutionEdges.isEmpty();
    }
    
    /**
     * 获取演化统计信息
     */
    public Map<String, Integer> getEvolutionStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        
        // 按版本状态统计节点
        for (VersionStatus status : VersionStatus.values()) {
            int count = getNodesByVersionStatus(status).size();
            stats.put("nodes_" + status.name(), count);
        }
        
        // 按类型统计演化边
        for (EvolutionEdge edge : evolutionEdges.values()) {
            String key = "evolution_edges_" + edge.getEdgeType();
            stats.put(key, stats.getOrDefault(key, 0) + 1);
        }
        
        return stats;
    }
    
    // ==================== 版本演化相关方法结束 ====================
}

package com.github.evolution;

import com.github.model.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 节点映射 - 存储两个版本之间的节点对应关系
 * 
 * <p>记录版本 V1 的节点与版本 V2 的节点之间的映射关系，
 * 以及映射的置信度（1.0 表示精确匹配，< 1.0 表示相似度匹配）。
 * 
 * <p><b>使用方式：</b>
 * <pre>{@code
 * NodeMapping mapping = new NodeMapping();
 * mapping.addMapping(v1NodeId, v2NodeId, 1.0);  // 精确匹配
 * mapping.addMapping(v1NodeId2, v2NodeId2, 0.85);  // 相似度匹配
 * 
 * String mappedId = mapping.getMappedNode(v1NodeId);
 * double confidence = mapping.getConfidence(v1NodeId);
 * }</pre>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class NodeMapping {
    
    /**
     * V1 节点 ID → V2 节点 ID 的映射
     */
    private final Map<String, String> v1ToV2Map;
    
    /**
     * V2 节点 ID → V1 节点 ID 的反向映射
     */
    private final Map<String, String> v2ToV1Map;
    
    /**
     * 映射置信度（V1 节点 ID → 置信度）
     * 1.0 表示精确匹配，< 1.0 表示基于相似度的推测匹配
     */
    private final Map<String, Double> confidenceMap;
    
    /**
     * V1 节点对象缓存（可选，用于快速访问）
     */
    private final Map<String, Node> v1Nodes;
    
    /**
     * V2 节点对象缓存（可选，用于快速访问）
     */
    private final Map<String, Node> v2Nodes;
    
    public NodeMapping() {
        this.v1ToV2Map = new HashMap<>();
        this.v2ToV1Map = new HashMap<>();
        this.confidenceMap = new HashMap<>();
        this.v1Nodes = new HashMap<>();
        this.v2Nodes = new HashMap<>();
    }
    
    /**
     * 添加节点映射
     * 
     * @param v1NodeId V1 版本的节点 ID
     * @param v2NodeId V2 版本的节点 ID
     * @param confidence 映射置信度（0.0 - 1.0）
     */
    public void addMapping(String v1NodeId, String v2NodeId, double confidence) {
        if (v1NodeId == null || v2NodeId == null) {
            throw new IllegalArgumentException("节点 ID 不能为 null");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("置信度必须在 0.0 - 1.0 之间");
        }
        
        v1ToV2Map.put(v1NodeId, v2NodeId);
        v2ToV1Map.put(v2NodeId, v1NodeId);
        confidenceMap.put(v1NodeId, confidence);
    }
    
    /**
     * 添加节点映射（带节点对象缓存）
     * 
     * @param v1Node V1 版本的节点
     * @param v2Node V2 版本的节点
     * @param confidence 映射置信度
     */
    public void addMapping(Node v1Node, Node v2Node, double confidence) {
        addMapping(v1Node.getId(), v2Node.getId(), confidence);
        v1Nodes.put(v1Node.getId(), v1Node);
        v2Nodes.put(v2Node.getId(), v2Node);
    }
    
    /**
     * 获取 V1 节点映射到的 V2 节点 ID
     * 
     * @param v1NodeId V1 节点 ID
     * @return V2 节点 ID，如果没有映射则返回 null
     */
    public String getMappedNode(String v1NodeId) {
        return v1ToV2Map.get(v1NodeId);
    }
    
    /**
     * 获取 V2 节点映射到的 V1 节点 ID（反向查询）
     * 
     * @param v2NodeId V2 节点 ID
     * @return V1 节点 ID，如果没有映射则返回 null
     */
    public String getReverseMappedNode(String v2NodeId) {
        return v2ToV1Map.get(v2NodeId);
    }
    
    /**
     * 获取映射置信度
     * 
     * @param v1NodeId V1 节点 ID
     * @return 置信度，如果没有映射则返回 0.0
     */
    public double getConfidence(String v1NodeId) {
        return confidenceMap.getOrDefault(v1NodeId, 0.0);
    }
    
    /**
     * 检查 V1 节点是否有映射
     * 
     * @param v1NodeId V1 节点 ID
     * @return 是否有映射
     */
    public boolean hasMappingForV1(String v1NodeId) {
        return v1ToV2Map.containsKey(v1NodeId);
    }
    
    /**
     * 检查 V2 节点是否有映射
     * 
     * @param v2NodeId V2 节点 ID
     * @return 是否有映射
     */
    public boolean hasMappingForV2(String v2NodeId) {
        return v2ToV1Map.containsKey(v2NodeId);
    }
    
    /**
     * 获取所有映射关系
     * 
     * @return V1 节点 ID → V2 节点 ID 的映射
     */
    public Map<String, String> getAllMappings() {
        return new HashMap<>(v1ToV2Map);
    }
    
    /**
     * 获取所有 V1 节点 ID
     * 
     * @return V1 节点 ID 集合
     */
    public Set<String> getV1NodeIds() {
        return v1ToV2Map.keySet();
    }
    
    /**
     * 获取所有 V2 节点 ID
     * 
     * @return V2 节点 ID 集合
     */
    public Set<String> getV2NodeIds() {
        return v2ToV1Map.keySet();
    }
    
    /**
     * 获取映射总数
     * 
     * @return 映射数量
     */
    public int size() {
        return v1ToV2Map.size();
    }
    
    /**
     * 判断是否为空
     * 
     * @return 是否没有任何映射
     */
    public boolean isEmpty() {
        return v1ToV2Map.isEmpty();
    }
    
    /**
     * 获取缓存的 V1 节点
     * 
     * @param v1NodeId V1 节点 ID
     * @return V1 节点对象，如果未缓存则返回 null
     */
    public Node getV1Node(String v1NodeId) {
        return v1Nodes.get(v1NodeId);
    }
    
    /**
     * 获取缓存的 V2 节点
     * 
     * @param v2NodeId V2 节点 ID
     * @return V2 节点对象，如果未缓存则返回 null
     */
    public Node getV2Node(String v2NodeId) {
        return v2Nodes.get(v2NodeId);
    }
    
    /**
     * 获取高置信度映射（>= 0.8）的数量
     * 
     * @return 高置信度映射数量
     */
    public int getHighConfidenceMappingCount() {
        return (int) confidenceMap.values().stream()
                .filter(conf -> conf >= 0.8)
                .count();
    }
    
    /**
     * 获取统计信息
     * 
     * @return 统计信息字符串
     */
    public String getStatistics() {
        int total = size();
        int exactMatch = (int) confidenceMap.values().stream()
                .filter(conf -> conf == 1.0)
                .count();
        int highConfidence = getHighConfidenceMappingCount();
        int lowConfidence = total - highConfidence;
        
        return String.format(
                "NodeMapping Statistics:\n" +
                "  Total mappings: %d\n" +
                "  Exact matches (1.0): %d\n" +
                "  High confidence (>= 0.8): %d\n" +
                "  Low confidence (< 0.8): %d",
                total, exactMatch, highConfidence, lowConfidence
        );
    }
    
    @Override
    public String toString() {
        return "NodeMapping{" +
                "mappings=" + v1ToV2Map.size() +
                ", avgConfidence=" + String.format("%.2f", 
                    confidenceMap.values().stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0)) +
                '}';
    }
}

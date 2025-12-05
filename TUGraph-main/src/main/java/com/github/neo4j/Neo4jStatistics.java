package com.github.neo4j;

import com.github.logging.GraphLogger;

import java.util.HashMap;
import java.util.Map;

/**
 * Neo4j 统计信息
 * 记录和展示写入到 Neo4j 的节点和边的统计信息
 */
public class Neo4jStatistics {
    
    private static final GraphLogger logger = GraphLogger.getInstance();
    
    // 节点统计 (按类型分类)
    private final Map<String, Integer> nodeTypeCount;
    
    // 边统计 (按类型分类)
    private final Map<String, Integer> edgeTypeCount;
    
    // 总计
    private int totalNodes;
    private int totalEdges;
    
    // 时间统计
    private long startTime;
    private long endTime;
    
    public Neo4jStatistics() {
        this.nodeTypeCount = new HashMap<>();
        this.edgeTypeCount = new HashMap<>();
        this.totalNodes = 0;
        this.totalEdges = 0;
        this.startTime = 0;
        this.endTime = 0;
    }
    
    /**
     * 开始计时
     */
    public void start() {
        this.startTime = System.currentTimeMillis();
        logger.info("开始 Neo4j 写入统计");
    }
    
    /**
     * 结束计时
     */
    public void end() {
        this.endTime = System.currentTimeMillis();
    }
    
    /**
     * 记录节点
     */
    public void recordNode(String nodeType) {
        nodeTypeCount.put(nodeType, nodeTypeCount.getOrDefault(nodeType, 0) + 1);
        totalNodes++;
    }
    
    /**
     * 记录边
     */
    public void recordEdge(String edgeType) {
        edgeTypeCount.put(edgeType, edgeTypeCount.getOrDefault(edgeType, 0) + 1);
        totalEdges++;
    }
    
    /**
     * 批量设置节点统计
     */
    public void setNodeStats(Map<String, Integer> stats) {
        this.nodeTypeCount.clear();
        this.nodeTypeCount.putAll(stats);
        this.totalNodes = stats.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * 批量设置边统计
     */
    public void setEdgeStats(Map<String, Integer> stats) {
        this.edgeTypeCount.clear();
        this.edgeTypeCount.putAll(stats);
        this.totalEdges = stats.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * 获取执行时间(毫秒)
     */
    public long getElapsedTimeMs() {
        if (startTime == 0 || endTime == 0) {
            return 0;
        }
        return endTime - startTime;
    }
    
    /**
     * 获取执行时间(秒)
     */
    public double getElapsedTimeSec() {
        return getElapsedTimeMs() / 1000.0;
    }
    
    /**
     * 获取节点总数
     */
    public int getTotalNodes() {
        return totalNodes;
    }
    
    /**
     * 获取边总数
     */
    public int getTotalEdges() {
        return totalEdges;
    }
    
    /**
     * 获取节点类型统计
     */
    public Map<String, Integer> getNodeTypeCount() {
        return new HashMap<>(nodeTypeCount);
    }
    
    /**
     * 获取边类型统计
     */
    public Map<String, Integer> getEdgeTypeCount() {
        return new HashMap<>(edgeTypeCount);
    }
    
    /**
     * 打印统计信息
     */
    public void printStatistics() {
        logger.info("===============================================");
        logger.info("           Neo4j 写入统计报告");
        logger.info("===============================================");
        
        // 节点统计
        logger.info(String.format("节点总数: %d", totalNodes));
        logger.info("节点类型分布:");
        nodeTypeCount.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .forEach(entry -> {
                logger.info(String.format("  - %s: %d (%.1f%%)", 
                    entry.getKey(), 
                    entry.getValue(),
                    100.0 * entry.getValue() / totalNodes));
            });
        
        logger.info("");
        
        // 边统计
        logger.info(String.format("边总数: %d", totalEdges));
        logger.info("边类型分布:");
        edgeTypeCount.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .forEach(entry -> {
                logger.info(String.format("  - %s: %d (%.1f%%)", 
                    entry.getKey(), 
                    entry.getValue(),
                    100.0 * entry.getValue() / totalEdges));
            });
        
        logger.info("");
        
        // 时间统计
        if (getElapsedTimeMs() > 0) {
            logger.info(String.format("执行时间: %.2f 秒", getElapsedTimeSec()));
            logger.info(String.format("节点写入速度: %.0f 节点/秒", totalNodes / getElapsedTimeSec()));
            logger.info(String.format("边写入速度: %.0f 边/秒", totalEdges / getElapsedTimeSec()));
        }
        
        logger.info("===============================================");
    }
    
    /**
     * 生成摘要字符串
     */
    public String getSummary() {
        return String.format("Neo4j 写入完成: %d 个节点, %d 条边 (耗时: %.2f 秒)", 
            totalNodes, totalEdges, getElapsedTimeSec());
    }
}

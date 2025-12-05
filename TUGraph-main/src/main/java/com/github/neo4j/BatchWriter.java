package com.github.neo4j;

import com.github.logging.GraphLogger;
import com.github.model.Edge;
import com.github.model.Node;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 批量写入器
 * 负责将节点和边批量写入 Neo4j 数据库
 */
public class BatchWriter {
    
    private static final GraphLogger logger = GraphLogger.getInstance();
    
    private final Session session;
    private final int batchSize;
    
    private final List<Node> nodeBatch;
    private final List<Edge> edgeBatch;
    
    private int totalNodesWritten;
    private int totalEdgesWritten;
    
    /**
     * 构造函数
     * @param session Neo4j 会话
     * @param batchSize 批处理大小
     */
    public BatchWriter(Session session, int batchSize) {
        this.session = session;
        this.batchSize = batchSize;
        this.nodeBatch = new ArrayList<>(batchSize);
        this.edgeBatch = new ArrayList<>(batchSize);
        this.totalNodesWritten = 0;
        this.totalEdgesWritten = 0;
    }
    
    /**
     * 添加节点到批处理队列
     */
    public void addNode(Node node) {
        nodeBatch.add(node);
        if (nodeBatch.size() >= batchSize) {
            flushNodes();
        }
    }
    
    /**
     * 添加边到批处理队列
     */
    public void addEdge(Edge edge) {
        edgeBatch.add(edge);
        if (edgeBatch.size() >= batchSize) {
            flushEdges();
        }
    }
    
    /**
     * 刷新所有待写入的节点和边
     */
    public void flush() {
        flushNodes();
        flushEdges();
        logger.info(String.format("批量写入完成: 共写入 %d 个节点, %d 条边", 
            totalNodesWritten, totalEdgesWritten));
    }
    
    /**
     * 刷新节点批次（公开方法，用于确保所有节点写入完成）
     */
    public void flushNodes() {
        if (nodeBatch.isEmpty()) {
            return;
        }
        
        try {
            session.executeWrite(tx -> {
                for (Node node : nodeBatch) {
                    writeNode(tx, node);
                }
                return null;
            });
            
            int batchCount = nodeBatch.size();
            totalNodesWritten += batchCount;
            logger.debug(String.format("写入节点批次: %d 个节点 (总计: %d)", 
                batchCount, totalNodesWritten));
            nodeBatch.clear();
            
        } catch (Exception e) {
            logger.error("写入节点批次失败: " + e.getMessage());
            throw new RuntimeException("Failed to write node batch", e);
        }
    }
    
    /**
     * 刷新边批次（公开方法，用于确保所有边写入完成）
     */
    public void flushEdges() {
        if (edgeBatch.isEmpty()) {
            return;
        }
        
        try {
            session.executeWrite(tx -> {
                for (Edge edge : edgeBatch) {
                    writeEdge(tx, edge);
                }
                return null;
            });
            
            int batchCount = edgeBatch.size();
            totalEdgesWritten += batchCount;
            logger.debug(String.format("写入边批次: %d 条边 (总计: %d)", 
                batchCount, totalEdgesWritten));
            edgeBatch.clear();
            
        } catch (Exception e) {
            logger.error("写入边批次失败: " + e.getMessage());
            throw new RuntimeException("Failed to write edge batch", e);
        }
    }
    
    /**
     * 写入单个节点
     */
    private void writeNode(TransactionContext tx, Node node) {
        try {
            String cypherQuery = CypherQueryBuilder.buildCreateNodeQuery(node);
            Map<String, Object> params = CypherQueryBuilder.nodeToParameters(node);
            tx.run(cypherQuery, params);
        } catch (Exception e) {
            logger.error(String.format("写入节点失败: %s [%s]", node.getId(), e.getMessage()));
            throw e;
        }
    }
    
    /**
     * 写入单个边
     */
    private void writeEdge(TransactionContext tx, Edge edge) {
        try {
            String cypherQuery = CypherQueryBuilder.buildCreateEdgeQuery(edge);
            Map<String, Object> params = CypherQueryBuilder.edgeToParameters(edge);
            tx.run(cypherQuery, params);
        } catch (Exception e) {
            logger.error(String.format("写入边失败: %s -> %s [%s]", 
                edge.getSourceId(), edge.getTargetId(), e.getMessage()));
            throw e;
        }
    }
    
    /**
     * 获取已写入的节点总数
     */
    public int getTotalNodesWritten() {
        return totalNodesWritten;
    }
    
    /**
     * 获取已写入的边总数
     */
    public int getTotalEdgesWritten() {
        return totalEdgesWritten;
    }
}

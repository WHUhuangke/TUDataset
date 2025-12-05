package com.github.neo4j;

import com.github.logging.GraphLogger;
import com.github.logging.ProgressTracker;
import com.github.model.Edge;
import com.github.model.KnowledgeGraph;
import com.github.model.Node;
import org.neo4j.driver.Session;

import java.util.Map;

/**
 * Neo4j 图谱写入器
 * 负责将知识图谱写入到 Neo4j 数据库
 */
public class Neo4jGraphWriter {
    
    private static final GraphLogger logger = GraphLogger.getInstance();
    
    private final Neo4jConfig config;
    private final Neo4jConnection connection;
    
    public Neo4jGraphWriter(Neo4jConfig config) {
        this.config = config;
        this.connection = Neo4jConnection.getInstance();
    }
    
    /**
     * 使用默认配置构造
     */
    public Neo4jGraphWriter() {
        this(new Neo4jConfig());
    }
    
    /**
     * 测试 Neo4j 连接
     * @return 连接状态信息
     */
    public Neo4jConnection.ConnectionStatus testConnection() {
        logger.info("测试 Neo4j 连接...");
        
        try {
            connection.initialize(config);
            Neo4jConnection.ConnectionStatus status = connection.testConnection();
            
            if (status.isSuccess()) {
                logger.info("✓ Neo4j 连接成功");
                logger.info("  - 服务器版本: " + status.getVersion());
                logger.info("  - 数据库版本: " + status.getEdition());
            } else {
                logger.error("✗ Neo4j 连接失败: " + status.getErrorMessage());
            }
            
            return status;
            
        } catch (Exception e) {
            logger.error("测试连接时发生异常: " + e.getMessage());
            return Neo4jConnection.ConnectionStatus.failure(e.getMessage());
        }
    }
    
    /**
     * 重置数据库 (删除所有节点和边)
     * @return 是否成功
     */
    public boolean resetDatabase() {
        logger.info("重置 Neo4j 数据库...");
        
        try {
            connection.initialize(config);
            
            try (Session session = connection.createSession()) {
                // 删除所有节点和边
                session.executeWrite(tx -> {
                    tx.run("MATCH (n) DETACH DELETE n");
                    return null;
                });
                
                logger.info("✓ 数据库重置完成");
                return true;
            }
            
        } catch (Exception e) {
            logger.error("重置数据库失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 写入知识图谱到 Neo4j
     * @param graph 知识图谱
     * @return 写入统计信息
     */
    public Neo4jStatistics writeGraph(KnowledgeGraph graph) {
        logger.info("开始写入知识图谱到 Neo4j...");
        
        Neo4jStatistics statistics = new Neo4jStatistics();
        statistics.start();
        
        try {
            connection.initialize(config);
            
            // 创建索引以提高查询性能
            createIndexes();
            
            try (Session session = connection.createSession()) {
                BatchWriter writer = new BatchWriter(session, config.getBatchSize());
                
                // 阶段 1: 先完整写入所有节点
                logger.info("阶段 1: 写入所有节点");
                writeNodes(graph, writer, statistics);
                writer.flushNodes();  // 确保所有节点都已写入
                logger.info("✓ 所有节点写入完成");
                
                // 阶段 2: 再写入所有边
                logger.info("阶段 2: 写入所有边");
                writeEdges(graph, writer, statistics);
                writer.flushEdges();  // 确保所有边都已写入
                logger.info("✓ 所有边写入完成");
            }
            
            statistics.end();
            logger.info("✓ 知识图谱写入完成");
            
            // 打印统计信息
            statistics.printStatistics();
            
            return statistics;
            
        } catch (Exception e) {
            statistics.end();
            logger.error("写入知识图谱失败: " + e.getMessage());
            throw new RuntimeException("Failed to write graph to Neo4j", e);
        }
    }
    
    /**
     * 创建索引
     */
    private void createIndexes() {
        logger.info("创建 Neo4j 索引...");
        
        try (Session session = connection.createSession()) {
            // 为所有节点类型的 id 属性创建索引
            // Parameter 和 Annotation 已移除
            String[] nodeLabels = {
                "Project", "Package", "File", "Type", 
                "Method", "Field"
            };
            
            for (String label : nodeLabels) {
                try {
                    session.executeWrite(tx -> {
                        String query = String.format(
                            "CREATE INDEX IF NOT EXISTS FOR (n:%s) ON (n.id)", label);
                        tx.run(query);
                        return null;
                    });
                } catch (Exception e) {
                    logger.warn(String.format("创建索引失败 [%s]: %s", label, e.getMessage()));
                }
            }
            
            logger.info("✓ 索引创建完成");
            
        } catch (Exception e) {
            logger.warn("创建索引时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 写入节点
     */
    private void writeNodes(KnowledgeGraph graph, BatchWriter writer, Neo4jStatistics statistics) {
        logger.info("写入节点...");
        
        ProgressTracker tracker = new ProgressTracker();
        tracker.startPhase("写入节点", graph.getNodeCount());
        
        Map<String, Integer> nodeStats = graph.getNodeStatistics();
        statistics.setNodeStats(nodeStats);
        
        int count = 0;
        for (Node node : graph.getAllNodes()) {
            writer.addNode(node);
            count++;
            
            if (count % 1000 == 0) {
                tracker.updateProgress(count);
            }
        }
        
        tracker.endPhase();
        logger.info(String.format("✓ 节点写入队列完成: %d 个节点", count));
    }
    
    /**
     * 写入边
     */
    private void writeEdges(KnowledgeGraph graph, BatchWriter writer, Neo4jStatistics statistics) {
        logger.info("写入边...");
        
        ProgressTracker tracker = new ProgressTracker();
        tracker.startPhase("写入边", graph.getEdgeCount());
        
        Map<String, Integer> edgeStats = graph.getEdgeStatistics();
        statistics.setEdgeStats(edgeStats);
        
        int count = 0;
        for (Edge edge : graph.getAllEdges()) {
            writer.addEdge(edge);
            count++;
            
            if (count % 1000 == 0) {
                tracker.updateProgress(count);
            }
        }
        
        tracker.endPhase();
        logger.info(String.format("✓ 边写入队列完成: %d 条边", count));
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        connection.close();
        logger.info("Neo4j 连接已关闭");
    }
}

package com.github.pipeline;

import com.github.config.AppConfig;
import com.github.logging.GraphLogger;
import com.github.model.KnowledgeGraph;
import com.github.neo4j.Neo4jBulkCsvExporter;
import com.github.neo4j.Neo4jBulkImporter;
import com.github.neo4j.Neo4jServiceManager;
import com.github.spoon.ProjectAnalyzer;

/**
 * 单版本分析流程
 * 这是原有的分析流程，用于构建单个版本的知识图谱
 * 
 * 流程步骤:
 * 1. 使用 Spoon 解析项目
 * 2. 导出为 Neo4j bulk import 格式的 CSV
 * 3. 自动停止 Neo4j 服务
 * 4. 使用 neo4j-admin import 命令导入
 * 5. 自动启动 Neo4j 服务
 */
public class SingleVersionPipeline implements Pipeline {
    
    private final GraphLogger logger = GraphLogger.getInstance();
    
    @Override
    public String getName() {
        return "Single Version Analysis";
    }
    
    @Override
    public String getDescription() {
        return "Construct knowledge graph for a single version of the project";
    }
    
    @Override
    public void execute(AppConfig config) throws Exception {
        long totalStartTime = System.currentTimeMillis();
        
        logger.info("========================================");
        logger.info("Neo4j 批量导入流程");
        logger.info("========================================");
        logger.info("项目路径: " + config.getProjectPath());
        logger.info("项目名称: " + config.getProjectName());
        logger.info("Neo4j Home: " + config.getNeo4jHome());
        logger.info("");
        
        // ==================== 阶段 1: 解析项目 ====================
        logger.info("========================================");
        logger.info("阶段 1: 使用 Spoon 解析项目");
        logger.info("========================================");
        long phase1Start = System.currentTimeMillis();
        
        ProjectAnalyzer analyzer = new ProjectAnalyzer(config.getProjectPath());
        KnowledgeGraph kg = analyzer.analyze();
        
        long phase1End = System.currentTimeMillis();
        double phase1Duration = (phase1End - phase1Start) / 1000.0;
        
        // 打印摘要
        System.out.println("\n" + kg.generateProjectSummary());
        logger.info(String.format("✓ 解析完成，耗时: %.2f 秒", phase1Duration));
        logger.info("");
        
        // ==================== 阶段 2: 导出 CSV ====================
        logger.info("========================================");
        logger.info("阶段 2: 导出 Neo4j Bulk Import 格式 CSV");
        logger.info("========================================");
        long phase2Start = System.currentTimeMillis();
        
        Neo4jBulkCsvExporter csvExporter = new Neo4jBulkCsvExporter();
        String exportDir = csvExporter.exportForBulkImport(kg, config.getProjectName());
        
        long phase2End = System.currentTimeMillis();
        double phase2Duration = (phase2End - phase2Start) / 1000.0;
        
        logger.info(String.format("✓ CSV 导出完成，耗时: %.2f 秒", phase2Duration));
        logger.info("");
        
        // ==================== 阶段 3: 停止 Neo4j 服务 ====================
        logger.info("========================================");
        logger.info("阶段 3: 停止 Neo4j 服务");
        logger.info("========================================");
        
        Neo4jServiceManager serviceManager = new Neo4jServiceManager(config.getNeo4jHome());
        if (!serviceManager.stop()) {
            logger.warn("停止 Neo4j 失败，尝试继续...");
        }
        
        // ==================== 阶段 4: 批量导入 Neo4j ====================
        logger.info("========================================");
        logger.info("阶段 4: 批量导入到 Neo4j");
        logger.info("========================================");
        long phase4Start = System.currentTimeMillis();
        
        // 确定CSV文件路径
        String nodesFile = exportDir + "/nodes_bulk.csv";
        String edgesFile = exportDir + "/edges_bulk.csv";
        
        // 执行批量导入
        Neo4jBulkImporter importer = new Neo4jBulkImporter(config.getNeo4jHome(), "neo4j", true);
        boolean importSuccess = importer.bulkImport(nodesFile, edgesFile);
        
        long phase4End = System.currentTimeMillis();
        double phase4Duration = (phase4End - phase4Start) / 1000.0;
        
        if (!importSuccess) {
            logger.error("✗ 批量导入失败");
            logger.error("");
            logger.error("可能的原因:");
            logger.error("1. NEO4J_HOME 路径不正确 - 当前: " + config.getNeo4jHome());
            logger.error("2. neo4j-admin 命令不可用");
            logger.error("3. CSV 文件格式有误");
            logger.error("");
            logger.error("或者使用手动脚本:");
            logger.error("  cd " + exportDir);
            logger.error("  bash import.sh");
            throw new RuntimeException("批量导入失败");
        }
        
        logger.info(String.format("✓ 批量导入完成，耗时: %.2f 秒", phase4Duration));
        logger.info("");
        
        // ==================== 阶段 5: 启动 Neo4j 服务 ====================
        logger.info("========================================");
        logger.info("阶段 5: 启动 Neo4j 服务");
        logger.info("========================================");
        
        if (!serviceManager.start()) {
            logger.error("✗ 启动 Neo4j 失败，请手动执行: neo4j start");
        } else {
            logger.info("✓ Neo4j 服务已启动");
        }
        
        // ==================== 总结 ====================
        long totalEndTime = System.currentTimeMillis();
        double totalDuration = (totalEndTime - totalStartTime) / 1000.0;
        
        logger.info("");
        logger.info("========================================");
        logger.info("✓ 全部完成!");
        logger.info("========================================");
        logger.info(String.format("阶段 1 (Spoon解析): %.2f 秒", phase1Duration));
        logger.info(String.format("阶段 2 (CSV导出): %.2f 秒", phase2Duration));
        logger.info(String.format("阶段 4 (批量导入): %.2f 秒", phase4Duration));
        logger.info(String.format("总耗时: %.2f 秒", totalDuration));
        logger.info("");
        logger.info("验证导入:");
        logger.info("1. 访问浏览器: http://localhost:7474");
        logger.info("2. 统计节点: MATCH (n) RETURN count(n)");
        logger.info("3. 查看标签: CALL db.labels()");
        logger.info("4. 查看 METHOD 节点多标签: MATCH (m:METHOD) RETURN labels(m), m.name LIMIT 10");
        logger.info("========================================");
    }
}

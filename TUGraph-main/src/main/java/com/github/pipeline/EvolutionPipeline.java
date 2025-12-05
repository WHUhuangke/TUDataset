package com.github.pipeline;

import com.github.config.AppConfig;
import com.github.evolution.EvolutionAnalyzer;
import com.github.evolution.GraphMerger;
import com.github.git.CommitHistoryFetcher;
import com.github.logging.GraphLogger;
import com.github.model.KnowledgeGraph;
import com.github.neo4j.Neo4jBulkCsvExporter;
import com.github.neo4j.Neo4jBulkImporter;
import com.github.neo4j.Neo4jServiceManager;

/**
 * 演化分析流程
 * 分析两个版本之间的代码演化，构建包含演化信息的知识图谱
 * 
 * 流程步骤:
 * 1. 使用 EvolutionAnalyzer 分析两个版本的演化
 *    - Git checkout V1 → Spoon 解析 → KnowledgeGraph
 *    - Git checkout V2 → Spoon 解析 → KnowledgeGraph
 *    - RefactoringMiner 检测重构
 *    - NodeMatching 匹配节点
 *    - GraphMerger 合并图谱
 * 2. 导出为 Neo4j bulk import 格式的 CSV
 * 3. 自动停止 Neo4j 服务
 * 4. 使用 neo4j-admin import 命令导入
 * 5. 自动启动 Neo4j 服务
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class EvolutionPipeline implements Pipeline {
    
    private final GraphLogger logger = GraphLogger.getInstance();
    
    @Override
    public String getName() {
        return "Evolution Analysis";
    }
    
    @Override
    public String getDescription() {
        return "Analyze code evolution between two versions and construct evolution knowledge graph";
    }
    
    @Override
    public void execute(AppConfig config) throws Exception {
        long totalStartTime = System.currentTimeMillis();
        
        logger.info("========================================");
        logger.info("演化分析流程");
        logger.info("========================================");
        logger.info("项目路径 (Git 仓库): " + config.getProjectPath());
        logger.info("Commit: " + config.getCommit() + " (将自动与父提交比较)");
        logger.info("Neo4j Home: " + config.getNeo4jHome());
        logger.info("");
        
        // ==================== 阶段 1: 演化分析 ====================
        logger.info("========================================");
        logger.info("阶段 1: 执行演化分析");
        logger.info("========================================");
        long phase1Start = System.currentTimeMillis();
        
        GraphMerger.resetTrackedNodes();
        try {
            CommitHistoryFetcher fetcher = new CommitHistoryFetcher();
            var commits = fetcher.collectLinearHistory(config.getProjectPath(), config.getCommit(), 1);
            if (!commits.isEmpty()) {
                CommitHistoryFetcher.CommitInfo first = commits.get(0); // older
                GraphMerger.registerVersionMetadata(
                        "V1",
                        first.getCommitId(),
                        first.getShortId(),
                        first.getMessage(),
                        first.getAuthor(),
                        first.getCommitTime()
                );
            }
            if (commits.size() >= 2) {
                CommitHistoryFetcher.CommitInfo second = commits.get(1); // target
                GraphMerger.registerVersionMetadata(
                        "V2",
                        second.getCommitId(),
                        second.getShortId(),
                        second.getMessage(),
                        second.getAuthor(),
                        second.getCommitTime()
                );
            }
        } catch (Exception e) {
            logger.warn("无法加载提交元数据: " + e.getMessage());
        }

        EvolutionAnalyzer analyzer = new EvolutionAnalyzer(
                config.getProjectPath(),  // 在演化模式下，projectPath 同时作为 Git 仓库路径
                config.getProjectPath(),  // 也作为项目分析路径
                config.getCommit(),
                logger
        );
        
        KnowledgeGraph mergedGraph = analyzer.analyze();
        
        long phase1End = System.currentTimeMillis();
        double phase1Duration = (phase1End - phase1Start) / 1000.0;
        
        // 打印摘要
        System.out.println("\n" + mergedGraph.generateProjectSummary());
        logger.info(String.format("✓ 演化分析完成，耗时: %.2f 秒", phase1Duration));
        logger.info("");

        // ==================== 阶段 2: 导出 CSV ====================
        logger.info("========================================");
        logger.info("阶段 2: 导出 Neo4j Bulk Import 格式 CSV");
        logger.info("========================================");
        long phase2Start = System.currentTimeMillis();

        Neo4jBulkCsvExporter csvExporter = new Neo4jBulkCsvExporter();
        String projectName = config.getProjectName() + "_evolution_" +
                             config.getCommit().substring(0, 7);
        String exportDir = csvExporter.exportForBulkImport(mergedGraph, projectName);

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
        Neo4jBulkImporter importer = new Neo4jBulkImporter(config.getNeo4jHome());
        boolean importSuccess = importer.bulkImport(nodesFile, edgesFile);

        long phase4End = System.currentTimeMillis();
        double phase4Duration = (phase4End - phase4Start) / 1000.0;

        if (!importSuccess) {
            throw new RuntimeException("Neo4j 批量导入失败");
        }

        logger.info(String.format("✓ 导入完成，耗时: %.2f 秒", phase4Duration));
        logger.info("");

        // ==================== 阶段 5: 启动 Neo4j 服务 ====================
        logger.info("========================================");
        logger.info("阶段 5: 启动 Neo4j 服务");
        logger.info("========================================");

        if (!serviceManager.start()) {
            logger.error("启动 Neo4j 失败！请手动启动服务。");
            throw new RuntimeException("无法启动 Neo4j 服务");
        }

        logger.info("✓ Neo4j 服务已启动");
        logger.info("");
        
        // ==================== 完成总结 ====================
        long totalEndTime = System.currentTimeMillis();
        double totalDuration = (totalEndTime - totalStartTime) / 1000.0;
        
        logger.info("========================================");
        logger.info("演化分析流程完成");
        logger.info("========================================");
        logger.info(String.format("总耗时: %.2f 秒", totalDuration));
        logger.info("");
        logger.info("演化统计:");
        logger.info("  V1 节点数: " + analyzer.getV1Graph().getAllNodes().size());
        logger.info("  V2 节点数: " + analyzer.getV2Graph().getAllNodes().size());
        logger.info("  合并后节点数: " + mergedGraph.getAllNodes().size());
        logger.info("  检测到重构: " + analyzer.getRefactorings().size() + " 个");
        logger.info("  节点映射: " + analyzer.getNodeMapping().size() + " 对");
        logger.info("");
        logger.info("可以通过 Neo4j Browser 查看演化图谱:");
        logger.info("  URL: http://localhost:7474");
        logger.info("  Database: " + config.getProjectName());
        logger.info("");
        logger.info("演化查询示例:");
        logger.info("  // 查找所有重命名的方法");
        logger.info("  MATCH (m:METHOD)-[:RENAMED]->(m2:METHOD) RETURN m, m2");
        logger.info("");
        logger.info("  // 查找所有新增的类");
        logger.info("  MATCH (t:TYPE) WHERE t.versionStatus = 'ADDED' RETURN t");
        logger.info("");
        logger.info("  // 查找所有删除的方法");
        logger.info("  MATCH (m:METHOD) WHERE m.versionStatus = 'DELETED' RETURN m");
        logger.info("========================================");
    }
}

package com.github.pipeline;

import com.github.config.AppConfig;
import com.github.config.TimelineStrategy;
import com.github.evolution.EvolutionAnalyzer;
import com.github.evolution.GraphMerger;
import com.github.evolution.TimelineAggregator;
import com.github.evolution.TimelineVersion;
import com.github.evolution.timeline.RefactoringTimelineBuilder;
import com.github.git.CommitHistoryFetcher;
import com.github.logging.GraphLogger;
import com.github.model.KnowledgeGraph;
import com.github.neo4j.Neo4jBulkCsvExporter;
import com.github.neo4j.Neo4jBulkImporter;
import com.github.neo4j.Neo4jServiceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 多版本演化流水线：针对目标提交向前回溯若干提交，逐对执行演化分析。
 */
public class MultiVersionEvolutionPipeline implements Pipeline {

    private static final GraphLogger logger = GraphLogger.getInstance();

    @Override
    public String getName() {
        return "Multi Version Evolution Analysis";
    }

    @Override
    public String getDescription() {
        return "Analyze multiple consecutive commits and export evolution knowledge graphs";
    }

    @Override
    public void execute(AppConfig config) throws Exception {
        long start = System.currentTimeMillis();

        String repoPath = config.getProjectPath();
        String targetCommit = config.getCommit();
        int historyWindow = Math.max(1, config.getEvolutionHistoryWindow());

        logger.info("========================================");
        logger.info("多版本演化流程");
        logger.info("仓库路径: " + repoPath);
        logger.info("目标提交: " + targetCommit);
        logger.info("回溯深度: " + historyWindow);
        
        // 获取时间线策略
        TimelineStrategy strategy = config.getTimelineStrategy();
        logger.info("时间线策略: " + strategy.getDisplayName());
        logger.info("========================================");

        List<TimelineVersion> timeline = collectTimeline(repoPath, targetCommit, historyWindow, strategy, config);
        if (timeline.size() < 2) {
            logger.warn("历史提交数量不足，至少需要一个父提交。");
            return;
        }

        TimelineAggregator aggregator = new TimelineAggregator(timeline);
        GraphMerger.resetTrackedNodes();
        for (TimelineVersion version : timeline) {
            GraphMerger.registerVersionMetadata(
                    version.getLabel(),
                    version.getCommitId(),
                    version.getShortId(),
                    version.getMessage(),
                    version.getAuthor(),
                    version.getCommitTime()
            );
        }
        for (int i = timeline.size() - 1; i >= 1; i--) {
            TimelineVersion baseVersion = timeline.get(i - 1);
            TimelineVersion currentVersion = timeline.get(i);

            logger.info("----------------------------------------");
            logger.info(String.format("分析提交对: %s -> %s",
                    baseVersion.toString(),
                    currentVersion.toString()));
            logger.info("----------------------------------------");

            // 使用显式指定两个commit的构造函数，确保分析正确的提交对
            EvolutionAnalyzer analyzer = new EvolutionAnalyzer(
                    repoPath,
                    repoPath,
                    baseVersion.getCommitId(),   // V1 (旧版本)
                    currentVersion.getCommitId(), // V2 (新版本)
                    logger
            );
            analyzer.overrideVersionLabels(
                    baseVersion.getLabel(),
                    currentVersion.getLabel()
            );

            KnowledgeGraph mergedGraph = analyzer.analyze();
            aggregator.addGraph(mergedGraph);

            logger.info(String.format("✓ 提交对 %s -> %s 分析完成",
                    baseVersion.getLabel(),
                    currentVersion.getLabel()));
        }

        exportAndImport(config, timeline, aggregator.getAggregatedGraph());

        long end = System.currentTimeMillis();
        logger.info(String.format("多版本演化流程完成，总耗时 %.2f 秒", (end - start) / 1000.0));
    }

    /**
     * 收集时间线（支持多种策略）
     */
    private List<TimelineVersion> collectTimeline(
            String repoPath, 
            String targetCommit, 
            int historyWindow,
            TimelineStrategy strategy,
            AppConfig config) throws Exception {
        
        List<String> commitIds;
        
        switch (strategy) {
            case LINEAR:
                // 线性策略：简单的相邻commit回溯
                commitIds = collectLinearTimeline(repoPath, targetCommit, historyWindow);
                break;
                
            case FILE_BASED:
                // 文件关联策略（未实现，回退到线性）
                logger.warn("FILE_BASED 策略尚未实现，使用 LINEAR 策略");
                commitIds = collectLinearTimeline(repoPath, targetCommit, historyWindow);
                break;
                
            case REFACTORING_DRIVEN:
                // 重构驱动策略：基于RefactoringMiner的智能追踪
                commitIds = collectRefactoringDrivenTimeline(repoPath, targetCommit, config);
                break;
                
            default:
                logger.warn("未知策略 " + strategy + "，使用 LINEAR 策略");
                commitIds = collectLinearTimeline(repoPath, targetCommit, historyWindow);
                break;
        }
        
        // 将commit ID列表转换为TimelineVersion列表
        List<TimelineVersion> timeline = convertToTimelineVersions(repoPath, commitIds);
        
        logger.info("");
        logger.info("时间线构建完成:");
        for (TimelineVersion version : timeline) {
            logger.info("  " + version.toString());
        }
        logger.info("");
        
        return timeline;
    }
    
    /**
     * 线性策略：按Git父提交关系回溯
     */
    private List<String> collectLinearTimeline(String repoPath, String targetCommit, int historyWindow) throws IOException {
        CommitHistoryFetcher fetcher = new CommitHistoryFetcher();
        List<CommitHistoryFetcher.CommitInfo> commits = fetcher.collectLinearHistory(
            repoPath, targetCommit, historyWindow
        );
        
        List<String> commitIds = new ArrayList<>();
        for (CommitHistoryFetcher.CommitInfo commit : commits) {
            commitIds.add(commit.getCommitId());
        }
        
        return commitIds;
    }
    
    /**
     * 重构驱动策略：基于RefactoringMiner的方法级追踪
     */
    private List<String> collectRefactoringDrivenTimeline(
            String repoPath, 
            String targetCommit,
            AppConfig config) throws Exception {
        
        RefactoringTimelineBuilder builder = new RefactoringTimelineBuilder(repoPath);
        
        // 设置参数
        builder.setMaxDepth(config.getRefactoringTimelineMaxDepth());
        builder.setMaxDays(config.getRefactoringTimelineMaxDays());
        
        // 构建时间线
        RefactoringTimelineBuilder.TimelineResult result = builder.buildTimeline(targetCommit);
        
        return result.getCommits();
    }
    
    /**
     * 将commit ID列表转换为TimelineVersion列表
     */
    private List<TimelineVersion> convertToTimelineVersions(String repoPath, List<String> commitIds) throws IOException {
        CommitHistoryFetcher fetcher = new CommitHistoryFetcher();
        List<TimelineVersion> versions = new ArrayList<>();
        
        int size = commitIds.size();
        for (int i = 0; i < size; i++) {
            String commitId = commitIds.get(i);
            
            // 获取commit详细信息
            CommitHistoryFetcher.CommitInfo info = fetcher.getCommitInfo(repoPath, commitId);
            
            // 计算距离目标commit的距离
            int distanceToHead = size - 1 - i;
            String label = distanceToHead == 0 ? "V0" : "V-" + distanceToHead;
            
            versions.add(new TimelineVersion(
                commitId,
                info.getShortId(),
                label,
                i,
                info.getMessage(),
                info.getAuthor(),
                info.getCommitTime()
            ));
        }
        
        return versions;
    }

    private void exportAndImport(AppConfig config, List<TimelineVersion> timeline, KnowledgeGraph aggregatedGraph) throws Exception {
        if (aggregatedGraph == null || aggregatedGraph.getAllNodes().isEmpty()) {
            logger.warn("聚合后的知识图谱为空，跳过导出。");
            return;
        }

        Neo4jBulkCsvExporter exporter = new Neo4jBulkCsvExporter();
        Neo4jServiceManager serviceManager = new Neo4jServiceManager(config.getNeo4jHome());
        Neo4jBulkImporter importer = new Neo4jBulkImporter(config.getNeo4jHome());

        String dirName = config.getProjectName()
                + "_timeline_"
                + timeline.get(0).getLabel()
                + "_"
                + timeline.get(timeline.size() - 1).getLabel();

        logger.info(String.format("导出聚合图谱: %s", dirName));

        String exportDir = exporter.exportForBulkImport(aggregatedGraph, dirName);

        if (!serviceManager.stop()) {
            logger.warn("停止 Neo4j 失败，将跳过自动导入。");
            return;
        }

        String nodesFile = exportDir + "/nodes_bulk.csv";
        String edgesFile = exportDir + "/edges_bulk.csv";

        if (!importer.bulkImport(nodesFile, edgesFile)) {
            logger.warn("Neo4j 批量导入失败，请手动执行导入脚本。");
        } else {
            logger.info("✓ 已导入聚合后的多版本演化图谱。");
        }

        if (!serviceManager.start()) {
            throw new RuntimeException("Neo4j 服务启动失败，请手动启动。");
        }
    }
}

package com.github.export;

import com.github.logging.GraphLogger;
import com.github.model.KnowledgeGraph;

/**
 * CSV 导出器 - 导出功能已禁用
 * 保留原有结构但取消所有文件导出操作
 */
public class CsvExporter {
    
    private static final GraphLogger logger = GraphLogger.getInstance();
    private static final String OUTPUT_DIR = "./csv_export";
    
    /**
     * 导出知识图谱到 CSV 文件 - 功能已禁用
     * @param graph 知识图谱
     * @param projectName 项目名称
     * @return 空字符串（不再返回实际路径）
     */
    public String exportToCSV(KnowledgeGraph graph, String projectName) {
        logger.info("CSV 导出功能已被禁用，不再执行任何文件导出操作");
        
        // 不再创建目录和文件，直接返回空字符串
        return "";
    }
    
    /**
     * 导出节点到 CSV - 功能已禁用
     */
    private String exportNodes(KnowledgeGraph graph, String exportDir) {
        logger.info("节点导出功能已被禁用");
        return "";
    }
    
    /**
     * 导出边到 CSV - 功能已禁用
     */
    private String exportEdges(KnowledgeGraph graph, String exportDir) {
        logger.info("边导出功能已被禁用");
        return "";
    }
    
    /**
     * 导出统计信息到 CSV - 功能已禁用
     */
    private String exportStatistics(KnowledgeGraph graph, String exportDir) {
        logger.info("统计信息导出功能已被禁用");
        return "";
    }
    
    /**
     * 将属性 Map 转换为字符串 - 保留方法但不再使用
     */
    private String propertiesToString(com.github.model.GraphElement element) {
        return "{}";
    }
    
    /**
     * CSV 转义 - 保留方法但不再使用
     */
    private String escapeCsv(String value) {
        return value != null ? value : "";
    }
}


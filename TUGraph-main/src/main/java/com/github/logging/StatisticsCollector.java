package com.github.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统计信息收集器
 * 负责收集和统计图谱构建过程中的各类信息
 */
public class StatisticsCollector {
    
    // 节点统计
    private final Map<String, Integer> nodeStats = new ConcurrentHashMap<>();
    
    // 边统计
    private final Map<String, Integer> edgeStats = new ConcurrentHashMap<>();
    
    // 阶段耗时统计
    private final Map<String, Long> phaseTimings = new HashMap<>();
    
    // 文件统计
    private int totalFiles = 0;
    private int processedFiles = 0;
    private long totalLines = 0;
    
    // 内存统计
    private long peakMemoryUsage = 0;
    
    // 开始时间
    private long startTime = 0;
    
    /**
     * 记录分析开始时间
     */
    public void recordStart() {
        startTime = System.currentTimeMillis();
        updateMemoryUsage();
    }
    
    /**
     * 记录节点创建
     */
    public void recordNodeCreation(String nodeType) {
        nodeStats.merge(nodeType, 1, Integer::sum);
        updateMemoryUsage();
    }
    
    /**
     * 记录边创建
     */
    public void recordEdgeCreation(String edgeType) {
        edgeStats.merge(edgeType, 1, Integer::sum);
        updateMemoryUsage();
    }
    
    /**
     * 记录阶段耗时
     */
    public void recordPhaseTiming(String phaseName, long durationMs) {
        phaseTimings.put(phaseName, durationMs);
    }
    
    /**
     * 设置总文件数
     */
    public void setTotalFiles(int total) {
        this.totalFiles = total;
    }
    
    /**
     * 记录处理的文件
     */
    public void recordProcessedFile() {
        processedFiles++;
    }
    
    /**
     * 记录代码行数
     */
    public void recordLines(long lines) {
        totalLines += lines;
    }
    
    /**
     * 更新内存使用
     */
    private void updateMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        if (usedMemory > peakMemoryUsage) {
            peakMemoryUsage = usedMemory;
        }
    }
    
    /**
     * 获取总节点数
     */
    public int getTotalNodes() {
        return nodeStats.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * 获取总边数
     */
    public int getTotalEdges() {
        return edgeStats.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * 获取总耗时
     */
    public long getTotalDuration() {
        if (startTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * 获取节点统计信息
     */
    public Map<String, Integer> getNodeStats() {
        return new HashMap<>(nodeStats);
    }
    
    /**
     * 获取边统计信息
     */
    public Map<String, Integer> getEdgeStats() {
        return new HashMap<>(edgeStats);
    }
    
    /**
     * 获取阶段耗时
     */
    public Map<String, Long> getPhaseTimings() {
        return new HashMap<>(phaseTimings);
    }
    
    /**
     * 获取峰值内存使用
     */
    public long getPeakMemoryUsage() {
        return peakMemoryUsage;
    }
    
    /**
     * 生成摘要报告
     */
    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(LogFormatter.formatSeparator('=', 50)).append("\n");
        sb.append(LogFormatter.formatTitle("Analysis Summary", 50)).append("\n");
        sb.append(LogFormatter.formatSeparator('=', 50)).append("\n\n");
        
        // 节点统计
        sb.append("Nodes:\n");
        sb.append(String.format("  Total: %,d\n", getTotalNodes()));
        nodeStats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> 
                    sb.append(String.format("    %-20s: %,5d\n", entry.getKey(), entry.getValue()))
                );
        sb.append("\n");
        
        // 边统计
        sb.append("Edges:\n");
        sb.append(String.format("  Total: %,d\n", getTotalEdges()));
        edgeStats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> 
                    sb.append(String.format("    %-20s: %,5d\n", entry.getKey(), entry.getValue()))
                );
        sb.append("\n");
        
        // 文件统计
        sb.append("Files:\n");
        sb.append(String.format("  Processed: %,d / %,d\n", processedFiles, totalFiles));
        sb.append(String.format("  Total Lines: %,d\n", totalLines));
        sb.append("\n");
        
        // 耗时统计
        sb.append("Timing:\n");
        sb.append(String.format("  Total Duration: %s\n", LogFormatter.formatDuration(getTotalDuration())));
        phaseTimings.forEach((phase, duration) ->
            sb.append(String.format("    %-20s: %s\n", phase, LogFormatter.formatDuration(duration)))
        );
        sb.append("\n");
        
        // 内存统计
        sb.append("Memory:\n");
        sb.append(String.format("  Peak Usage: %s\n", LogFormatter.formatFileSize(peakMemoryUsage)));
        sb.append("\n");
        
        sb.append(LogFormatter.formatSeparator('=', 50)).append("\n");
        
        return sb.toString();
    }
    
    /**
     * 生成简洁报告（控制台版）
     */
    public String generateConsoleSummary() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(LogFormatter.formatSeparator('=', 50)).append("\n");
        sb.append(LogFormatter.formatTitle("Analysis Summary", 50)).append("\n");
        sb.append(LogFormatter.formatSeparator('=', 50)).append("\n");
        
        sb.append(String.format("Total Nodes: %,d\n", getTotalNodes()));
        sb.append(String.format("Total Edges: %,d\n", getTotalEdges()));
        sb.append(String.format("Total Duration: %s\n", LogFormatter.formatDuration(getTotalDuration())));
        sb.append(String.format("Peak Memory: %s\n", LogFormatter.formatFileSize(peakMemoryUsage)));
        
        sb.append(LogFormatter.formatSeparator('=', 50)).append("\n");
        
        return sb.toString();
    }
    
    /**
     * 重置所有统计信息
     */
    public void reset() {
        nodeStats.clear();
        edgeStats.clear();
        phaseTimings.clear();
        totalFiles = 0;
        processedFiles = 0;
        totalLines = 0;
        peakMemoryUsage = 0;
        startTime = 0;
    }
}

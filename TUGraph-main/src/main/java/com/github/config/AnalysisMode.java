package com.github.config;

/**
 * 分析模式枚举
 * 定义系统支持的两种知识图谱构建模式
 */
public enum AnalysisMode {
    /**
     * 单版本模式 - 构建单个版本的知识图谱
     * 这是系统的默认模式，适用于分析某个时间点的项目结构
     */
    SINGLE_VERSION("Single Version Analysis"),
    
    /**
     * 演化模式 - 分析两个版本之间的演化
     * 用于比较两个版本的差异，构建包含演化关系的知识图谱
     */
    EVOLUTION("Evolution Analysis"),
    
    /**
     * 多版本演化模式 - 自动分析目标提交及其历史提交
     */
    MULTI_EVOLUTION("Multi Version Evolution Analysis");
    
    private final String displayName;
    
    AnalysisMode(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * 获取模式的显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 从字符串解析模式（不区分大小写）
     * @param modeStr 模式字符串
     * @return 对应的 AnalysisMode
     * @throws IllegalArgumentException 如果字符串无效
     */
    public static AnalysisMode fromString(String modeStr) {
        if (modeStr == null || modeStr.isEmpty()) {
            return SINGLE_VERSION;  // 默认为单版本模式
        }
        
        try {
            return valueOf(modeStr.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid analysis mode: " + modeStr + 
                ". Valid values are: SINGLE_VERSION, EVOLUTION, MULTI_EVOLUTION"
            );
        }
    }
}

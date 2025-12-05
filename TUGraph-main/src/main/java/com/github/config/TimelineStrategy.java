package com.github.config;

/**
 * 时间线构建策略枚举
 * 
 * 定义了三种不同的时间线构建方式，用户可以根据分析需求选择：
 * 
 * 1. LINEAR: 线性回溯策略（默认）
 *    - 简单地按照 Git 第一父提交关系回溯
 *    - 适合快速概览、连续开发的场景
 *    - 可能包含大量无关的 commit
 * 
 * 2. FILE_BASED: 基于文件变更的关联策略（未实现）
 *    - 只追踪目标 commit 中变更文件的历史
 *    - 过滤掉无关的 commit，提高语义关联性
 *    - 适合特定功能演化分析、影响分析
 * 
 * 3. REFACTORING_DRIVEN: 基于重构的方法级追踪策略
 *    - 使用RefactoringMiner追踪方法级别的演化
 *    - 动态更新追踪列表（处理重命名、移动等）
 *    - 高精准度、低噪音
 * 
 * @author TUGraph Team
 * @since 2.2.0
 */
public enum TimelineStrategy {
    
    /**
     * 线性回溯策略 - 按照 Git 第一父提交关系回溯
     */
    LINEAR("Linear", "按时间顺序线性回溯相邻提交"),
    
    /**
     * 基于文件变更的关联策略 - 只追踪相关文件的变更历史
     */
    FILE_BASED("File-Based", "基于文件变更的逻辑关联回溯（未实现）"),
    
    /**
     * 基于重构的方法级追踪策略 - 使用RefactoringMiner追踪方法演化
     */
    REFACTORING_DRIVEN("Refactoring-Driven", "基于重构的方法级别追踪");
    
    private final String displayName;
    private final String description;
    
    TimelineStrategy(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return displayName + " - " + description;
    }
}

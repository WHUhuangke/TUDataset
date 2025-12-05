package com.github.model;

/**
 * 演化边基类 - 表示节点在版本演化过程中的关系
 * 
 * <p>演化边用于连接不同版本之间的相关节点，记录代码元素的演化轨迹。
 * 包括重命名、移动、提取、内联等重构操作，以及签名变化等修改操作。
 * 
 * <p><b>演化边类型：</b>
 * <ul>
 *   <li>RENAMED - 重命名（类、方法、字段等）</li>
 *   <li>MOVED - 移动（类在包之间移动，方法在类之间移动等）</li>
 *   <li>EXTRACTED - 提取（提取方法、提取类等）</li>
 *   <li>INLINED - 内联（内联方法、内联变量等）</li>
 *   <li>CHANGED_SIGNATURE - 签名变化（方法参数、返回值变化）</li>
 *   <li>REFACTORED - 一般性重构（其他重构操作）</li>
 *   <li>SIMILAR_TO - 相似节点（基于结构相似度的匹配）</li>
 * </ul>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public abstract class EvolutionEdge extends Edge {
    
    /**
     * 重构类型（来自 RefactoringMiner 的 RefactoringType）
     * 例如：RENAME_METHOD, EXTRACT_METHOD, MOVE_CLASS 等
     */
    protected String refactoringType;
    
    /**
     * 匹配置信度（0.0 - 1.0）
     * 1.0 表示完全匹配，< 1.0 表示基于相似度的推测匹配
     */
    protected double confidence;
    
    /**
     * 演化描述（人类可读）
     * 例如："方法 calculateTotal 被重命名为 computeSum"
     */
    protected String description;
    
    /**
     * 源版本（Git commit hash）
     */
    protected String fromVersion;
    
    /**
     * 目标版本（Git commit hash）
     */
    protected String toVersion;
    
    protected EvolutionEdge(String fromNodeId, String toNodeId) {
        super(fromNodeId, toNodeId);
        this.confidence = 1.0;  // 默认完全匹配
    }
    
    protected EvolutionEdge(String fromNodeId, String toNodeId, String fromVersion, String toVersion) {
        super(fromNodeId, toNodeId);
        this.confidence = 1.0;  // 默认完全匹配
        setFromVersion(fromVersion);
        setToVersion(toVersion);
    }
    
    @Override
    public abstract String getEdgeType();
    
    /**
     * 获取重构类型
     */
    public String getRefactoringType() {
        return refactoringType;
    }
    
    /**
     * 设置重构类型
     */
    public void setRefactoringType(String refactoringType) {
        this.refactoringType = refactoringType;
        setProperty("refactoringType", refactoringType);
    }
    
    /**
     * 获取匹配置信度
     */
    public double getConfidence() {
        return confidence;
    }
    
    /**
     * 设置匹配置信度
     */
    public void setConfidence(double confidence) {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        this.confidence = confidence;
        setProperty("confidence", confidence);
    }
    
    /**
     * 获取演化描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 设置演化描述
     */
    public void setDescription(String description) {
        this.description = description;
        setProperty("description", description);
    }
    
    /**
     * 获取源版本
     */
    public String getFromVersion() {
        return fromVersion;
    }
    
    /**
     * 设置源版本
     */
    public void setFromVersion(String fromVersion) {
        this.fromVersion = fromVersion;
        if (isNonBlank(fromVersion)) {
            setProperty("fromVersion", fromVersion);
        } else {
            properties.remove("fromVersion");
        }
        updateVersionFlowProperty();
    }
    
    /**
     * 获取目标版本
     */
    public String getToVersion() {
        return toVersion;
    }
    
    /**
     * 设置目标版本
     */
    public void setToVersion(String toVersion) {
        this.toVersion = toVersion;
        if (isNonBlank(toVersion)) {
            setProperty("toVersion", toVersion);
        } else {
            properties.remove("toVersion");
        }
        updateVersionFlowProperty();
    }
    
    /**
     * 判断是否为高置信度匹配
     * @return 置信度 >= 0.8 返回 true
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }
    
    /**
     * 判断是否来自 RefactoringMiner
     * @return 是否有 refactoringType
     */
    public boolean isFromRefactoringMiner() {
        return refactoringType != null && !refactoringType.isEmpty();
    }
    
    @Override
    public String getLabel() {
        return getEdgeType();
    }

    private void updateVersionFlowProperty() {
        if (isNonBlank(fromVersion) && isNonBlank(toVersion)) {
            setProperty("versionFlow", fromVersion + "->" + toVersion);
        } else {
            properties.remove("versionFlow");
        }
    }

    private boolean isNonBlank(String text) {
        return text != null && !text.isBlank();
    }
}

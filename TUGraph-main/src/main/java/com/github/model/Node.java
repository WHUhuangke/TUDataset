package com.github.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 节点抽象类 - 包含丰富的语义信息用于LLM检索
 */
public abstract class Node implements GraphElement {
    protected final String id;
    protected final Map<String, Object> properties;
    
    // 核心语义信息字段
    protected String sourceCode;        // 源代码
    protected String documentation;     // 文档注释（Javadoc）
    protected String comments;          // 所有注释（包括行注释、块注释、Javadoc）
    protected String semanticSummary;   // 语义摘要（用于LLM快速理解）
    
    // 位置信息字段
    protected String absolutePath;      // 节点所在文件的绝对路径
    protected String relativePath;      // 节点所在文件的相对路径（相对于项目根目录）
    
    // ==================== 版本演化信息字段 (Phase 2) ====================
    // 以下字段用于支持版本演化分析，仅在 EVOLUTION 模式下使用
    
    /**
     * 节点存在的版本集合（Git commit hash）
     * 单版本模式下为空集合
     */
    protected Set<String> versions;
    
    /**
     * 节点的版本状态（仅在演化模式的版本比较中使用）
     * 单版本模式下为 null
     */
    protected VersionStatus versionStatus;
    
    /**
     * 节点首次出现的版本（Git commit hash）
     * 单版本模式下为 null
     */
    protected String firstVersion;
    
    /**
     * 节点最后出现的版本（Git commit hash）
     * 单版本模式下为 null
     */
    protected String lastVersion;
    
    // ==================== 版本演化信息字段结束 ====================
    
    protected Node() {
        this.id = UUID.randomUUID().toString();
        this.properties = new HashMap<>();
        this.versions = new HashSet<>();  // 初始化版本集合
    }
    
    protected Node(String id) {
        this.id = id;
        this.properties = new HashMap<>();
        this.versions = new HashSet<>();  // 初始化版本集合
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }
    
    @Override
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    @Override
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    public abstract String getNodeType();
    
    // 语义信息的getter/setter
    public String getSourceCode() {
        return sourceCode;
    }
    
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }
    
    public String getDocumentation() {
        return documentation;
    }
    
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }
    
    public String getComments() {
        return comments;
    }
    
    public void setComments(String comments) {
        this.comments = comments;
    }
    
    public String getSemanticSummary() {
        return semanticSummary;
    }
    
    public void setSemanticSummary(String semanticSummary) {
        this.semanticSummary = semanticSummary;
    }

    // 位置信息的getter/setter
    public String getAbsolutePath() {
        return absolutePath;
    }
    
    public void setAbsolutePath(String absolutePath) {
        this.absolutePath = absolutePath;
    }
    
    public String getRelativePath() {
        return relativePath;
    }
    
    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }
    
    /**
     * 生成用于LLM检索的上下文字符串
     */
    public String toContextString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(getNodeType()).append(": ").append(getLabel()).append(" ===\n");
        
        // 位置信息
        if (relativePath != null && !relativePath.isEmpty()) {
            sb.append("Location: ").append(relativePath).append("\n");
        }
        
        if (semanticSummary != null && !semanticSummary.isEmpty()) {
            sb.append("Summary: ").append(semanticSummary).append("\n");
        }
        
        // 优先显示documentation，如果为空则显示comments
        if (documentation != null && !documentation.isEmpty()) {
            sb.append("Documentation:\n").append(documentation).append("\n");
        } else if (comments != null && !comments.isEmpty()) {
            sb.append("Comments:\n").append(comments).append("\n");
        }
        
        if (sourceCode != null && !sourceCode.isEmpty()) {
            sb.append("Source Code:\n").append(sourceCode).append("\n");
        }
        return sb.toString();
    }
    
    // ==================== 版本演化相关方法 (Phase 2) ====================
    
    /**
     * 获取节点存在的所有版本
     * @return 版本集合（不可变视图）
     */
    public Set<String> getVersions() {
        return new HashSet<>(versions);
    }
    
    /**
     * 添加节点存在的版本
     * @param version Git commit hash
     */
    public void addVersion(String version) {
        if (version != null && !version.trim().isEmpty()) {
            this.versions.add(version);
        }
    }
    
    /**
     * 批量添加版本
     * @param versions 版本集合
     */
    public void addVersions(Set<String> versions) {
        if (versions != null) {
            this.versions.addAll(versions);
        }
    }

    /**
     * 替换当前的版本集合，主要用于时间线聚合时的标签规范化。
     */
    public void replaceVersions(Set<String> versions) {
        this.versions.clear();
        if (versions != null) {
            for (String version : versions) {
                if (version != null && !version.trim().isEmpty()) {
                    this.versions.add(version);
                }
            }
        }
    }
    
    /**
     * 检查节点是否存在于指定版本
     * @param version Git commit hash
     * @return 是否存在
     */
    public boolean existsInVersion(String version) {
        return versions.contains(version);
    }
    
    /**
     * 获取版本状态
     * @return 版本状态
     */
    public VersionStatus getVersionStatus() {
        return versionStatus;
    }
    
    /**
     * 设置版本状态
     * @param versionStatus 版本状态
     */
    public void setVersionStatus(VersionStatus versionStatus) {
        this.versionStatus = versionStatus;
    }
    
    /**
     * 获取首次出现的版本
     * @return Git commit hash
     */
    public String getFirstVersion() {
        return firstVersion;
    }
    
    /**
     * 设置首次出现的版本
     * @param firstVersion Git commit hash
     */
    public void setFirstVersion(String firstVersion) {
        this.firstVersion = firstVersion;
    }
    
    /**
     * 获取最后出现的版本
     * @return Git commit hash
     */
    public String getLastVersion() {
        return lastVersion;
    }
    
    /**
     * 设置最后出现的版本
     * @param lastVersion Git commit hash
     */
    public void setLastVersion(String lastVersion) {
        this.lastVersion = lastVersion;
    }
    
    /**
     * 判断节点是否为演化节点（包含版本信息）
     * @return 是否为演化节点
     */
    public boolean isEvolutionNode() {
        return !versions.isEmpty() || versionStatus != null;
    }
    
    // ==================== 版本演化相关方法结束 ====================
}

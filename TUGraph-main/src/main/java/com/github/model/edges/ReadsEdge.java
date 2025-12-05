package com.github.model.edges;

import com.github.model.Edge;

/**
 * 读取字段关系（Method→Field）
 * 表示方法读取字段的值
 */
public class ReadsEdge extends Edge {
    
    /**
     * 标准构造函数（用于反射创建边副本）
     */
    public ReadsEdge(String methodId, String fieldId) {
        super(methodId, fieldId);
        setProperty("lineNumber", 0);
        setProperty("readCount", 1);
        setProperty("accessType", "direct");
    }
    
    /**
     * 完整构造函数（用于创建新边）
     */
    public ReadsEdge(String methodId, String fieldId, int lineNumber) {
        super(methodId, fieldId);
        setProperty("lineNumber", lineNumber);
        setProperty("readCount", 1);
        setProperty("accessType", "direct"); // direct/getter/reflection
    }
    
    @Override
    public String getLabel() {
        return "READS";
    }
    
    @Override
    public String getEdgeType() {
        return "READS";
    }
    
    public int getLineNumber() {
        return (int) getProperty("lineNumber");
    }
    
    public void incrementReadCount() {
        int count = (int) getProperty("readCount");
        setProperty("readCount", count + 1);
    }
    
    public void setAccessType(String accessType) {
        setProperty("accessType", accessType);
    }
    
    /**
     * 设置读取字段的代码片段
     * 例如: "int value = this.counter;"
     */
    public void setReadStatement(String statement) {
        setContextSnippet(statement);
        setDescription("Field read at line " + getProperty("lineNumber"));
    }
    
    /**
     * 生成增强的上下文字符串（支持合并后的边）
     */
    public String toContextString() {
        StringBuilder context = new StringBuilder();
        
        // 获取读取计数
        Object countObj = getProperty("readCount");
        int readCount = (countObj instanceof Integer) ? (Integer) countObj : 1;
        
        // 基础信息
        context.append("READS");
        if (readCount > 1) {
            context.append(String.format(" (×%d)", readCount));
        }
        context.append(": ");
        context.append(getSourceId()).append(" reads ").append(getTargetId());
        
        // 位置信息
        if (readCount == 1) {
            context.append(String.format(" at line %d", getLineNumber()));
        } else {
            @SuppressWarnings("unchecked")
            java.util.List<Integer> locations = (java.util.List<Integer>) getProperty("readLocations");
            if (locations != null && !locations.isEmpty()) {
                if (locations.size() <= 5) {
                    context.append(" at lines ").append(
                        locations.stream()
                            .map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(", "))
                    );
                } else {
                    context.append(String.format(" at lines %d, %d, ..., %d",
                        locations.get(0),
                        locations.get(1),
                        locations.get(locations.size() - 1)
                    ));
                }
            }
        }
        
        // 访问类型
        String accessType = (String) getProperty("accessType");
        if (accessType != null && !accessType.equals("direct")) {
            context.append(String.format(" [%s]", accessType));
        }
        
        // 语义摘要
        String semanticSummary = (String) getProperty("semanticSummary");
        if (semanticSummary != null) {
            context.append(String.format(" (%s)", semanticSummary));
        }
        
        return context.toString();
    }
}

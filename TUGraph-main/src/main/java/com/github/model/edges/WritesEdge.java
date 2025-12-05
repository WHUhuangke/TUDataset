package com.github.model.edges;

import com.github.model.Edge;

/**
 * 写入字段关系（Method→Field）
 * 表示方法修改字段的值
 */
public class WritesEdge extends Edge {
    
    /**
     * 标准构造函数（用于反射创建边副本）
     */
    public WritesEdge(String methodId, String fieldId) {
        super(methodId, fieldId);
        setProperty("lineNumber", 0);
        setProperty("writeCount", 1);
        setProperty("accessType", "direct");
        setProperty("isInitialization", false);
    }
    
    /**
     * 完整构造函数（用于创建新边）
     */
    public WritesEdge(String methodId, String fieldId, int lineNumber) {
        super(methodId, fieldId);
        setProperty("lineNumber", lineNumber);
        setProperty("writeCount", 1);
        setProperty("accessType", "direct"); // direct/setter/reflection
        setProperty("isInitialization", false);
    }
    
    @Override
    public String getLabel() {
        return "WRITES";
    }
    
    @Override
    public String getEdgeType() {
        return "WRITES";
    }
    
    public int getLineNumber() {
        return (int) getProperty("lineNumber");
    }
    
    public void incrementWriteCount() {
        int count = (int) getProperty("writeCount");
        setProperty("writeCount", count + 1);
    }
    
    public void setAccessType(String accessType) {
        setProperty("accessType", accessType);
    }
    
    public void setInitialization(boolean isInit) {
        setProperty("isInitialization", isInit);
    }
    
    /**
     * 设置写入字段的代码片段
     * 例如: "this.counter = newValue;"
     */
    public void setWriteStatement(String statement) {
        setContextSnippet(statement);
        setDescription("Field write at line " + getProperty("lineNumber"));
    }
    
    /**
     * 生成增强的上下文字符串（支持合并后的边）
     */
    public String toContextString() {
        StringBuilder context = new StringBuilder();
        
        // 获取写入计数
        Object countObj = getProperty("writeCount");
        int writeCount = (countObj instanceof Integer) ? (Integer) countObj : 1;
        
        // 基础信息
        context.append("WRITES");
        if (writeCount > 1) {
            context.append(String.format(" (×%d)", writeCount));
        }
        context.append(": ");
        context.append(getSourceId()).append(" writes ").append(getTargetId());
        
        // 位置信息
        if (writeCount == 1) {
            context.append(String.format(" at line %d", getLineNumber()));
        } else {
            @SuppressWarnings("unchecked")
            java.util.List<Integer> locations = (java.util.List<Integer>) getProperty("writeLocations");
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
        
        // 初始化标记
        Boolean isInit = (Boolean) getProperty("isInitialization");
        if (isInit != null && isInit) {
            context.append(" [INIT]");
        }
        
        // 语义摘要
        String semanticSummary = (String) getProperty("semanticSummary");
        if (semanticSummary != null) {
            context.append(String.format(" (%s)", semanticSummary));
        }
        
        return context.toString();
    }
}

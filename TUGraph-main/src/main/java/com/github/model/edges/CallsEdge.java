package com.github.model.edges;

import com.github.model.Edge;

/**
 * 调用关系（Method→Method）
 * 表示方法调用另一个方法，包含调用上下文
 */
public class CallsEdge extends Edge {
    
    /**
     * 标准构造函数（用于反射创建边副本）
     * 
     * @param callerMethodId 调用者方法 ID
     * @param calleeMethodId 被调用者方法 ID
     */
    public CallsEdge(String callerMethodId, String calleeMethodId) {
        super(callerMethodId, calleeMethodId);
        setProperty("lineNumber", 0); // 默认行号
        setProperty("callCount", 1);
        setProperty("isRecursive", false);
        setProperty("isDynamic", false);
        setProperty("callType", "direct");
    }
    
    /**
     * 完整构造函数（用于创建新边）
     * 
     * @param callerMethodId 调用者方法 ID
     * @param calleeMethodId 被调用者方法 ID
     * @param lineNumber 调用发生的行号
     */
    public CallsEdge(String callerMethodId, String calleeMethodId, int lineNumber) {
        super(callerMethodId, calleeMethodId);
        setProperty("lineNumber", lineNumber);
        setProperty("callCount", 1);
        setProperty("isRecursive", false);
        setProperty("isDynamic", false); // 是否是动态调用（如反射）
        setProperty("callType", "direct"); // direct/virtual/interface/special/static
    }
    
    @Override
    public String getLabel() {
        return "CALLS";
    }
    
    @Override
    public String getEdgeType() {
        return "CALLS";
    }
    
    public int getLineNumber() {
        return (int) getProperty("lineNumber");
    }
    
    public CallsEdge incrementCallCount() {
        int count = (int) getProperty("callCount");
        setProperty("callCount", count + 1);
        return this;
    }
    
    public void setRecursive(boolean isRecursive) {
        setProperty("isRecursive", isRecursive);
    }
    
    public void setCallType(String callType) {
        setProperty("callType", callType);
    }
    
    /**
     * 设置方法调用的代码片段
     * 例如: "result = calculator.add(a, b);"
     */
    public void setCallStatement(String statement) {
        setContextSnippet(statement);
        setDescription("Method call at line " + getProperty("lineNumber"));
    }
    
    /**
     * 生成增强的上下文字符串（支持合并后的边）
     */
    public String toContextString() {
        StringBuilder context = new StringBuilder();
        
        // 获取调用计数
        Object countObj = getProperty("callCount");
        int callCount = (countObj instanceof Integer) ? (Integer) countObj : 1;
        
        // 基础信息
        context.append("CALLS");
        if (callCount > 1) {
            context.append(String.format(" (×%d)", callCount));
        }
        context.append(": ");
        context.append(getSourceId()).append(" → ").append(getTargetId());
        
        // 位置信息
        if (callCount == 1) {
            // 单次调用
            context.append(String.format(" at line %d", getLineNumber()));
        } else {
            // 多次调用 - 显示位置列表
            @SuppressWarnings("unchecked")
            java.util.List<Integer> locations = (java.util.List<Integer>) getProperty("callLocations");
            if (locations != null && !locations.isEmpty()) {
                if (locations.size() <= 5) {
                    context.append(" at lines ").append(
                        locations.stream()
                            .map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(", "))
                    );
                } else {
                    // 位置太多，只显示前3个和后2个
                    context.append(String.format(" at lines %d, %d, %d, ..., %d, %d",
                        locations.get(0),
                        locations.get(1),
                        locations.get(2),
                        locations.get(locations.size() - 2),
                        locations.get(locations.size() - 1)
                    ));
                }
            }
        }
        
        // 调用类型
        String callType = (String) getProperty("callType");
        if (callType != null && !callType.equals("direct")) {
            context.append(String.format(" [%s]", callType));
        }
        
        // 语义摘要
        String semanticSummary = (String) getProperty("semanticSummary");
        if (semanticSummary != null) {
            context.append(String.format(" (%s)", semanticSummary));
        }
        
        // 热点标记
        Boolean isHotspot = (Boolean) getProperty("isHotspot");
        if (isHotspot != null && isHotspot) {
            context.append(" ⚡HOTSPOT");
        }
        
        // 递归标记
        Boolean isRecursive = (Boolean) getProperty("isRecursive");
        if (isRecursive != null && isRecursive) {
            context.append(" 🔄RECURSIVE");
        }
        
        return context.toString();
    }
}

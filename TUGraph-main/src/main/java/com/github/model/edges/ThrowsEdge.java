package com.github.model.edges;

import com.github.model.Edge;

/**
 * 抛出异常关系（Method→Type）
 * 表示方法可能抛出某种异常
 */
public class ThrowsEdge extends Edge {
    
    public ThrowsEdge(String methodId, String exceptionTypeId) {
        super(methodId, exceptionTypeId);
        setProperty("isDeclared", true); // 是否在throws子句中声明
        setProperty("isChecked", true);  // 是否是受检异常
        setProperty("throwLocations", new java.util.ArrayList<Integer>()); // 抛出位置的行号
    }
    
    @Override
    public String getLabel() {
        return "THROWS";
    }
    
    @Override
    public String getEdgeType() {
        return "THROWS";
    }
    
    public void setDeclared(boolean isDeclared) {
        setProperty("isDeclared", isDeclared);
    }
    
    public void setChecked(boolean isChecked) {
        setProperty("isChecked", isChecked);
    }
    
    @SuppressWarnings("unchecked")
    public void addThrowLocation(int lineNumber) {
        java.util.List<Integer> locations = (java.util.List<Integer>) getProperty("throwLocations");
        locations.add(lineNumber);
    }
    
    /**
     * 设置异常抛出的代码片段
     */
    public void setThrowStatement(String statement) {
        setContextSnippet(statement);
        setDescription("Method throws exception");
    }
}

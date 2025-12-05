package com.github.model.edges;

import com.github.model.Edge;

/**
 * 返回类型关系（Method→Type）
 * 表示方法返回某种类型
 */
public class ReturnTypeEdge extends Edge {
    
    public ReturnTypeEdge(String methodId, String typeId) {
        super(methodId, typeId);
        setProperty("isGeneric", false);
        setProperty("genericInfo", "");
    }
    
    @Override
    public String getLabel() {
        return "RETURN_TYPE";
    }
    
    @Override
    public String getEdgeType() {
        return "RETURN_TYPE";
    }
    
    public void setGeneric(boolean isGeneric, String genericInfo) {
        setProperty("isGeneric", isGeneric);
        setProperty("genericInfo", genericInfo);
    }
    
    /**
     * 设置返回类型声明
     */
    public void setReturnTypeDeclaration(String declaration) {
        setContextSnippet(declaration);
        setDescription("Method return type");
    }
}

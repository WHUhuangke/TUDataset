package com.github.model.edges;

import com.github.model.Edge;

/**
 * 实现关系（Type→Type）
 * 表示类实现接口
 */
public class ImplementsEdge extends Edge {
    
    public ImplementsEdge(String implementorId, String interfaceId) {
        super(implementorId, interfaceId);
    }
    
    @Override
    public String getLabel() {
        return "IMPLEMENTS";
    }
    
    @Override
    public String getEdgeType() {
        return "IMPLEMENTS";
    }
    
    /**
     * 设置实现声明的代码片段
     */
    public void setImplementationDeclaration(String declaration) {
        setContextSnippet(declaration);
        setDescription("Interface implementation relationship");
    }
}

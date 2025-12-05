package com.github.model.edges;

import com.github.model.Edge;

/**
 * 继承关系（Type→Type）
 * 表示子类继承父类
 */
public class ExtendsEdge extends Edge {
    
    public ExtendsEdge(String subTypeId, String superTypeId) {
        super(subTypeId, superTypeId);
        setProperty("isDirectExtension", true);
    }
    
    @Override
    public String getLabel() {
        return "EXTENDS";
    }
    
    @Override
    public String getEdgeType() {
        return "EXTENDS";
    }
    
    /**
     * 设置继承的代码片段，如 "public class Child extends Parent"
     */
    public void setInheritanceDeclaration(String declaration) {
        setContextSnippet(declaration);
        setDescription("Class inheritance relationship");
    }
}

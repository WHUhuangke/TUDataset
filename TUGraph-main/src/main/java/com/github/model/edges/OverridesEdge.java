package com.github.model.edges;

import com.github.model.Edge;

/**
 * 重写关系（Method→Method）
 * 表示子类方法重写父类/接口方法
 */
public class OverridesEdge extends Edge {
    
    public OverridesEdge(String overridingMethodId, String overriddenMethodId) {
        super(overridingMethodId, overriddenMethodId);
        setProperty("hasAnnotation", false); // 是否有@Override注解
        setProperty("isInterfaceImpl", false); // 是否是接口实现
    }
    
    @Override
    public String getLabel() {
        return "OVERRIDES";
    }
    
    @Override
    public String getEdgeType() {
        return "OVERRIDES";
    }
    
    public void setHasAnnotation(boolean hasAnnotation) {
        setProperty("hasAnnotation", hasAnnotation);
    }
    
    public void setInterfaceImplementation(boolean isInterfaceImpl) {
        setProperty("isInterfaceImpl", isInterfaceImpl);
    }
    
    /**
     * 设置重写方法的签名对比
     */
    public void setOverrideContext(String overridingMethod, String overriddenMethod) {
        setContextSnippet("Overriding: " + overridingMethod + "\nOverridden: " + overriddenMethod);
        setDescription("Method override relationship");
    }
}

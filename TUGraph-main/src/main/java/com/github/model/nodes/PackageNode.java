package com.github.model.nodes;

import com.github.model.Node;

/**
 * 包节点 - 包含包级文档和结构信息
 */
public class PackageNode extends Node {
    
    public PackageNode(String qualifiedName) {
        this(qualifiedName, qualifiedName);
    }

    public PackageNode(String id, String qualifiedName) {
        super(id); // 使用全限定名作为ID
        initialize(qualifiedName);
    }

    private void initialize(String qualifiedName) {
        setProperty("qualifiedName", qualifiedName);
        setProperty("simpleName", extractSimpleName(qualifiedName));
        setProperty("typeCount", 0);
        setProperty("isTestPackage", qualifiedName.contains("test"));
    }
    
    @Override
    public String getLabel() {
        return "Package:" + getProperty("qualifiedName");
    }
    
    @Override
    public String getNodeType() {
        return "PACKAGE";
    }
    
    private String extractSimpleName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot > 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }
    
    public String getQualifiedName() {
        return (String) getProperty("qualifiedName");
    }
    
    public void incrementTypeCount() {
        int count = (int) getProperty("typeCount");
        setProperty("typeCount", count + 1);
    }
    
    /**
     * 设置包级别的文档（如package-info.java的内容）
     */
    public void setPackageDocumentation(String doc) {
        setDocumentation(doc);
        setSemanticSummary("Package containing " + getProperty("typeCount") + " types: " + doc);
    }
}

package com.github.model.nodes;

import com.github.model.Node;

/**
 * 文件节点 - 包含完整文件内容和导入信息
 */
public class FileNode extends Node {
    
    public FileNode(String absolutePath, String relativePath, String fileName) {
        this(absolutePath, absolutePath, relativePath, fileName);
    }

    public FileNode(String id, String absolutePath, String relativePath, String fileName) {
        super(id);
        initialize(absolutePath, relativePath, fileName);
    }

    private void initialize(String absolutePath, String relativePath, String fileName) {
        // 使用基类的路径字段
        setAbsolutePath(absolutePath);
        setRelativePath(relativePath);
        
        // FileNode特有属性
        setProperty("name", fileName);
        setProperty("packageName", "");
        setProperty("imports", new java.util.ArrayList<String>());
        setProperty("typeCount", 0);
        
        // 保持向后兼容（废弃，建议使用getAbsolutePath()）
        setProperty("path", absolutePath);
    }
    
    /**
     * 兼容旧版本的构造函数（只有绝对路径）
     * @deprecated 请使用 FileNode(String, String, String) 构造函数
     */
    @Deprecated
    public FileNode(String absolutePath, String fileName) {
        this(absolutePath, "", fileName);
    }
    
    @Override
    public String getLabel() {
        return "File:" + getProperty("name");
    }
    
    @Override
    public String getNodeType() {
        return "FILE";
    }
    
    /**
     * @deprecated 请使用 getAbsolutePath() 代替
     */
    @Deprecated
    public String getPath() {
        return getAbsolutePath();
    }
    
    public String getName() {
        return (String) getProperty("name");
    }
    
    public void setPackageName(String packageName) {
        setProperty("packageName", packageName);
    }
    
    @SuppressWarnings("unchecked")
    public void addImport(String importStatement) {
        java.util.List<String> imports = (java.util.List<String>) getProperty("imports");
        imports.add(importStatement);
    }
    
    /**
     * 设置完整的文件源代码
     */
    public void setFileContent(String content) {
        setSourceCode(content);
    }
    
    /**
     * 生成文件摘要用于LLM理解
     */
    public void generateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Java file: ").append(getName());
        summary.append(", Package: ").append(getProperty("packageName"));
        summary.append(", Contains ").append(getProperty("typeCount")).append(" type(s)");
        
        @SuppressWarnings("unchecked")
        java.util.List<String> imports = (java.util.List<String>) getProperty("imports");
        if (!imports.isEmpty()) {
            summary.append(", Imports: ").append(imports.size()).append(" classes");
        }
        
        setSemanticSummary(summary.toString());
    }
}

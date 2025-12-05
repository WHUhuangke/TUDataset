package com.github.model.edges;

import com.github.model.Edge;

/**
 * 项目包含文件关系（Project→File）
 * 表示项目包含某个源文件
 */
public class ContainsFileEdge extends Edge {
    
    public ContainsFileEdge(String projectId, String fileId) {
        super(projectId, fileId);
        setProperty("relativePath", "");
        setProperty("isSourceFile", true);
        setProperty("isTestFile", false);
    }
    
    @Override
    public String getLabel() {
        return "CONTAINS_FILE";
    }
    
    @Override
    public String getEdgeType() {
        return "CONTAINS_FILE";
    }
    
    public void setRelativePath(String relativePath) {
        setProperty("relativePath", relativePath);
    }
    
    public void setTestFile(boolean isTest) {
        setProperty("isTestFile", isTest);
        setProperty("isSourceFile", !isTest);
    }
    
    public void setFileContext(String fileName, String packageName) {
        setDescription("Project contains file: " + fileName + " in package " + packageName);
    }
}

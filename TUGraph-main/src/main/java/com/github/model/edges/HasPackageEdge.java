package com.github.model.edges;

import com.github.model.Edge;

/**
 * 项目包含包关系（Project→Package）
 * 表示项目包含某个包
 */
public class HasPackageEdge extends Edge {
    
    public HasPackageEdge(String projectId, String packageId) {
        super(projectId, packageId);
        setProperty("isTestPackage", false);
        setProperty("fileCount", 0);
        setProperty("typeCount", 0);
    }
    
    @Override
    public String getLabel() {
        return "HAS_PACKAGE";
    }
    
    @Override
    public String getEdgeType() {
        return "HAS_PACKAGE";
    }
    
    public void setTestPackage(boolean isTest) {
        setProperty("isTestPackage", isTest);
    }
    
    public void setFileCount(int count) {
        setProperty("fileCount", count);
    }
    
    public void setTypeCount(int count) {
        setProperty("typeCount", count);
    }
    
    public void setPackageContext(String packageName, int fileCount, int typeCount) {
        setDescription("Project has package: " + packageName + 
                      " with " + fileCount + " files and " + typeCount + " types");
    }
}

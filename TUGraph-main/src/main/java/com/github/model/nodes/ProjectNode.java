package com.github.model.nodes;

import com.github.model.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目节点 - 项目级元信息
 */
public class ProjectNode extends Node {
    
    public ProjectNode(String projectName, String version, String groupId, String artifactId) {
        super();
        setProperty("name", projectName);
        setProperty("version", version);
        setProperty("groupId", groupId);
        setProperty("artifactId", artifactId);
        setProperty("description", "");
        setProperty("dependencies", new ArrayList<String>());
        setProperty("buildTool", "maven"); // maven/gradle
    }
    
    @Override
    public String getLabel() {
        return "Project:" + getProperty("name");
    }
    
    @Override
    public String getNodeType() {
        return "PROJECT";
    }
    
    public String getName() {
        return (String) getProperty("name");
    }
    
    public String getVersion() {
        return (String) getProperty("version");
    }
    
    public void setDescription(String description) {
        setProperty("description", description);
        setSemanticSummary(description);
    }
    
    @SuppressWarnings("unchecked")
    public void addDependency(String dependency) {
        List<String> deps = (List<String>) getProperty("dependencies");
        deps.add(dependency);
    }
}

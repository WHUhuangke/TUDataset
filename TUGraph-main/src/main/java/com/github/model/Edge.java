package com.github.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 边抽象类 - 包含语义信息
 */
public abstract class Edge implements GraphElement {
    protected final String id;
    protected final String sourceId;
    protected final String targetId;
    protected final Map<String, Object> properties;
    
    // 语义信息
    protected String contextSnippet;    // 关系发生的上下文代码片段
    protected String description;       // 关系描述
    
    protected Edge(String sourceId, String targetId) {
        this.id = UUID.randomUUID().toString();
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.properties = new HashMap<>();
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    public String getSourceId() {
        return sourceId;
    }
    
    public String getTargetId() {
        return targetId;
    }
    
    @Override
    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }
    
    @Override
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    @Override
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    public abstract String getEdgeType();
    
    public String getContextSnippet() {
        return contextSnippet;
    }
    
    public void setContextSnippet(String contextSnippet) {
        this.contextSnippet = contextSnippet;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}

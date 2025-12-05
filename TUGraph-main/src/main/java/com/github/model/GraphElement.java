package com.github.model;

import java.util.Map;

/**
 * 图元素基础接口
 */
public interface GraphElement {
    String getId();
    String getLabel();
    Map<String, Object> getProperties();
    void setProperty(String key, Object value);
    Object getProperty(String key);
}

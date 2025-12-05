package com.github.model.nodes;

import com.github.model.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * 字段节点 - 包含字段定义和使用信息
 */
public class FieldNode extends Node {
    
    public FieldNode(String qualifiedName, String fieldName, String fieldType) {
        this(qualifiedName, qualifiedName, fieldName, fieldType);
    }

    public FieldNode(String id, String qualifiedName, String fieldName, String fieldType) {
        super(id);
        initialize(qualifiedName, fieldName, fieldType);
    }

    private void initialize(String qualifiedName, String fieldName, String fieldType) {
        setProperty("qualifiedName", qualifiedName);
        setProperty("name", fieldName);
        setProperty("type", fieldType);
        setProperty("isStatic", false);
        setProperty("isFinal", false);
        setProperty("isTransient", false);
        setProperty("isVolatile", false);
        setProperty("visibility", "private");
        setProperty("initialValue", "");
        setProperty("annotations", new ArrayList<String>());
        setProperty("readBy", new ArrayList<String>()); // 被哪些方法读取
        setProperty("writtenBy", new ArrayList<String>()); // 被哪些方法写入
        setProperty("lineNumber", 0);
        setProperty("isTestField", false); // 是否是测试字段
    }
    
    @Override
    public String getLabel() {
        return getProperty("name") + ": " + getProperty("type");
    }
    
    @Override
    public String getNodeType() {
        return "FIELD";
    }
    
    public String getName() {
        return (String) getProperty("name");
    }
    
    public String getFieldType() {
        return (String) getProperty("type");
    }
    
    public FieldNode setStatic(boolean isStatic) {
        setProperty("isStatic", isStatic);
        return this;
    }
    
    public FieldNode setFinal(boolean isFinal) {
        setProperty("isFinal", isFinal);
        return this;
    }
    
    public FieldNode setVisibility(String visibility) {
        setProperty("visibility", visibility);
        return this;
    }
    
    public FieldNode setTestField(boolean isTestField) {
        setProperty("isTestField", isTestField);
        return this;
    }
    
    public void setInitialValue(String value) {
        setProperty("initialValue", value);
    }
    
    public void setLineNumber(int lineNumber) {
        setProperty("lineNumber", lineNumber);
    }
    
    @SuppressWarnings("unchecked")
    public void addAnnotation(String annotation) {
        List<String> annotations = (List<String>) getProperty("annotations");
        annotations.add(annotation);
    }
    
    /**
     * 设置字段声明代码
     */
    public void setFieldDeclaration(String declaration) {
        setSourceCode(declaration);
    }
    
    /**
     * 设置字段的Javadoc注释
     */
    public void setJavadoc(String javadoc) {
        setDocumentation(javadoc);
    }
    
    /**
     * 生成字段的语义摘要
     */
    public void generateSemanticSummary() {
        StringBuilder summary = new StringBuilder();
        
        // 字段声明
        summary.append(getProperty("visibility")).append(" ");
        if ((boolean) getProperty("isStatic")) summary.append("static ");
        if ((boolean) getProperty("isFinal")) summary.append("final ");
        if ((boolean) getProperty("isTransient")) summary.append("transient ");
        if ((boolean) getProperty("isVolatile")) summary.append("volatile ");
        
        summary.append(getProperty("type")).append(" ");
        summary.append(getProperty("name"));
        
        String initValue = (String) getProperty("initialValue");
        if (initValue != null && !initValue.isEmpty()) {
            summary.append(" = ").append(initValue);
        }
        
        // 注解信息
        @SuppressWarnings("unchecked")
        List<String> annotations = (List<String>) getProperty("annotations");
        if (!annotations.isEmpty()) {
            summary.append(". Annotations: ").append(String.join(", ", annotations));
        }
        
        // 文档摘要
        if (documentation != null && !documentation.isEmpty()) {
            String docFirstLine = documentation.split("\n")[0].trim();
            if (!docFirstLine.isEmpty()) {
                summary.append(". ").append(docFirstLine);
            }
        }
        
        setSemanticSummary(summary.toString());
    }
}

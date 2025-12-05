package com.github.model.nodes;

import com.github.model.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * 类型节点（类、接口、枚举、注解）- 包含完整类代码和语义信息
 */
public class TypeNode extends Node {
    
    public enum TypeKind {
        CLASS,       // 普通类
        INTERFACE,   // 接口
        ENUM,        // 枚举
        ANNOTATION,  // 注解
        TEST_CLASS   // 测试类
    }
    
    public TypeNode(String qualifiedName, TypeKind kind) {
        this(qualifiedName, qualifiedName, kind);
    }

    public TypeNode(String id, String qualifiedName, TypeKind kind) {
        super(id);
        initialize(qualifiedName, kind);
    }

    private void initialize(String qualifiedName, TypeKind kind) {
        setProperty("qualifiedName", qualifiedName);
        setProperty("simpleName", extractSimpleName(qualifiedName));
        setProperty("kind", kind.name());
        setProperty("isAbstract", false);
        setProperty("isFinal", false);
        setProperty("isStatic", false);
        setProperty("visibility", "public");
        setProperty("superClass", "");
        setProperty("interfaces", new ArrayList<String>());
        setProperty("genericTypes", new ArrayList<String>());
        setProperty("annotations", new ArrayList<String>());
        setProperty("methodCount", 0);
        setProperty("fieldCount", 0);
        setProperty("lineStart", 0);
        setProperty("lineEnd", 0);
        setProperty("complexity", 0); // 圈复杂度
    }
    
    @Override
    public String getLabel() {
        return getProperty("simpleName") + " (" + getProperty("kind") + ")";
    }
    
    @Override
    public String getNodeType() {
        return "TYPE";
    }
    
    private String extractSimpleName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot > 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }
    
    public String getQualifiedName() {
        return (String) getProperty("qualifiedName");
    }
    
    public TypeKind getKind() {
        return TypeKind.valueOf((String) getProperty("kind"));
    }
    
    public TypeNode setAbstract(boolean isAbstract) {
        setProperty("isAbstract", isAbstract);
        return this;
    }
    
    public TypeNode setFinal(boolean isFinal) {
        setProperty("isFinal", isFinal);
        return this;
    }
    
    public TypeNode setStatic(boolean isStatic) {
        setProperty("isStatic", isStatic);
        return this;
    }
    
    public TypeNode setVisibility(String visibility) {
        setProperty("visibility", visibility);
        return this;
    }
    
    public void setSuperClass(String superClass) {
        setProperty("superClass", superClass);
    }
    
    @SuppressWarnings("unchecked")
    public void addInterface(String interfaceName) {
        List<String> interfaces = (List<String>) getProperty("interfaces");
        interfaces.add(interfaceName);
    }
    
    @SuppressWarnings("unchecked")
    public void addAnnotation(String annotation) {
        List<String> annotations = (List<String>) getProperty("annotations");
        annotations.add(annotation);
    }
    
    public void setLineRange(int start, int end) {
        setProperty("lineStart", start);
        setProperty("lineEnd", end);
    }
    
    public void setComplexity(int complexity) {
        setProperty("complexity", complexity);
    }
    
    /**
     * 设置完整的类源代码
     */
    public void setClassCode(String code) {
        setSourceCode(code);
    }
    
    /**
     * 设置类的Javadoc注释
     */
    public void setJavadoc(String javadoc) {
        setDocumentation(javadoc);
    }
    
    /**
     * 生成类的语义摘要，用于LLM快速理解
     */
    public void generateSemanticSummary() {
        StringBuilder summary = new StringBuilder();
        
        // 基本信息
        summary.append(getProperty("kind")).append(" ");
        summary.append(getProperty("simpleName"));
        
        // 继承信息
        String superClass = (String) getProperty("superClass");
        if (superClass != null && !superClass.isEmpty() && !superClass.equals("Object")) {
            summary.append(" extends ").append(superClass);
        }
        
        @SuppressWarnings("unchecked")
        List<String> interfaces = (List<String>) getProperty("interfaces");
        if (!interfaces.isEmpty()) {
            summary.append(" implements ").append(String.join(", ", interfaces));
        }
        
        // 成员统计
        summary.append(". Contains ");
        summary.append(getProperty("methodCount")).append(" method(s), ");
        summary.append(getProperty("fieldCount")).append(" field(s).");
        
        // 修饰符
        List<String> modifiers = new ArrayList<>();
        if ((boolean) getProperty("isAbstract")) modifiers.add("abstract");
        if ((boolean) getProperty("isFinal")) modifiers.add("final");
        if ((boolean) getProperty("isStatic")) modifiers.add("static");
        if (!modifiers.isEmpty()) {
            summary.append(" Modifiers: ").append(String.join(", ", modifiers)).append(".");
        }
        
        // 文档摘要
        if (documentation != null && !documentation.isEmpty()) {
            String docFirstLine = documentation.split("\n")[0].trim();
            if (!docFirstLine.isEmpty()) {
                summary.append(" ").append(docFirstLine);
            }
        }
        
        setSemanticSummary(summary.toString());
    }
    
    public void incrementMethodCount() {
        int count = (int) getProperty("methodCount");
        setProperty("methodCount", count + 1);
    }
    
    public void incrementFieldCount() {
        int count = (int) getProperty("fieldCount");
        setProperty("fieldCount", count + 1);
    }
}

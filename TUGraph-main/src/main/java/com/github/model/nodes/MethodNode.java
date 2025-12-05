package com.github.model.nodes;

import com.github.model.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * 方法节点 - 包含完整方法代码、控制流信息
 */
public class MethodNode extends Node {
    
    public enum MethodKind {
        SOURCE_METHOD,  // 普通源码方法（原 METHOD）
        CONSTRUCTOR,    // 构造函数
        TEST_METHOD     // 测试方法
    }
    
    public MethodNode(String signature, MethodKind kind) {
        this(signature, signature, kind);
    }

    public MethodNode(String id, String signature, MethodKind kind) {
        super(id);
        initialize(signature, kind);
    }

    private void initialize(String signature, MethodKind kind) {
        setProperty("signature", signature);
        setProperty("name", "");
        setProperty("kind", kind.name());
        setProperty("returnType", "void");
        setProperty("parameterTypes", new ArrayList<String>());
        setProperty("parameterNames", new ArrayList<String>());
        setProperty("exceptions", new ArrayList<String>());
        setProperty("annotations", new ArrayList<String>());
        setProperty("isAbstract", false);
        setProperty("isFinal", false);
        setProperty("isStatic", false);
        setProperty("isSynchronized", false);
        setProperty("visibility", "public");
        setProperty("lineStart", 0);
        setProperty("lineEnd", 0);
        setProperty("cyclomaticComplexity", 1);
        setProperty("linesOfCode", 0);
        setProperty("calledMethods", new ArrayList<String>());
        setProperty("accessedFields", new ArrayList<String>());
        setProperty("localVariables", new ArrayList<String>());
    }
    
    @Override
    public String getLabel() {
        return getProperty("name") + "(" + getProperty("kind") + ")";
    }
    
    @Override
    public String getNodeType() {
        return "METHOD";
    }
    
    public String getSignature() {
        return (String) getProperty("signature");
    }
    
    public MethodKind getKind() {
        return MethodKind.valueOf((String) getProperty("kind"));
    }
    
    public MethodNode setName(String name) {
        setProperty("name", name);
        return this;
    }
    
    public MethodNode setReturnType(String returnType) {
        setProperty("returnType", returnType);
        return this;
    }
    
    public MethodNode setAbstract(boolean isAbstract) {
        setProperty("isAbstract", isAbstract);
        return this;
    }
    
    public MethodNode setStatic(boolean isStatic) {
        setProperty("isStatic", isStatic);
        return this;
    }
    
    public MethodNode setVisibility(String visibility) {
        setProperty("visibility", visibility);
        return this;
    }
    
    public void setLineRange(int start, int end) {
        setProperty("lineStart", start);
        setProperty("lineEnd", end);
        setProperty("linesOfCode", end - start + 1);
    }
    
    public void setCyclomaticComplexity(int complexity) {
        setProperty("cyclomaticComplexity", complexity);
    }
    
    @SuppressWarnings("unchecked")
    public void addParameter(String type, String name) {
        List<String> types = (List<String>) getProperty("parameterTypes");
        List<String> names = (List<String>) getProperty("parameterNames");
        types.add(type);
        names.add(name);
    }
    
    @SuppressWarnings("unchecked")
    public void addException(String exceptionType) {
        List<String> exceptions = (List<String>) getProperty("exceptions");
        exceptions.add(exceptionType);
    }
    
    @SuppressWarnings("unchecked")
    public void addAnnotation(String annotation) {
        List<String> annotations = (List<String>) getProperty("annotations");
        annotations.add(annotation);
    }
    
    @SuppressWarnings("unchecked")
    public void addCalledMethod(String methodSignature) {
        List<String> called = (List<String>) getProperty("calledMethods");
        called.add(methodSignature);
    }
    
    @SuppressWarnings("unchecked")
    public void addAccessedField(String fieldName) {
        List<String> fields = (List<String>) getProperty("accessedFields");
        fields.add(fieldName);
    }
    
    /**
     * 设置完整的方法源代码
     */
    public void setMethodCode(String code) {
        setSourceCode(code);
    }
    
    /**
     * 设置方法的Javadoc注释
     */
    public void setJavadoc(String javadoc) {
        setDocumentation(javadoc);
    }
    
    /**
     * 生成方法的语义摘要，用于LLM理解方法功能
     */
    public void generateSemanticSummary() {
        StringBuilder summary = new StringBuilder();
        
        // 方法签名
        summary.append(getProperty("visibility")).append(" ");
        if ((boolean) getProperty("isStatic")) summary.append("static ");
        if ((boolean) getProperty("isAbstract")) summary.append("abstract ");
        if ((boolean) getProperty("isFinal")) summary.append("final ");
        
        if (getKind() == MethodKind.CONSTRUCTOR) {
            summary.append("constructor ");
        } else {
            summary.append(getProperty("returnType")).append(" ");
        }
        
        summary.append(getProperty("name")).append("(");
        
        @SuppressWarnings("unchecked")
        List<String> paramTypes = (List<String>) getProperty("parameterTypes");
        @SuppressWarnings("unchecked")
        List<String> paramNames = (List<String>) getProperty("parameterNames");
        
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) summary.append(", ");
            summary.append(paramTypes.get(i)).append(" ").append(paramNames.get(i));
        }
        summary.append(")");
        
        // 异常信息
        @SuppressWarnings("unchecked")
        List<String> exceptions = (List<String>) getProperty("exceptions");
        if (!exceptions.isEmpty()) {
            summary.append(" throws ").append(String.join(", ", exceptions));
        }
        
        // 复杂度信息
        summary.append(". Complexity: ").append(getProperty("cyclomaticComplexity"));
        summary.append(", LOC: ").append(getProperty("linesOfCode"));
        
        // 调用信息
        @SuppressWarnings("unchecked")
        List<String> calledMethods = (List<String>) getProperty("calledMethods");
        if (!calledMethods.isEmpty()) {
            summary.append(". Calls ").append(calledMethods.size()).append(" method(s)");
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

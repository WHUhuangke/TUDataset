package com.github.model.edges;

import com.github.model.Edge;
import java.util.List;

/**
 * 参数使用关系（Method→Type）
 * 表示方法使用某个类型作为参数
 * 
 * 这个关系直接连接方法和参数类型,不再需要中间的ParameterNode
 * 所有参数信息都作为边的属性存储
 */
public class UsesParameterEdge extends Edge {
    
    /**
     * 标准构造函数（用于反射创建边副本）
     */
    public UsesParameterEdge(String methodId, String paramTypeId) {
        super(methodId, paramTypeId);
        setProperty("paramName", "");
        setProperty("paramIndex", 0);
        setProperty("isFinal", false);
        setProperty("isVarArgs", false);
        setProperty("annotations", new java.util.ArrayList<String>());
        setProperty("semanticRole", "");
    }
    
    /**
     * 完整构造函数（用于创建新边）
     */
    public UsesParameterEdge(String methodId, String paramTypeId, int index, String paramName) {
        super(methodId, paramTypeId);
        setProperty("paramName", paramName);
        setProperty("paramIndex", index);
        setProperty("isFinal", false);
        setProperty("isVarArgs", false);
        setProperty("annotations", new java.util.ArrayList<String>());
        setProperty("semanticRole", ""); // entity, config, callback, dto, etc.
    }
    
    @Override
    public String getLabel() {
        return "USES_PARAMETER";
    }
    
    @Override
    public String getEdgeType() {
        return "USES_PARAMETER";
    }
    
    public String getParamName() {
        return (String) getProperty("paramName");
    }
    
    public int getParamIndex() {
        return (int) getProperty("paramIndex");
    }
    
    public boolean isFinal() {
        return (boolean) getProperty("isFinal");
    }
    
    public boolean isVarArgs() {
        return (boolean) getProperty("isVarArgs");
    }
    
    public UsesParameterEdge setFinal(boolean isFinal) {
        setProperty("isFinal", isFinal);
        return this;
    }
    
    public UsesParameterEdge setVarArgs(boolean isVarArgs) {
        setProperty("isVarArgs", isVarArgs);
        return this;
    }
    
    @SuppressWarnings("unchecked")
    public UsesParameterEdge addAnnotation(String annotation) {
        List<String> annotations = (List<String>) getProperty("annotations");
        annotations.add(annotation);
        return this;
    }
    
    /**
     * 设置参数的语义角色
     * @param role entity, config, callback, dto, builder, strategy等
     */
    public UsesParameterEdge setSemanticRole(String role) {
        setProperty("semanticRole", role);
        return this;
    }
    
    /**
     * 设置参数声明的代码片段
     */
    public void setParameterDeclaration(String declaration) {
        setContextSnippet(declaration);
        
        // 生成语义描述
        StringBuilder desc = new StringBuilder();
        desc.append("Parameter #").append(getProperty("paramIndex"));
        if (isFinal()) desc.append(" (final)");
        if (isVarArgs()) desc.append(" (varargs)");
        
        String role = (String) getProperty("semanticRole");
        if (!role.isEmpty()) {
            desc.append(" - Role: ").append(role);
        }
        
        setDescription(desc.toString());
    }
    
    /**
     * 生成用于LLM检索的上下文字符串
     */
    public String toContextString() {
        StringBuilder sb = new StringBuilder();
        sb.append("USES_PARAMETER: ");
        
        // 参数位置
        sb.append("#").append(getProperty("paramIndex")).append(" ");
        
        // 修饰符
        if (isFinal()) sb.append("final ");
        
        // 类型和名称
        sb.append(getTargetId()).append(" ").append(getProperty("paramName"));
        if (isVarArgs()) sb.append("...");
        
        // 语义角色
        String role = (String) getProperty("semanticRole");
        if (!role.isEmpty()) {
            sb.append(" [").append(role).append("]");
        }
        
        // 注解
        @SuppressWarnings("unchecked")
        List<String> annotations = (List<String>) getProperty("annotations");
        if (!annotations.isEmpty()) {
            sb.append(" ").append(annotations);
        }
        
        return sb.toString();
    }
}

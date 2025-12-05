package com.github.model.edges;

import com.github.model.Edge;

/**
 * 文件声明类型关系（File→Type）
 * 表示文件中声明了某个类型
 */
public class DeclaresTypeEdge extends Edge {
    
    public DeclaresTypeEdge(String fileId, String typeId) {
        super(fileId, typeId);
        setProperty("isPrimaryType", false); // 是否是主类型（与文件名相同）
        setProperty("lineStart", 0);
        setProperty("lineEnd", 0);
    }
    
    @Override
    public String getLabel() {
        return "DECLARES_TYPE";
    }
    
    @Override
    public String getEdgeType() {
        return "DECLARES_TYPE";
    }
    
    public void setPrimaryType(boolean isPrimary) {
        setProperty("isPrimaryType", isPrimary);
    }
    
    public void setLineRange(int start, int end) {
        setProperty("lineStart", start);
        setProperty("lineEnd", end);
    }
    
    public void setTypeDeclarationContext(String declaration) {
        setContextSnippet(declaration);
        setDescription("File declares a type");
    }
}

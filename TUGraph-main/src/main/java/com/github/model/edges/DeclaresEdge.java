package com.github.model.edges;

import com.github.model.Edge;

/**
 * 声明关系（Type→Method/Field）
 * 表示类型声明了某个成员
 */
public class DeclaresEdge extends Edge {
    
    public DeclaresEdge(String typeId, String memberId) {
        super(typeId, memberId);
        setProperty("memberKind", ""); // method/field/constructor
    }
    
    @Override
    public String getLabel() {
        return "DECLARES";
    }
    
    @Override
    public String getEdgeType() {
        return "DECLARES";
    }
    
    public void setMemberKind(String kind) {
        setProperty("memberKind", kind);
        setDescription("Type declares a " + kind);
    }
}

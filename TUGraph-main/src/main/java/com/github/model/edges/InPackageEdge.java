package com.github.model.edges;

import com.github.model.Edge;

/**
 * 所属包关系（Type→Package）
 * 表示类型属于某个包
 */
public class InPackageEdge extends Edge {
    
    public InPackageEdge(String typeId, String packageId) {
        super(typeId, packageId);
        setDescription("Type belongs to package");
    }
    
    @Override
    public String getLabel() {
        return "IN_PACKAGE";
    }
    
    @Override
    public String getEdgeType() {
        return "IN_PACKAGE";
    }
}

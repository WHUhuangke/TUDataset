package com.github.model.edges.evolution;

import com.github.model.EvolutionEdge;

/**
 * 内联边 - 表示代码被内联到其他位置
 * 
 * <p>适用场景：
 * <ul>
 *   <li>内联方法：将简单方法的内容直接展开到调用处</li>
 *   <li>内联变量：将变量直接替换为其值</li>
 *   <li>内联类：将小类的内容合并到使用它的类中</li>
 * </ul>
 * 
 * <p>对应 RefactoringMiner 类型：
 * <ul>
 *   <li>INLINE_METHOD</li>
 *   <li>INLINE_VARIABLE</li>
 * </ul>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class InlinedEdge extends EvolutionEdge {
    
    /**
     * 目标位置（被内联到的位置）
     */
    private String targetLocation;
    
    /**
     * 内联类型（METHOD, VARIABLE等）
     */
    private String inlineType;
    
    public InlinedEdge(String fromNodeId, String toNodeId) {
        super(fromNodeId, toNodeId);
    }
    
    public InlinedEdge(String fromNodeId, String toNodeId, String fromVersion, String toVersion) {
        super(fromNodeId, toNodeId, fromVersion, toVersion);
    }
    
    @Override
    public String getEdgeType() {
        return "INLINED";
    }
    
    public String getTargetLocation() {
        return targetLocation;
    }
    
    public void setTargetLocation(String targetLocation) {
        this.targetLocation = targetLocation;
        setProperty("targetLocation", targetLocation);
    }
    
    public String getInlineType() {
        return inlineType;
    }
    
    public void setInlineType(String inlineType) {
        this.inlineType = inlineType;
        setProperty("inlineType", inlineType);
    }
}

package com.github.model.edges.evolution;

import com.github.model.EvolutionEdge;

/**
 * 提取边 - 表示代码被提取为独立单元
 * 
 * <p>适用场景：
 * <ul>
 *   <li>提取方法：从长方法中提取出新方法</li>
 *   <li>提取类：从大类中提取出新类</li>
 *   <li>提取接口：从类中提取出接口</li>
 *   <li>提取超类：将共同代码提取到超类</li>
 * </ul>
 * 
 * <p>对应 RefactoringMiner 类型：
 * <ul>
 *   <li>EXTRACT_METHOD</li>
 *   <li>EXTRACT_CLASS</li>
 *   <li>EXTRACT_INTERFACE</li>
 *   <li>EXTRACT_SUPERCLASS</li>
 *   <li>EXTRACT_VARIABLE</li>
 *   <li>EXTRACT_AND_MOVE_METHOD</li>
 * </ul>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class ExtractedEdge extends EvolutionEdge {
    
    /**
     * 源节点位置（被提取的代码所在位置）
     */
    private String sourceLocation;
    
    /**
     * 提取类型（METHOD, CLASS, INTERFACE, SUPERCLASS等）
     */
    private String extractType;
    
    public ExtractedEdge(String fromNodeId, String toNodeId) {
        super(fromNodeId, toNodeId);
    }
    
    public ExtractedEdge(String fromNodeId, String toNodeId, String fromVersion, String toVersion) {
        super(fromNodeId, toNodeId, fromVersion, toVersion);
    }
    
    @Override
    public String getEdgeType() {
        return "EXTRACTED";
    }
    
    public String getSourceLocation() {
        return sourceLocation;
    }
    
    public void setSourceLocation(String sourceLocation) {
        this.sourceLocation = sourceLocation;
        setProperty("sourceLocation", sourceLocation);
    }
    
    public String getExtractType() {
        return extractType;
    }
    
    public void setExtractType(String extractType) {
        this.extractType = extractType;
        setProperty("extractType", extractType);
    }
}

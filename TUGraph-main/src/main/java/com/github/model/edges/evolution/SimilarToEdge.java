package com.github.model.edges.evolution;

import com.github.model.EvolutionEdge;

/**
 * 相似边 - 表示基于结构相似度的节点匹配
 * 
 * <p>当 RefactoringMiner 无法识别明确的重构操作时，
 * 使用结构相似度算法（基于 AST 结构、方法调用、字段访问等）来推测节点的对应关系。
 * 
 * <p>适用场景：
 * <ul>
 *   <li>大规模重构后的方法匹配</li>
 *   <li>类结构发生重大变化时的匹配</li>
 *   <li>RefactoringMiner 无法识别的隐含对应关系</li>
 * </ul>
 * 
 * <p><b>注意：</b>此类型的边置信度 < 1.0，表示基于推测的匹配。
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class SimilarToEdge extends EvolutionEdge {
    
    /**
     * 相似度分数（0.0 - 1.0）
     * 与 confidence 字段含义相同，但特指结构相似度
     */
    private double similarityScore;
    
    /**
     * 相似度计算依据（例如：AST结构、方法调用、控制流等）
     */
    private String similarityBasis;
    
    public SimilarToEdge(String fromNodeId, String toNodeId) {
        super(fromNodeId, toNodeId);
        // SimilarTo 边默认置信度较低
        this.confidence = 0.0;
    }
    
    public SimilarToEdge(String fromNodeId, String toNodeId, String fromVersion, String toVersion) {
        super(fromNodeId, toNodeId, fromVersion, toVersion);
        this.confidence = 0.0;
    }
    
    @Override
    public String getEdgeType() {
        return "SIMILAR_TO";
    }
    
    public double getSimilarityScore() {
        return similarityScore;
    }
    
    public void setSimilarityScore(double similarityScore) {
        if (similarityScore < 0.0 || similarityScore > 1.0) {
            throw new IllegalArgumentException("Similarity score must be between 0.0 and 1.0");
        }
        this.similarityScore = similarityScore;
        // 同步更新 confidence
        setConfidence(similarityScore);
        setProperty("similarityScore", similarityScore);
    }
    
    public String getSimilarityBasis() {
        return similarityBasis;
    }
    
    public void setSimilarityBasis(String similarityBasis) {
        this.similarityBasis = similarityBasis;
        setProperty("similarityBasis", similarityBasis);
    }
}

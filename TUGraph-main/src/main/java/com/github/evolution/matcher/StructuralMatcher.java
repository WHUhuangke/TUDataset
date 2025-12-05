package com.github.evolution.matcher;

import com.github.model.Node;
import com.github.model.nodes.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 结构匹配器 - 基于节点结构相似度进行匹配
 * 
 * <p>匹配规则：
 * <ul>
 *   <li>METHOD节点：比较方法体的结构特征（代码长度、圈复杂度、调用的方法）</li>
 *   <li>TYPE节点：比较类的结构特征（方法数量、字段数量）</li>
 *   <li>FIELD节点：暂不支持（需要更复杂的分析）</li>
 * </ul>
 * 
 * <p>置信度计算：
 * <ul>
 *   <li>0.7-0.79 - 结构高度相似</li>
 *   <li>0.6-0.69 - 结构中等相似</li>
 *   <li>0.5-0.59 - 结构略微相似</li>
 *   <li>< 0.5 - 结构不相似</li>
 * </ul>
 * 
 * <p><b>注意：</b>这是一个简化版本的结构匹配器，
 * 未来可以扩展为使用 AST 差异算法（如 GumTree）进行更精确的匹配。
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class StructuralMatcher implements NodeMatcher {
    
    /**
     * 相似度阈值
     */
    private static final double SIMILARITY_THRESHOLD = 0.5;
    
    @Override
    public boolean match(Node v1Node, Node v2Node) {
        return getConfidence(v1Node, v2Node) >= SIMILARITY_THRESHOLD;
    }
    
    @Override
    public double getConfidence(Node v1Node, Node v2Node) {
        // 节点类型必须相同
        if (!v1Node.getNodeType().equals(v2Node.getNodeType())) {
            return 0.0;
        }
        
        String nodeType = v1Node.getNodeType();
        
        switch (nodeType) {
            case "METHOD":
                return matchMethodStructure((MethodNode) v1Node, (MethodNode) v2Node);
            case "TYPE":
                return matchTypeStructure((TypeNode) v1Node, (TypeNode) v2Node);
            default:
                return 0.0;
        }
    }
    
    @Override
    public String getName() {
        return "StructuralMatcher";
    }
    
    @Override
    public int getPriority() {
        return 10;  // 最低优先级
    }
    
    @Override
    public boolean supports(String nodeType) {
        return nodeType.equals("METHOD") || nodeType.equals("TYPE");
    }
    
    /**
     * 匹配方法结构
     * 
     * <p>比较多个结构特征：
     * <ul>
     *   <li>代码行数</li>
     *   <li>圈复杂度</li>
     *   <li>调用的方法集合</li>
     *   <li>访问的字段集合</li>
     *   <li>局部变量数量</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private double matchMethodStructure(MethodNode v1, MethodNode v2) {
        double totalScore = 0.0;
        int featureCount = 0;
        
        // 特征 1: 代码行数相似度（权重 0.2）
        int v1Lines = (int) v1.getProperty("linesOfCode");
        int v2Lines = (int) v2.getProperty("linesOfCode");
        if (v1Lines > 0 && v2Lines > 0) {
            double linesSimilarity = 1.0 - Math.abs(v1Lines - v2Lines) / (double) Math.max(v1Lines, v2Lines);
            totalScore += linesSimilarity * 0.2;
            featureCount++;
        }
        
        // 特征 2: 圈复杂度相似度（权重 0.2）
        int v1Complexity = (int) v1.getProperty("cyclomaticComplexity");
        int v2Complexity = (int) v2.getProperty("cyclomaticComplexity");
        if (v1Complexity > 0 && v2Complexity > 0) {
            double complexitySimilarity = 1.0 - Math.abs(v1Complexity - v2Complexity) / (double) Math.max(v1Complexity, v2Complexity);
            totalScore += complexitySimilarity * 0.2;
            featureCount++;
        }
        
        // 特征 3: 调用方法的 Jaccard 相似度（权重 0.3）
        List<String> v1CalledMethods = (List<String>) v1.getProperty("calledMethods");
        List<String> v2CalledMethods = (List<String>) v2.getProperty("calledMethods");
        if (v1CalledMethods != null && v2CalledMethods != null && 
            !v1CalledMethods.isEmpty() && !v2CalledMethods.isEmpty()) {
            double callsSimilarity = jaccardSimilarity(v1CalledMethods, v2CalledMethods);
            totalScore += callsSimilarity * 0.3;
            featureCount++;
        }
        
        // 特征 4: 访问字段的 Jaccard 相似度（权重 0.2）
        List<String> v1AccessedFields = (List<String>) v1.getProperty("accessedFields");
        List<String> v2AccessedFields = (List<String>) v2.getProperty("accessedFields");
        if (v1AccessedFields != null && v2AccessedFields != null && 
            !v1AccessedFields.isEmpty() && !v2AccessedFields.isEmpty()) {
            double fieldsSimilarity = jaccardSimilarity(v1AccessedFields, v2AccessedFields);
            totalScore += fieldsSimilarity * 0.2;
            featureCount++;
        }
        
        // 特征 5: 局部变量数量相似度（权重 0.1）
        List<String> v1LocalVars = (List<String>) v1.getProperty("localVariables");
        List<String> v2LocalVars = (List<String>) v2.getProperty("localVariables");
        if (v1LocalVars != null && v2LocalVars != null) {
            int v1VarCount = v1LocalVars.size();
            int v2VarCount = v2LocalVars.size();
            if (v1VarCount > 0 && v2VarCount > 0) {
                double varSimilarity = 1.0 - Math.abs(v1VarCount - v2VarCount) / (double) Math.max(v1VarCount, v2VarCount);
                totalScore += varSimilarity * 0.1;
                featureCount++;
            }
        }
        
        // 如果没有任何特征可以比较，返回 0
        if (featureCount == 0) {
            return 0.0;
        }
        
        // 归一化到 0-1 范围
        return totalScore;
    }
    
    /**
     * 匹配类型结构
     * 
     * <p>比较类的结构特征（简化版本）
     */
    private double matchTypeStructure(TypeNode v1, TypeNode v2) {
        // 简化版本：比较类的类型和一些基本属性
        String v1Kind = (String) v1.getProperty("kind");
        String v2Kind = (String) v2.getProperty("kind");
        
        if (!v1Kind.equals(v2Kind)) {
            return 0.0;
        }
        
        // TODO: 未来可以比较：
        // - 方法数量
        // - 字段数量
        // - 继承关系
        // - 实现的接口
        // 这需要访问图结构
        
        return 0.0;
    }
    
    /**
     * 计算两个集合的 Jaccard 相似度
     * 
     * <p>Jaccard 相似度 = |A ∩ B| / |A ∪ B|
     * 
     * @param list1 列表 1
     * @param list2 列表 2
     * @return Jaccard 相似度（0.0 - 1.0）
     */
    private double jaccardSimilarity(List<String> list1, List<String> list2) {
        if (list1.isEmpty() && list2.isEmpty()) {
            return 1.0;
        }
        
        Set<String> set1 = new HashSet<>(list1);
        Set<String> set2 = new HashSet<>(list2);
        
        // 交集
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        // 并集
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        if (union.isEmpty()) {
            return 0.0;
        }
        
        return (double) intersection.size() / union.size();
    }
}

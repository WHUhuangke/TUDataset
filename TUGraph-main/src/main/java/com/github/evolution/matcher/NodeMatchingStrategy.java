package com.github.evolution.matcher;

import com.github.evolution.NodeMapping;
import com.github.logging.GraphLogger;
import com.github.model.KnowledgeGraph;
import com.github.model.Node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 节点匹配策略 - 串联多个匹配器进行分层匹配
 * 
 * <p>匹配流程：
 * <ol>
 *   <li>按优先级排序所有匹配器</li>
 *   <li>对每个 V1 节点，依次尝试各个匹配器</li>
 *   <li>找到第一个置信度 >= 阈值的匹配即停止</li>
 *   <li>记录映射关系和置信度</li>
 * </ol>
 * 
 * <p><b>默认匹配器顺序：</b>
 * <ol>
 *   <li>{@link ExactMatcher} (优先级 100) - 精确匹配</li>
 *   <li>{@link SignatureBasedMatcher} (优先级 50) - 签名匹配</li>
 *   <li>{@link StructuralMatcher} (优先级 10) - 结构匹配</li>
 * </ol>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class NodeMatchingStrategy {
    
    private static final GraphLogger logger = GraphLogger.getInstance();
    
    /**
     * 置信度阈值（只记录 >= 该值的映射）
     */
    private static final double CONFIDENCE_THRESHOLD = 0.5;
    
    /**
     * 匹配器列表（按优先级排序）
     */
    private final List<NodeMatcher> matchers;
    
    /**
     * 构造函数 - 使用默认匹配器
     */
    public NodeMatchingStrategy() {
        this.matchers = new ArrayList<>();
        
        // 添加默认匹配器
        matchers.add(new ExactMatcher());
        // matchers.add(new SignatureBasedMatcher());  // 暂时注释，类不存在
        matchers.add(new StructuralMatcher());
        
        // 按优先级排序（高优先级在前）
        matchers.sort(Comparator.comparingInt(NodeMatcher::getPriority).reversed());
        
        logger.info("初始化节点匹配策略，匹配器数量: " + matchers.size());
        for (NodeMatcher matcher : matchers) {
            logger.debug("  - " + matcher.getName() + " (优先级: " + matcher.getPriority() + ")");
        }
    }
    
    /**
     * 构造函数 - 自定义匹配器列表
     * 
     * @param matchers 匹配器列表
     */
    public NodeMatchingStrategy(List<NodeMatcher> matchers) {
        this.matchers = new ArrayList<>(matchers);
        this.matchers.sort(Comparator.comparingInt(NodeMatcher::getPriority).reversed());
    }
    
    /**
     * 执行节点匹配
     * 
     * @param v1Graph V1 版本的知识图谱
     * @param v2Graph V2 版本的知识图谱
     * @return 节点映射关系
     */
    public NodeMapping match(KnowledgeGraph v1Graph, KnowledgeGraph v2Graph) {
        logger.info("========================================");
        logger.info("开始节点匹配");
        logger.info("========================================");
        logger.info("V1 图谱节点数: " + v1Graph.getNodeCount());
        logger.info("V2 图谱节点数: " + v2Graph.getNodeCount());
        logger.info("");
        
        NodeMapping mapping = new NodeMapping();
        
        // 统计各类型匹配结果
        int[] matchCounts = new int[matchers.size()];
        int totalMatched = 0;
        
        // 遍历 V1 的所有节点
        for (Node v1Node : v1Graph.getAllNodes()) {
            String nodeType = v1Node.getNodeType();
            
            // 尝试在 V2 中找到匹配的节点
            Node bestMatch = null;
            double bestConfidence = 0.0;
            int bestMatcherIndex = -1;
            
            // 依次尝试各个匹配器
            for (int i = 0; i < matchers.size(); i++) {
                NodeMatcher matcher = matchers.get(i);
                
                // 检查匹配器是否支持该节点类型
                if (!matcher.supports(nodeType)) {
                    continue;
                }
                
                // 在 V2 中查找最佳匹配
                for (Node v2Node : v2Graph.getNodesByType(nodeType)) {
                    // 如果 V2 节点已经被映射，跳过
                    if (mapping.hasMappingForV2(v2Node.getId())) {
                        continue;
                    }
                    
                    double confidence = matcher.getConfidence(v1Node, v2Node);
                    
                    if (confidence > bestConfidence) {
                        bestConfidence = confidence;
                        bestMatch = v2Node;
                        bestMatcherIndex = i;
                    }
                }
                
                // 如果找到高置信度匹配，停止尝试后续匹配器
                if (bestConfidence >= 0.8) {
                    break;
                }
            }
            
            // 记录映射关系
            if (bestMatch != null && bestConfidence >= CONFIDENCE_THRESHOLD) {
                mapping.addMapping(v1Node, bestMatch, bestConfidence);
                matchCounts[bestMatcherIndex]++;
                totalMatched++;
                
                logger.debug(String.format(
                    "映射: %s (%s) → %s (置信度: %.2f, 匹配器: %s)",
                    v1Node.getLabel(),
                    nodeType,
                    bestMatch.getLabel(),
                    bestConfidence,
                    matchers.get(bestMatcherIndex).getName()
                ));
            }
        }
        
        // 打印统计信息
        logger.info("");
        logger.info("========================================");
        logger.info("节点匹配完成");
        logger.info("========================================");
        logger.info("总映射数: " + totalMatched);
        logger.info("映射率: " + String.format("%.1f%%", 
            100.0 * totalMatched / v1Graph.getNodeCount()));
        logger.info("");
        logger.info("各匹配器结果:");
        for (int i = 0; i < matchers.size(); i++) {
            logger.info(String.format("  - %s: %d 个映射", 
                matchers.get(i).getName(), matchCounts[i]));
        }
        logger.info("");
        logger.info(mapping.getStatistics());
        logger.info("");
        
        return mapping;
    }
    
    /**
     * 添加自定义匹配器
     * 
     * @param matcher 匹配器
     */
    public void addMatcher(NodeMatcher matcher) {
        matchers.add(matcher);
        matchers.sort(Comparator.comparingInt(NodeMatcher::getPriority).reversed());
    }
    
    /**
     * 获取所有匹配器
     * 
     * @return 匹配器列表
     */
    public List<NodeMatcher> getMatchers() {
        return new ArrayList<>(matchers);
    }
}

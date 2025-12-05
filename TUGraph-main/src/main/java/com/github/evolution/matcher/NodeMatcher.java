package com.github.evolution.matcher;

import com.github.model.Node;

/**
 * 节点匹配器接口 - 定义节点匹配规则
 * 
 * <p>用于判断两个版本的节点是否匹配，以及计算匹配置信度。
 * 不同的实现类可以使用不同的匹配策略：
 * <ul>
 *   <li>{@link ExactMatcher} - 精确匹配（全限定名 + 签名完全相同）</li>
 *   <li>{@link SignatureBasedMatcher} - 基于签名匹配（处理重命名）</li>
 *   <li>{@link StructuralMatcher} - 基于结构相似度匹配</li>
 * </ul>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public interface NodeMatcher {
    
    /**
     * 判断两个节点是否匹配
     * 
     * @param v1Node V1 版本的节点
     * @param v2Node V2 版本的节点
     * @return 是否匹配
     */
    boolean match(Node v1Node, Node v2Node);
    
    /**
     * 计算两个节点的匹配置信度
     * 
     * <p>置信度范围：
     * <ul>
     *   <li>1.0 - 完全匹配（精确匹配）</li>
     *   <li>0.8-0.99 - 高置信度匹配（签名匹配）</li>
     *   <li>0.5-0.79 - 中等置信度匹配（结构相似）</li>
     *   <li>< 0.5 - 低置信度匹配（不推荐使用）</li>
     *   <li>0.0 - 不匹配</li>
     * </ul>
     * 
     * @param v1Node V1 版本的节点
     * @param v2Node V2 版本的节点
     * @return 置信度（0.0 - 1.0）
     */
    double getConfidence(Node v1Node, Node v2Node);
    
    /**
     * 获取匹配器名称
     * 
     * @return 匹配器名称
     */
    String getName();
    
    /**
     * 获取匹配器优先级
     * 
     * <p>优先级越高，越先执行。建议值：
     * <ul>
     *   <li>100 - 精确匹配器</li>
     *   <li>50 - 签名匹配器</li>
     *   <li>10 - 结构匹配器</li>
     * </ul>
     * 
     * @return 优先级
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * 判断是否支持指定类型的节点
     * 
     * @param nodeType 节点类型（METHOD, TYPE, FIELD 等）
     * @return 是否支持
     */
    default boolean supports(String nodeType) {
        return true;  // 默认支持所有类型
    }
}

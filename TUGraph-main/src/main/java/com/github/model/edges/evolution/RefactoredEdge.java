package com.github.model.edges.evolution;

import com.github.model.EvolutionEdge;

/**
 * 重构边 - 表示一般性重构操作
 * 
 * <p>用于表示其他未被具体分类的重构操作，或复杂的组合重构。
 * 
 * <p>适用场景：
 * <ul>
 *   <li>Pull Up Method/Field：方法或字段上移到父类</li>
 *   <li>Push Down Method/Field：方法或字段下移到子类</li>
 *   <li>Replace Type Code：替换类型码</li>
 *   <li>Encapsulate Field：封装字段</li>
 *   <li>Replace Magic Number：替换魔术数字</li>
 *   <li>其他复杂重构操作</li>
 * </ul>
 * 
 * <p>对应 RefactoringMiner 类型：
 * <ul>
 *   <li>PULL_UP_METHOD</li>
 *   <li>PULL_UP_ATTRIBUTE</li>
 *   <li>PUSH_DOWN_METHOD</li>
 *   <li>PUSH_DOWN_ATTRIBUTE</li>
 *   <li>REPLACE_VARIABLE_WITH_ATTRIBUTE</li>
 *   <li>其他未被特殊分类的重构类型</li>
 * </ul>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class RefactoredEdge extends EvolutionEdge {
    
    /**
     * 重构详情（具体的重构操作说明）
     */
    private String refactoringDetails;
    
    public RefactoredEdge(String fromNodeId, String toNodeId) {
        super(fromNodeId, toNodeId);
    }
    
    public RefactoredEdge(String fromNodeId, String toNodeId, String fromVersion, String toVersion) {
        super(fromNodeId, toNodeId, fromVersion, toVersion);
    }
    
    @Override
    public String getEdgeType() {
        return "REFACTORED";
    }
    
    public String getRefactoringDetails() {
        return refactoringDetails;
    }
    
    public void setRefactoringDetails(String refactoringDetails) {
        this.refactoringDetails = refactoringDetails;
        setProperty("refactoringDetails", refactoringDetails);
    }
}

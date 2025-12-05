package com.github.model.edges.evolution;

import com.github.model.EvolutionEdge;

/**
 * 签名变化边 - 表示方法/构造器的签名发生变化
 * 
 * <p>适用场景：
 * <ul>
 *   <li>添加参数：method(a) → method(a, b)</li>
 *   <li>删除参数：method(a, b) → method(a)</li>
 *   <li>修改参数类型：method(int a) → method(long a)</li>
 *   <li>修改返回类型：int method() → long method()</li>
 *   <li>添加异常声明：method() → method() throws Exception</li>
 *   <li>修改访问修饰符：private → public</li>
 * </ul>
 * 
 * <p>对应 RefactoringMiner 类型：
 * <ul>
 *   <li>CHANGE_PARAMETER_TYPE</li>
 *   <li>CHANGE_RETURN_TYPE</li>
 *   <li>ADD_PARAMETER</li>
 *   <li>REMOVE_PARAMETER</li>
 *   <li>REORDER_PARAMETER</li>
 *   <li>ADD_THROWN_EXCEPTION_TYPE</li>
 *   <li>REMOVE_THROWN_EXCEPTION_TYPE</li>
 *   <li>CHANGE_METHOD_ACCESS_MODIFIER</li>
 * </ul>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class ChangedSignatureEdge extends EvolutionEdge {
    
    /**
     * 旧签名
     */
    private String oldSignature;
    
    /**
     * 新签名
     */
    private String newSignature;
    
    /**
     * 签名变化类型（PARAMETER, RETURN_TYPE, EXCEPTION, MODIFIER等）
     */
    private String changeType;
    
    public ChangedSignatureEdge(String fromNodeId, String toNodeId) {
        super(fromNodeId, toNodeId);
    }
    
    public ChangedSignatureEdge(String fromNodeId, String toNodeId, String fromVersion, String toVersion) {
        super(fromNodeId, toNodeId, fromVersion, toVersion);
    }
    
    @Override
    public String getEdgeType() {
        return "CHANGED_SIGNATURE";
    }
    
    public String getOldSignature() {
        return oldSignature;
    }
    
    public void setOldSignature(String oldSignature) {
        this.oldSignature = oldSignature;
        setProperty("oldSignature", oldSignature);
    }
    
    public String getNewSignature() {
        return newSignature;
    }
    
    public void setNewSignature(String newSignature) {
        this.newSignature = newSignature;
        setProperty("newSignature", newSignature);
    }
    
    public String getChangeType() {
        return changeType;
    }
    
    public void setChangeType(String changeType) {
        this.changeType = changeType;
        setProperty("changeType", changeType);
    }
}

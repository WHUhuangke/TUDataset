package com.github.model.edges.evolution;

import com.github.model.EvolutionEdge;

/**
 * 重命名边 - 表示节点被重命名
 * 
 * <p>适用场景：
 * <ul>
 *   <li>类重命名：Person → User</li>
 *   <li>方法重命名：calculateTotal → computeSum</li>
 *   <li>字段重命名：userName → userId</li>
 *   <li>包重命名：com.old → com.new</li>
 * </ul>
 * 
 * <p>对应 RefactoringMiner 类型：
 * <ul>
 *   <li>RENAME_CLASS</li>
 *   <li>RENAME_METHOD</li>
 *   <li>RENAME_ATTRIBUTE</li>
 *   <li>RENAME_PACKAGE</li>
 *   <li>RENAME_PARAMETER</li>
 *   <li>RENAME_VARIABLE</li>
 * </ul>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class RenamedEdge extends EvolutionEdge {
    
    /**
     * 旧名称
     */
    private String oldName;
    
    /**
     * 新名称
     */
    private String newName;
    
    public RenamedEdge(String fromNodeId, String toNodeId) {
        super(fromNodeId, toNodeId);
    }
    
    public RenamedEdge(String fromNodeId, String toNodeId, String fromVersion, String toVersion) {
        super(fromNodeId, toNodeId, fromVersion, toVersion);
    }
    
    @Override
    public String getEdgeType() {
        return "RENAMED";
    }
    
    public String getOldName() {
        return oldName;
    }
    
    public void setOldName(String oldName) {
        this.oldName = oldName;
        setProperty("oldName", oldName);
    }
    
    public String getNewName() {
        return newName;
    }
    
    public void setNewName(String newName) {
        this.newName = newName;
        setProperty("newName", newName);
    }
}

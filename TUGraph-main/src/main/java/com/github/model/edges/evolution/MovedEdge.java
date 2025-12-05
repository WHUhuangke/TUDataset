package com.github.model.edges.evolution;

import com.github.model.EvolutionEdge;

/**
 * 移动边 - 表示节点在代码结构中移动
 * 
 * <p>适用场景：
 * <ul>
 *   <li>类在包之间移动：com.old.User → com.new.User</li>
 *   <li>方法在类之间移动：ClassA.method() → ClassB.method()</li>
 *   <li>字段在类之间移动：ClassA.field → ClassB.field</li>
 *   <li>内部类变为顶层类或反之</li>
 * </ul>
 * 
 * <p>对应 RefactoringMiner 类型：
 * <ul>
 *   <li>MOVE_CLASS</li>
 *   <li>MOVE_METHOD</li>
 *   <li>MOVE_ATTRIBUTE</li>
 *   <li>MOVE_AND_RENAME_CLASS</li>
 *   <li>MOVE_AND_RENAME_METHOD</li>
 *   <li>MOVE_AND_RENAME_ATTRIBUTE</li>
 * </ul>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class MovedEdge extends EvolutionEdge {
    
    /**
     * 旧位置（全限定名或路径）
     */
    private String oldLocation;
    
    /**
     * 新位置（全限定名或路径）
     */
    private String newLocation;
    
    public MovedEdge(String fromNodeId, String toNodeId) {
        super(fromNodeId, toNodeId);
    }
    
    public MovedEdge(String fromNodeId, String toNodeId, String fromVersion, String toVersion) {
        super(fromNodeId, toNodeId, fromVersion, toVersion);
    }
    
    @Override
    public String getEdgeType() {
        return "MOVED";
    }
    
    public String getOldLocation() {
        return oldLocation;
    }
    
    public void setOldLocation(String oldLocation) {
        this.oldLocation = oldLocation;
        setProperty("oldLocation", oldLocation);
    }
    
    public String getNewLocation() {
        return newLocation;
    }
    
    public void setNewLocation(String newLocation) {
        this.newLocation = newLocation;
        setProperty("newLocation", newLocation);
    }
}

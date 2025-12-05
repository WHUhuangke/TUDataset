package com.github.model;

/**
 * 节点在版本演化中的状态
 * 
 * <p>用于标识节点在版本比较中的变化状态：
 * <ul>
 *   <li>ADDED - 新增节点（只存在于新版本）</li>
 *   <li>DELETED - 删除节点（只存在于旧版本）</li>
 *   <li>MODIFIED - 修改节点（两个版本都存在但有变化）</li>
 *   <li>UNCHANGED - 未变化节点（两个版本完全相同）</li>
 * </ul>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public enum VersionStatus {
    /**
     * 新增节点 - 只存在于新版本中
     */
    ADDED("新增", "Node added in new version"),
    
    /**
     * 删除节点 - 只存在于旧版本中
     */
    DELETED("删除", "Node deleted in old version"),
    
    /**
     * 修改节点 - 两个版本都存在但属性有变化
     */
    MODIFIED("修改", "Node modified between versions"),
    
    /**
     * 未变化节点 - 两个版本完全相同
     */
    UNCHANGED("未变化", "Node unchanged between versions");
    
    private final String displayName;
    private final String description;
    
    VersionStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 从字符串解析状态
     * 
     * @param str 状态字符串
     * @return 对应的 VersionStatus，如果无法解析则返回 null
     */
    public static VersionStatus fromString(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        
        String normalized = str.trim().toUpperCase();
        try {
            return VersionStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

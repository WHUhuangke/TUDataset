package com.github.evolution.matcher;

import com.github.model.Node;
import com.github.model.nodes.*;

/**
 * 精确匹配器 - 基于全限定名、签名和源代码进行精确匹配
 * 
 * <p>匹配规则：
 * <ul>
 *   <li>TYPE节点：全限定名完全相同 + 源代码相同</li>
 *   <li>METHOD节点：方法签名完全相同 + 源代码相同</li>
 *   <li>FIELD节点：全限定名 + 字段类型完全相同 + 初始值相同</li>
 *   <li>PACKAGE节点：包名完全相同</li>
 *   <li>FILE节点：文件路径完全相同 + 内容相同</li>
 * </ul>
 * 
 * <p>这是最高优先级的匹配器，用于识别<b>完全未变化</b>的节点。
 * <p><b>注意</b>：如果签名相同但源代码不同，confidence 会降低到 0.9（表示 MODIFIED）
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class ExactMatcher implements NodeMatcher {
    
    @Override
    public boolean match(Node v1Node, Node v2Node) {
        // 签名匹配 + 源代码匹配 = 完全匹配
        return matchSignature(v1Node, v2Node) && matchSourceCode(v1Node, v2Node);
    }
    
    @Override
    public double getConfidence(Node v1Node, Node v2Node) {
        // 检查签名是否匹配
        if (!matchSignature(v1Node, v2Node)) {
            return 0.0;
        }
        
        // 签名相同，进一步检查源代码是否相同
        boolean sourceCodeMatch = matchSourceCode(v1Node, v2Node);
        
        // 关键逻辑：
        // - 源代码相同 → confidence=1.0 → GraphMerger合并节点
        // - 源代码不同 → confidence=0.8 → GraphMerger识别为MODIFIED，分离节点+创建演化边
        return sourceCodeMatch ? 1.0 : 0.8;
    }
    
    /**
     * 检查签名是否匹配（不包括源代码）
     */
    private boolean matchSignature(Node v1Node, Node v2Node) {
        // 节点类型必须相同
        if (!v1Node.getNodeType().equals(v2Node.getNodeType())) {
            return false;
        }
        
        // 根据节点类型进行签名匹配
        String nodeType = v1Node.getNodeType();
        
        switch (nodeType) {
            case "TYPE":
                return matchTypeSignature((TypeNode) v1Node, (TypeNode) v2Node);
            case "METHOD":
                return matchMethodSignature((MethodNode) v1Node, (MethodNode) v2Node);
            case "FIELD":
                return matchFieldSignature((FieldNode) v1Node, (FieldNode) v2Node);
            case "PACKAGE":
                return matchPackageNode((PackageNode) v1Node, (PackageNode) v2Node);
            case "FILE":
                return matchFileSignature((FileNode) v1Node, (FileNode) v2Node);
            default:
                return v1Node.getLabel().equals(v2Node.getLabel());
        }
    }
    
    /**
     * 检查源代码是否相同
     * 
     * <p><b>方案B逻辑</b>：只对有源代码的节点进行比较
     * <ul>
     *   <li>双方都没有源代码：认为"无需比较"，返回 true（交由签名决定）</li>
     *   <li>双方都有源代码：必须完全相同，否则认为是不同节点</li>
     *   <li>只有一方有源代码：数据不一致，返回 false</li>
     * </ul>
     */
    private boolean matchSourceCode(Node v1Node, Node v2Node) {
        String v1Source = v1Node.getSourceCode();
        String v2Source = v2Node.getSourceCode();
        
        // 情况1: 双方都没有源代码（Package/File/部分Field节点）
        // 这些节点本身不包含可比较的"代码"，只能依赖签名匹配
        if (v1Source == null && v2Source == null) {
            return true;  // 跳过源代码比较，让签名决定
        }
        
        // 情况2: 只有一方有源代码（数据不一致，不应该发生）
        if (v1Source == null || v2Source == null) {
            return false;  // 认为不匹配
        }
        
        // 情况3: 双方都有源代码（Method/Type节点）
        // 必须完全相同才认为是同一个节点
        return v1Source.equals(v2Source);
    }
    
    @Override
    public String getName() {
        return "ExactMatcher";
    }
    
    @Override
    public int getPriority() {
        return 100;  // 最高优先级
    }
    
    /**
     * 匹配 TYPE 节点的签名
     */
    private boolean matchTypeSignature(TypeNode v1, TypeNode v2) {
        // 全限定名必须相同
        return v1.getQualifiedName().equals(v2.getQualifiedName());
    }
    
    /**
     * 匹配 METHOD 节点的签名
     */
    private boolean matchMethodSignature(MethodNode v1, MethodNode v2) {
        // 方法签名必须完全相同（签名包含了方法名和参数）
        return v1.getSignature().equals(v2.getSignature());
    }
    
    /**
     * 匹配 FIELD 节点的签名
     */
    private boolean matchFieldSignature(FieldNode v1, FieldNode v2) {
        // 字段的 qualifiedName 必须相同（包含了所属类和字段名）
        String v1QualifiedName = (String) v1.getProperty("qualifiedName");
        String v2QualifiedName = (String) v2.getProperty("qualifiedName");
        
        if (!v1QualifiedName.equals(v2QualifiedName)) {
            return false;
        }
        
        // 字段类型也必须相同
        return v1.getFieldType().equals(v2.getFieldType());
    }
    
    /**
     * 匹配 PACKAGE 节点
     */
    private boolean matchPackageNode(PackageNode v1, PackageNode v2) {
        // 包的全限定名必须相同
        return v1.getQualifiedName().equals(v2.getQualifiedName());
    }
    
    /**
     * 匹配 FILE 节点的签名
     */
    private boolean matchFileSignature(FileNode v1, FileNode v2) {
        // 文件的相对路径必须相同
        // 注意：这里比较相对路径，而不是绝对路径
        String v1RelativePath = v1.getRelativePath();
        String v2RelativePath = v2.getRelativePath();
        
        if (v1RelativePath == null || v2RelativePath == null) {
            return false;
        }
        
        return v1RelativePath.equals(v2RelativePath);
    }
}

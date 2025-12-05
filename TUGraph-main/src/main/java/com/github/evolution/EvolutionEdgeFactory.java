package com.github.evolution;

import com.github.model.EvolutionEdge;
import com.github.model.Node;
import com.github.model.edges.evolution.*;
import com.github.model.nodes.FieldNode;
import com.github.model.nodes.MethodNode;
import com.github.model.nodes.TypeNode;
import com.github.refactoring.RefactoringInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 演化边工厂 - 根据 RefactoringInfo 创建对应的演化边
 * 
 * <p>负责将 RefactoringMiner 检测到的重构操作映射为知识图谱中的演化边。
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class EvolutionEdgeFactory {
    
    /**
     * RefactoringMiner 类型到演化边类型的映射表
     */
    private static final Map<String, EdgeTypeInfo> TYPE_MAPPING = new HashMap<>();
    
    static {
        // Rename 系列 -> RenamedEdge
        TYPE_MAPPING.put("RENAME_CLASS", new EdgeTypeInfo(RenamedEdge.class, "class"));
        TYPE_MAPPING.put("RENAME_METHOD", new EdgeTypeInfo(RenamedEdge.class, "method"));
        TYPE_MAPPING.put("RENAME_ATTRIBUTE", new EdgeTypeInfo(RenamedEdge.class, "field"));
        TYPE_MAPPING.put("RENAME_PARAMETER", new EdgeTypeInfo(RenamedEdge.class, "parameter"));
        TYPE_MAPPING.put("RENAME_VARIABLE", new EdgeTypeInfo(RenamedEdge.class, "variable"));
        TYPE_MAPPING.put("RENAME_PACKAGE", new EdgeTypeInfo(RenamedEdge.class, "package"));
        
        // Move 系列 -> MovedEdge
        TYPE_MAPPING.put("MOVE_CLASS", new EdgeTypeInfo(MovedEdge.class, "class"));
        TYPE_MAPPING.put("MOVE_METHOD", new EdgeTypeInfo(MovedEdge.class, "method"));
        TYPE_MAPPING.put("MOVE_ATTRIBUTE", new EdgeTypeInfo(MovedEdge.class, "field"));
        TYPE_MAPPING.put("MOVE_AND_RENAME_CLASS", new EdgeTypeInfo(MovedEdge.class, "class"));
        TYPE_MAPPING.put("MOVE_AND_RENAME_METHOD", new EdgeTypeInfo(MovedEdge.class, "method"));
        TYPE_MAPPING.put("MOVE_AND_RENAME_ATTRIBUTE", new EdgeTypeInfo(MovedEdge.class, "field"));
        
        // Extract 系列 -> ExtractedEdge
        TYPE_MAPPING.put("EXTRACT_METHOD", new EdgeTypeInfo(ExtractedEdge.class, "method"));
        TYPE_MAPPING.put("EXTRACT_CLASS", new EdgeTypeInfo(ExtractedEdge.class, "class"));
        TYPE_MAPPING.put("EXTRACT_INTERFACE", new EdgeTypeInfo(ExtractedEdge.class, "interface"));
        TYPE_MAPPING.put("EXTRACT_SUPERCLASS", new EdgeTypeInfo(ExtractedEdge.class, "superclass"));
        TYPE_MAPPING.put("EXTRACT_SUBCLASS", new EdgeTypeInfo(ExtractedEdge.class, "subclass"));
        TYPE_MAPPING.put("EXTRACT_VARIABLE", new EdgeTypeInfo(ExtractedEdge.class, "variable"));
        TYPE_MAPPING.put("EXTRACT_AND_MOVE_METHOD", new EdgeTypeInfo(ExtractedEdge.class, "method"));
        
        // Inline 系列 -> InlinedEdge
        TYPE_MAPPING.put("INLINE_METHOD", new EdgeTypeInfo(InlinedEdge.class, "method"));
        TYPE_MAPPING.put("INLINE_VARIABLE", new EdgeTypeInfo(InlinedEdge.class, "variable"));
        TYPE_MAPPING.put("MOVE_AND_INLINE_METHOD", new EdgeTypeInfo(InlinedEdge.class, "method"));
        
        // Signature Change 系列 -> ChangedSignatureEdge
        TYPE_MAPPING.put("CHANGE_PARAMETER_TYPE", new EdgeTypeInfo(ChangedSignatureEdge.class, "parameter_type"));
        TYPE_MAPPING.put("CHANGE_RETURN_TYPE", new EdgeTypeInfo(ChangedSignatureEdge.class, "return_type"));
        TYPE_MAPPING.put("ADD_PARAMETER", new EdgeTypeInfo(ChangedSignatureEdge.class, "add_parameter"));
        TYPE_MAPPING.put("REMOVE_PARAMETER", new EdgeTypeInfo(ChangedSignatureEdge.class, "remove_parameter"));
        TYPE_MAPPING.put("REORDER_PARAMETER", new EdgeTypeInfo(ChangedSignatureEdge.class, "reorder_parameter"));
        TYPE_MAPPING.put("ADD_THROWN_EXCEPTION_TYPE", new EdgeTypeInfo(ChangedSignatureEdge.class, "add_exception"));
        TYPE_MAPPING.put("REMOVE_THROWN_EXCEPTION_TYPE", new EdgeTypeInfo(ChangedSignatureEdge.class, "remove_exception"));
        TYPE_MAPPING.put("CHANGE_METHOD_ACCESS_MODIFIER", new EdgeTypeInfo(ChangedSignatureEdge.class, "modifier"));
        TYPE_MAPPING.put("CHANGE_ATTRIBUTE_ACCESS_MODIFIER", new EdgeTypeInfo(ChangedSignatureEdge.class, "modifier"));
        
        // Pull Up/Push Down 系列 -> RefactoredEdge
        TYPE_MAPPING.put("PULL_UP_METHOD", new EdgeTypeInfo(RefactoredEdge.class, "pull_up"));
        TYPE_MAPPING.put("PULL_UP_ATTRIBUTE", new EdgeTypeInfo(RefactoredEdge.class, "pull_up"));
        TYPE_MAPPING.put("PUSH_DOWN_METHOD", new EdgeTypeInfo(RefactoredEdge.class, "push_down"));
        TYPE_MAPPING.put("PUSH_DOWN_ATTRIBUTE", new EdgeTypeInfo(RefactoredEdge.class, "push_down"));
        
        // 其他重构 -> RefactoredEdge
        TYPE_MAPPING.put("REPLACE_VARIABLE_WITH_ATTRIBUTE", new EdgeTypeInfo(RefactoredEdge.class, "replace"));
        TYPE_MAPPING.put("PARAMETERIZE_VARIABLE", new EdgeTypeInfo(RefactoredEdge.class, "parameterize"));
        TYPE_MAPPING.put("MERGE_PARAMETER", new EdgeTypeInfo(RefactoredEdge.class, "merge"));
        TYPE_MAPPING.put("SPLIT_PARAMETER", new EdgeTypeInfo(RefactoredEdge.class, "split"));
        TYPE_MAPPING.put("MERGE_VARIABLE", new EdgeTypeInfo(RefactoredEdge.class, "merge"));
        TYPE_MAPPING.put("SPLIT_VARIABLE", new EdgeTypeInfo(RefactoredEdge.class, "split"));
        TYPE_MAPPING.put("MERGE_CLASS", new EdgeTypeInfo(RefactoredEdge.class, "merge"));
        TYPE_MAPPING.put("SPLIT_CLASS", new EdgeTypeInfo(RefactoredEdge.class, "split"));
    }
    
    /**
     * 根据 RefactoringInfo 创建演化边
     * 
     * @param refactoring 重构信息
     * @param fromNode 源节点（旧版本）
     * @param toNode 目标节点（新版本）
     * @param fromVersion 源版本 hash
     * @param toVersion 目标版本 hash
     * @return 创建的演化边，如果无法创建则返回 null
     */
    public static EvolutionEdge createEdge(
            RefactoringInfo refactoring,
            Node fromNode,
            Node toNode,
            String fromVersion,
            String toVersion) {
        
        String refactoringType = refactoring.getType();
        EdgeTypeInfo typeInfo = TYPE_MAPPING.get(refactoringType);
        
        EvolutionEdge edge;
        
        if (typeInfo != null) {
            // 根据映射表创建对应的边类型
            edge = createEdgeByType(typeInfo.edgeClass, fromNode.getId(), toNode.getId(), fromVersion, toVersion);
        } else {
            // 未知类型，使用 RefactoredEdge 兜底
            edge = new RefactoredEdge(fromNode.getId(), toNode.getId(), fromVersion, toVersion);
        }
        
        if (edge == null) {
            return null;
        }
        
        // 设置通用属性
        edge.setRefactoringType(refactoringType);
        edge.setDescription(refactoring.getDescription());
        edge.setConfidence(refactoring.getConfidence());
        if (typeInfo != null && typeInfo.subType != null && !typeInfo.subType.isBlank()) {
            edge.setProperty("refactoringSubType", typeInfo.subType);
        }
        edge.setProperty("detectedBy", "RefactoringMiner");
        
        List<String> leftLocations = convertLocations(refactoring.getLeftSideLocations());
        List<String> rightLocations = convertLocations(refactoring.getRightSideLocations());
        if (!leftLocations.isEmpty()) {
            edge.setProperty("leftLocations", leftLocations);
        }
        if (!rightLocations.isEmpty()) {
            edge.setProperty("rightLocations", rightLocations);
        }
        
        List<String> leftElements = extractCodeElements(refactoring.getLeftSideLocations());
        List<String> rightElements = extractCodeElements(refactoring.getRightSideLocations());
        if (!leftElements.isEmpty()) {
            edge.setProperty("leftElements", leftElements);
        }
        if (!rightElements.isEmpty()) {
            edge.setProperty("rightElements", rightElements);
        }
        
        List<String> refactoringTypes = new ArrayList<>();
        if (refactoringType != null && !refactoringType.isBlank()) {
            refactoringTypes.add(refactoringType);
        }
        if (!refactoringTypes.isEmpty()) {
            edge.setProperty("refactoringTypes", refactoringTypes);
        }
        
        // 设置特定边类型的属性
        enrichEdgeProperties(edge, refactoring, fromNode, toNode, typeInfo);
        
        return edge;
    }
    
    /**
     * 根据类型创建演化边实例
     */
    private static EvolutionEdge createEdgeByType(
            Class<? extends EvolutionEdge> edgeClass,
            String fromNodeId,
            String toNodeId,
            String fromVersion,
            String toVersion) {
        
        try {
            return edgeClass.getConstructor(String.class, String.class, String.class, String.class)
                    .newInstance(fromNodeId, toNodeId, fromVersion, toVersion);
        } catch (Exception e) {
            // 如果构造失败，返回 null
            return null;
        }
    }
    
    /**
     * 丰富边的特定属性
     */
    private static void enrichEdgeProperties(
            EvolutionEdge edge,
            RefactoringInfo refactoring,
            Node fromNode,
            Node toNode,
            EdgeTypeInfo typeInfo) {
        
        if (edge instanceof RenamedEdge) {
            enrichRenamedEdge((RenamedEdge) edge, fromNode, toNode);
        } else if (edge instanceof MovedEdge) {
            enrichMovedEdge((MovedEdge) edge, fromNode, toNode);
        } else if (edge instanceof ExtractedEdge) {
            enrichExtractedEdge((ExtractedEdge) edge, typeInfo != null ? typeInfo.subType : null);
        } else if (edge instanceof InlinedEdge) {
            enrichInlinedEdge((InlinedEdge) edge, typeInfo != null ? typeInfo.subType : null);
        } else if (edge instanceof ChangedSignatureEdge) {
            enrichChangedSignatureEdge((ChangedSignatureEdge) edge, fromNode, toNode, typeInfo != null ? typeInfo.subType : null);
        } else if (edge instanceof RefactoredEdge) {
            enrichRefactoredEdge((RefactoredEdge) edge, refactoring.getDescription());
        }
    }
    
    /**
     * 丰富 RenamedEdge 的属性
     */
    private static void enrichRenamedEdge(RenamedEdge edge, Node fromNode, Node toNode) {
        String oldName = extractNodeName(fromNode);
        String newName = extractNodeName(toNode);
        
        edge.setOldName(oldName);
        edge.setNewName(newName);
    }
    
    /**
     * 丰富 MovedEdge 的属性
     */
    private static void enrichMovedEdge(MovedEdge edge, Node fromNode, Node toNode) {
        String oldLocation = extractNodeLocation(fromNode);
        String newLocation = extractNodeLocation(toNode);
        
        edge.setOldLocation(oldLocation);
        edge.setNewLocation(newLocation);
    }
    
    /**
     * 丰富 ExtractedEdge 的属性
     */
    private static void enrichExtractedEdge(ExtractedEdge edge, String extractType) {
        if (extractType != null) {
            edge.setExtractType(extractType);
        }
    }
    
    /**
     * 丰富 InlinedEdge 的属性
     */
    private static void enrichInlinedEdge(InlinedEdge edge, String inlineType) {
        if (inlineType != null) {
            edge.setInlineType(inlineType);
        }
    }
    
    /**
     * 丰富 ChangedSignatureEdge 的属性
     */
    private static void enrichChangedSignatureEdge(ChangedSignatureEdge edge, Node fromNode, Node toNode, String changeType) {
        if (fromNode instanceof MethodNode && toNode instanceof MethodNode) {
            String oldSignature = ((MethodNode) fromNode).getSignature();
            String newSignature = ((MethodNode) toNode).getSignature();
            
            edge.setOldSignature(oldSignature);
            edge.setNewSignature(newSignature);
        }
        
        if (changeType != null) {
            edge.setChangeType(changeType);
        }
    }
    
    /**
     * 丰富 RefactoredEdge 的属性
     */
    private static void enrichRefactoredEdge(RefactoredEdge edge, String details) {
        if (details != null && !details.isBlank()) {
            edge.setRefactoringDetails(details);
        }
    }
    
    /**
     * 转换代码位置信息为可读列表
     */
    private static List<String> convertLocations(List<RefactoringInfo.CodeLocation> locations) {
        List<String> result = new ArrayList<>();
        if (locations == null) {
            return result;
        }
        
        for (RefactoringInfo.CodeLocation location : locations) {
            if (location == null) {
                continue;
            }
            String formatted = formatLocation(location);
            if (!formatted.isBlank()) {
                result.add(formatted);
            }
        }
        return deduplicate(result);
    }
    
    /**
     * 提取代码元素名称列表
     */
    private static List<String> extractCodeElements(List<RefactoringInfo.CodeLocation> locations) {
        List<String> elements = new ArrayList<>();
        if (locations == null) {
            return elements;
        }
        for (RefactoringInfo.CodeLocation location : locations) {
            String element = location.getCodeElement();
            if (element != null && !element.isBlank()) {
                elements.add(element.trim());
            }
        }
        return deduplicate(elements);
    }
    
    private static String formatLocation(RefactoringInfo.CodeLocation location) {
        StringBuilder sb = new StringBuilder();
        if (location.getFilePath() != null && !location.getFilePath().isBlank()) {
            sb.append(location.getFilePath());
        }
        int start = location.getStartLine();
        int end = location.getEndLine();
        if (start > 0 || end > 0) {
            sb.append(":");
            if (start > 0) {
                sb.append(start);
            }
            if (end > 0 && end != start) {
                sb.append("-").append(end);
            }
        }
        if (location.getCodeElement() != null && !location.getCodeElement().isBlank()) {
            if (sb.length() > 0) {
                sb.append("::");
            }
            sb.append(location.getCodeElement().trim());
        }
        return sb.toString();
    }
    
    private static List<String> deduplicate(List<String> values) {
        Set<String> deduped = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    deduped.add(trimmed);
                }
            }
        }
        return new ArrayList<>(deduped);
    }
    
    /**
     * 提取节点名称
     */
    private static String extractNodeName(Node node) {
        if (node instanceof MethodNode) {
            return (String) node.getProperty("name");
        } else if (node instanceof TypeNode) {
            return (String) node.getProperty("simpleName");
        } else if (node instanceof FieldNode) {
            return ((FieldNode) node).getName();
        }
        return "";
    }
    
    /**
     * 提取节点位置
     */
    private static String extractNodeLocation(Node node) {
        if (node instanceof MethodNode) {
            return ((MethodNode) node).getSignature();
        } else if (node instanceof TypeNode) {
            return ((TypeNode) node).getQualifiedName();
        } else if (node instanceof FieldNode) {
            return (String) node.getProperty("qualifiedName");
        }
        return "";
    }
    
    /**
     * 边类型信息（包含子类型）
     */
    private static class EdgeTypeInfo {
        final Class<? extends EvolutionEdge> edgeClass;
        final String subType;
        
        EdgeTypeInfo(Class<? extends EvolutionEdge> edgeClass, String subType) {
            this.edgeClass = edgeClass;
            this.subType = subType;
        }
    }
}

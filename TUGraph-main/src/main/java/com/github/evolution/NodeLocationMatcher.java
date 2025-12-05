package com.github.evolution;

import com.github.model.KnowledgeGraph;
import com.github.model.Node;
import com.github.model.nodes.FieldNode;
import com.github.model.nodes.MethodNode;
import com.github.model.nodes.TypeNode;
import com.github.refactoring.RefactoringInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点位置匹配器 - 基于代码位置查找图中的节点
 * 
 * <p>核心策略：
 * <ol>
 *   <li>文件路径匹配：RefactoringInfo.filePath == Node.relativePath</li>
 *   <li>行号范围匹配：
 *     <ul>
 *       <li>MethodNode: [startLine, endLine] 与 [lineStart, lineEnd] 重叠</li>
 *       <li>TypeNode: [startLine, endLine] 与 [lineStart, lineEnd] 重叠</li>
 *       <li>FieldNode: startLine ≈ lineNumber (±2行容错)</li>
 *     </ul>
 *   </li>
 *   <li>名称验证（可选）：匹配 className/methodName 作为二次确认</li>
 * </ol>
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class NodeLocationMatcher {
    
    /**
     * 行号匹配的容错范围（行）
     * 应对 Spoon 和 RefactoringMiner 的解析差异
     */
    private static final int LINE_TOLERANCE = 2;
    
    private final KnowledgeGraph graph;
    
    public NodeLocationMatcher(KnowledgeGraph graph) {
        this.graph = graph;
    }
    
    /**
     * 根据代码位置查找节点
     * 
     * @param location RefactoringMiner 提供的代码位置
     * @return 匹配的节点列表（可能有多个匹配）
     */
    public List<Node> findNodesByLocation(RefactoringInfo.CodeLocation location) {
        List<Node> matches = new ArrayList<>();
        
        String filePath = location.getFilePath();
        int startLine = location.getStartLine();
        int endLine = location.getEndLine();
        String codeElement = location.getCodeElement();
        
        // 遍历图中所有节点
        for (Node node : graph.getAllNodes()) {
            // 1. 检查文件路径匹配
            if (!matchesFilePath(node, filePath)) {
                continue;
            }
            
            // 2. 根据节点类型进行位置匹配
            if (node instanceof MethodNode) {
                if (matchesMethodLocation((MethodNode) node, startLine, endLine, codeElement)) {
                    matches.add(node);
                }
            } else if (node instanceof TypeNode) {
                if (matchesTypeLocation((TypeNode) node, startLine, endLine, codeElement)) {
                    matches.add(node);
                }
            } else if (node instanceof FieldNode) {
                if (matchesFieldLocation((FieldNode) node, startLine, codeElement)) {
                    matches.add(node);
                }
            }
        }
        
        return matches;
    }
    
    /**
     * 检查文件路径是否匹配
     */
    private boolean matchesFilePath(Node node, String targetFilePath) {
        String nodeRelativePath = node.getRelativePath();
        String nodeAbsolutePath = node.getAbsolutePath();
        
        if (nodeRelativePath == null && nodeAbsolutePath == null) {
            return false;
        }
        
        // 匹配相对路径或绝对路径
        return (nodeRelativePath != null && nodeRelativePath.equals(targetFilePath)) ||
               (nodeAbsolutePath != null && nodeAbsolutePath.endsWith(targetFilePath));
    }
    
    /**
     * 检查方法节点的位置是否匹配
     */
    private boolean matchesMethodLocation(MethodNode method, int startLine, int endLine, String codeElement) {
        Integer lineStart = (Integer) method.getProperty("lineStart");
        Integer lineEnd = (Integer) method.getProperty("lineEnd");
        
        if (lineStart == null || lineEnd == null || lineStart == 0 || lineEnd == 0) {
            return false;
        }
        
        // 检查行号范围是否重叠（带容错）
        boolean lineMatches = rangesOverlap(startLine, endLine, lineStart, lineEnd, LINE_TOLERANCE);
        
        // 可选：检查方法名是否匹配（提高准确性）
        if (lineMatches && codeElement != null) {
            String methodName = (String) method.getProperty("name");
            if (methodName != null && codeElement.contains(methodName)) {
                return true;
            }
        }
        
        return lineMatches;
    }
    
    /**
     * 检查类型节点的位置是否匹配
     */
    private boolean matchesTypeLocation(TypeNode type, int startLine, int endLine, String codeElement) {
        Integer lineStart = (Integer) type.getProperty("lineStart");
        Integer lineEnd = (Integer) type.getProperty("lineEnd");
        
        if (lineStart == null || lineEnd == null || lineStart == 0 || lineEnd == 0) {
            return false;
        }
        
        // 检查行号范围是否重叠（带容错）
        boolean lineMatches = rangesOverlap(startLine, endLine, lineStart, lineEnd, LINE_TOLERANCE);
        
        // 可选：检查类名是否匹配
        if (lineMatches && codeElement != null) {
            String simpleName = (String) type.getProperty("simpleName");
            String qualifiedName = type.getQualifiedName();
            if ((simpleName != null && codeElement.contains(simpleName)) ||
                (qualifiedName != null && codeElement.contains(qualifiedName))) {
                return true;
            }
        }
        
        return lineMatches;
    }
    
    /**
     * 检查字段节点的位置是否匹配
     */
    private boolean matchesFieldLocation(FieldNode field, int startLine, String codeElement) {
        Integer lineNumber = (Integer) field.getProperty("lineNumber");
        
        if (lineNumber == null || lineNumber == 0) {
            return false;
        }
        
        // 字段只有单行，检查是否在容错范围内
        boolean lineMatches = Math.abs(lineNumber - startLine) <= LINE_TOLERANCE;
        
        // 可选：检查字段名是否匹配
        if (lineMatches && codeElement != null) {
            String fieldName = field.getName();
            if (fieldName != null && codeElement.contains(fieldName)) {
                return true;
            }
        }
        
        return lineMatches;
    }
    
    /**
     * 检查两个行号范围是否重叠（带容错）
     * 
     * @param start1 第一个范围的起始行
     * @param end1 第一个范围的结束行
     * @param start2 第二个范围的起始行
     * @param end2 第二个范围的结束行
     * @param tolerance 容错范围（行数）
     * @return 是否重叠
     */
    private boolean rangesOverlap(int start1, int end1, int start2, int end2, int tolerance) {
        // 扩展范围以容错
        int expandedStart1 = start1 - tolerance;
        int expandedEnd1 = end1 + tolerance;
        int expandedStart2 = start2 - tolerance;
        int expandedEnd2 = end2 + tolerance;
        
        // 检查重叠：范围1的起点在范围2内，或范围2的起点在范围1内
        return (expandedStart1 <= expandedEnd2 && expandedEnd1 >= expandedStart2);
    }
    
    /**
     * 查找最佳匹配节点（当有多个匹配时）
     * 
     * @param candidates 候选节点列表
     * @param location 目标位置
     * @return 最佳匹配节点，如果没有匹配则返回 null
     */
    public Node findBestMatch(List<Node> candidates, RefactoringInfo.CodeLocation location) {
        if (candidates.isEmpty()) {
            return null;
        }
        
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        
        // 如果有多个匹配，选择行号最接近的
        Node bestMatch = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (Node candidate : candidates) {
            int distance = calculateLineDistance(candidate, location.getStartLine(), location.getEndLine());
            if (distance < minDistance) {
                minDistance = distance;
                bestMatch = candidate;
            }
        }
        
        return bestMatch;
    }
    
    /**
     * 计算节点与目标行号的距离
     */
    private int calculateLineDistance(Node node, int targetStart, int targetEnd) {
        if (node instanceof MethodNode || node instanceof TypeNode) {
            Integer lineStart = (Integer) node.getProperty("lineStart");
            Integer lineEnd = (Integer) node.getProperty("lineEnd");
            
            if (lineStart != null && lineEnd != null) {
                return Math.abs(lineStart - targetStart) + Math.abs(lineEnd - targetEnd);
            }
        } else if (node instanceof FieldNode) {
            Integer lineNumber = (Integer) node.getProperty("lineNumber");
            if (lineNumber != null) {
                return Math.abs(lineNumber - targetStart);
            }
        }
        
        return Integer.MAX_VALUE;
    }
}

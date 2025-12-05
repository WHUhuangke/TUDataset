package com.github.spoon.index;

import com.github.model.nodes.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Collection;

/**
 * 节点索引管理器
 * 作用：
 * 1. 确保节点唯一性（同一个实体只创建一个节点）
 * 2. 提供快速查找能力（通过限定名查找节点）
 * 3. 管理节点生命周期
 */
public class NodeIndex {
    
    // 使用 Map 存储不同类型的节点，key 为节点的唯一标识符
    private final Map<String, ProjectNode> projects = new HashMap<>();
    private final Map<String, PackageNode> packages = new HashMap<>();
    private final Map<String, FileNode> files = new HashMap<>();
    private final Map<String, TypeNode> types = new HashMap<>();
    private final Map<String, MethodNode> methods = new HashMap<>();
    private final Map<String, FieldNode> fields = new HashMap<>();
    // ParameterNode 已完全移除 - 参数信息现在作为 UsesParameterEdge 的属性存储
    // AnnotationNode 已完全移除 - 注解信息现在作为节点属性存储
    
    // ============ 添加节点（如果已存在则返回现有节点） ============
    
    public ProjectNode addProject(ProjectNode node) {
        return projects.computeIfAbsent(node.getId(), k -> node);
    }
    
    public PackageNode addPackage(PackageNode node) {
        return packages.computeIfAbsent(node.getQualifiedName(), k -> node);
    }
    
    public FileNode addFile(FileNode node) {
        return files.computeIfAbsent(node.getPath(), k -> node);
    }
    
    public TypeNode addType(TypeNode node) {
        return types.computeIfAbsent(node.getQualifiedName(), k -> node);
    }
    
    public MethodNode addMethod(MethodNode node) {
        return methods.computeIfAbsent(node.getSignature(), k -> node);
    }
    
    public FieldNode addField(FieldNode node) {
        return fields.computeIfAbsent(node.getId(), k -> node);
    }
    
    // addParameter() 已完全移除 - ParameterNode 不再使用
    // addAnnotation() 已完全移除 - AnnotationNode 不再使用
    
    // ============ 查找节点 ============
    
    public Optional<ProjectNode> getProject(String projectId) {
        return Optional.ofNullable(projects.get(projectId));
    }
    
    public Optional<PackageNode> getPackage(String qualifiedName) {
        return Optional.ofNullable(packages.get(qualifiedName));
    }
    
    public Optional<FileNode> getFile(String path) {
        return Optional.ofNullable(files.get(path));
    }
    
    public Optional<TypeNode> getType(String qualifiedName) {
        return Optional.ofNullable(types.get(qualifiedName));
    }
    
    public Optional<MethodNode> getMethod(String signature) {
        return Optional.ofNullable(methods.get(signature));
    }
    
    public Optional<FieldNode> getField(String qualifiedName) {
        return Optional.ofNullable(fields.get(qualifiedName));
    }
    
    // getParameter() 已完全移除 - ParameterNode 不再使用
    // getAnnotation() 已完全移除 - AnnotationNode 不再使用
    
    // ============ 获取所有节点（用于第二遍遍历） ============
    
    public Collection<ProjectNode> getAllProjects() {
        return projects.values();
    }
    
    public Collection<PackageNode> getAllPackages() {
        return packages.values();
    }
    
    public Collection<FileNode> getAllFiles() {
        return files.values();
    }
    
    public Collection<TypeNode> getAllTypes() {
        return types.values();
    }
    
    public Collection<MethodNode> getAllMethods() {
        return methods.values();
    }
    
    public Collection<FieldNode> getAllFields() {
        return fields.values();
    }
    
    // getAllParameters() 已完全移除 - ParameterNode 不再使用
    // getAllAnnotations() 已完全移除 - AnnotationNode 不再使用
    
    // ============ 统计信息 ============
    
    public int getTotalNodeCount() {
        return projects.size() + packages.size() + files.size() + 
               types.size() + methods.size() + fields.size(); // parameters 和 annotations 已移除
    }
    
    public void printStatistics() {
        System.out.println("=== Node Index Statistics ===");
        System.out.println("Projects: " + projects.size());
        System.out.println("Packages: " + packages.size());
        System.out.println("Files: " + files.size());
        System.out.println("Types: " + types.size());
        System.out.println("Methods: " + methods.size());
        System.out.println("Fields: " + fields.size());
        // System.out.println("Parameters: " + parameters.size()); // 已移除
        // System.out.println("Annotations: " + annotations.size()); // 已移除
        System.out.println("Total: " + getTotalNodeCount());
        System.out.println("Note: Parameters are now stored as edge properties (UsesParameterEdge)");
        System.out.println("Note: Annotations are now stored as node properties");
    }
    
    // ============ 清空索引 ============
    
    public void clear() {
        projects.clear();
        packages.clear();
        files.clear();
        types.clear();
        methods.clear();
        fields.clear();
        // parameters.clear(); // 已移除
        // annotations.clear(); // 已移除
    }
}

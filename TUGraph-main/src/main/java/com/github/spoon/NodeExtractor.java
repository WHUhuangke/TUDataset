package com.github.spoon;

import com.github.logging.GraphLogger;
import com.github.model.nodes.*;
import com.github.spoon.index.NodeIndex;
import com.github.spoon.visitors.*;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;

import java.io.File;

/**
 * 节点提取器 - 第一遍遍历
 * 作用：
 * 1. 遍历项目的 AST
 * 2. 提取所有节点（Project, Package, File, Type, Method, Field）
 * 3. 将节点注册到索引中
 * 注意：Parameter 和 Annotation 节点已移除
 */
public class NodeExtractor {
    
    private final NodeIndex nodeIndex;
    private final CtModel model;
    private final String projectPath;
    private final GraphLogger logger = GraphLogger.getInstance();
    
    public NodeExtractor(CtModel model, String projectPath) {
        this.nodeIndex = new NodeIndex();
        this.model = model;
        this.projectPath = projectPath;
    }
    
    /**
     * 执行节点提取
     */
    public NodeIndex extract() {
        logger.startPhase("Extracting Nodes", model.getAllTypes().size());
        
        // 1. 创建项目节点
        extractProjectNode();
        
        // 2. 创建包节点
        extractPackageNodes();
        
        // 3. 创建文件节点
        extractFileNodes();
        
        // 4. 使用 Visitor 提取类型、方法、字段、参数节点
        extractCodeElements();
        
        // 5. 统一设置所有节点的相对路径
        normalizeNodePaths();
        
        // 6. 打印统计信息
        nodeIndex.printStatistics();
        
        logger.endPhase();
        return nodeIndex;
    }
    
    /**
     * 提取项目节点
     */
    private void extractProjectNode() {
        logger.debug("Extracting project node...");
        
        // 从路径提取项目名称
        File projectDir = new File(projectPath);
        String projectName = projectDir.getName();
        
        // 创建项目节点
        ProjectNode projectNode = new ProjectNode(projectName, "1.0.0", "", "");
        projectNode.setDescription("Java project analyzed by Spoon");
        
        nodeIndex.addProject(projectNode);
        logger.logNodeCreation("PROJECT", projectName);
    }
    
    /**
     * 提取包节点
     */
    private void extractPackageNodes() {
        logger.debug("Extracting package nodes...");
        
        int count = 0;
        for (CtPackage ctPackage : model.getAllPackages()) {
            if (ctPackage.isUnnamedPackage()) {
                continue; // 跳过未命名包
            }
            
            String qualifiedName = ctPackage.getQualifiedName();
            
            PackageNode packageNode = new PackageNode(qualifiedName);
            
            // 统计包中的类型数量
            int typeCount = ctPackage.getTypes().size();
            packageNode.setProperty("typeCount", typeCount);
            
            // 判断是否是测试包
            boolean isTest = qualifiedName.contains("test");
            packageNode.setProperty("isTestPackage", isTest);
            
            nodeIndex.addPackage(packageNode);
            logger.logNodeCreation("PACKAGE", qualifiedName);
            count++;
        }
        
        logger.info("Created " + count + " packages");
    }
    
    /**
     * 提取文件节点
     */
    private void extractFileNodes() {
        logger.debug("Extracting file nodes...");
        
        int count = 0;
        for (CtType<?> type : model.getAllTypes()) {
            if (type.getPosition() == null || !type.getPosition().isValidPosition()) {
                continue;
            }
            
            File file = type.getPosition().getFile();
            if (file == null) {
                continue;
            }
            
            String absolutePath = file.getAbsolutePath();
            
            // 检查是否已经创建
            if (nodeIndex.getFile(absolutePath).isPresent()) {
                continue;
            }
            
            String fileName = file.getName();
            
            // 计算相对路径
            String relativePath = calculateRelativePath(absolutePath);
            
            FileNode fileNode = new FileNode(absolutePath, relativePath, fileName);
            
            // 设置包名
            if (type.getPackage() != null) {
                fileNode.setPackageName(type.getPackage().getQualifiedName());
            }
            
            // 判断是否是测试文件
            boolean isTest = relativePath.contains("/test/") || relativePath.contains("\\test\\");
            fileNode.setProperty("isTestFile", isTest);
            
            nodeIndex.addFile(fileNode);
            logger.logNodeCreation("FILE", relativePath);
            count++;
        }
        
        logger.info("Created " + count + " files");
    }
    
    /**
     * 计算文件相对于项目根目录的相对路径
     */
    private String calculateRelativePath(String absolutePath) {
        try {
            File projectDir = new File(projectPath).getCanonicalFile();
            File targetFile = new File(absolutePath).getCanonicalFile();
            
            String projectPathStr = projectDir.getAbsolutePath();
            String targetPathStr = targetFile.getAbsolutePath();
            
            if (targetPathStr.startsWith(projectPathStr)) {
                String relative = targetPathStr.substring(projectPathStr.length());
                // 移除开头的分隔符
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                return relative;
            }
            
            // 如果不在项目目录下，返回绝对路径
            return absolutePath;
        } catch (Exception e) {
            return absolutePath;
        }
    }
    
    /**
     * 使用 Visitor 提取代码元素
     * 注意: 参数信息现在作为 UsesParameterEdge 的属性存储,不再创建 ParameterNode
     */
    private void extractCodeElements() {
        logger.info("Extracting code elements (types, methods, fields)...");
        
        // 创建各种 Visitor
        TypeVisitor typeVisitor = new TypeVisitor(nodeIndex);
        MethodVisitor methodVisitor = new MethodVisitor(nodeIndex);
        FieldVisitor fieldVisitor = new FieldVisitor(nodeIndex);
        // ParameterVisitor 已移除 - 参数信息现在作为边的属性存储
        
        // 获取所有类型
        var allTypes = model.getAllTypes();
        int processed = 0;
        
        // 遍历所有类型
        for (CtType<?> type : allTypes) {
            // 提取类型
            type.accept(typeVisitor);
            
            // 提取方法
            type.accept(methodVisitor);
            
            // 提取字段
            type.accept(fieldVisitor);
            
            // 更新进度
            processed++;
            logger.getProgressTracker().updateProgress(processed);
        }
        
        logger.info("✓ Code elements extraction completed");
    }
    
    /**
     * 统一为所有节点设置相对路径
     * 这个方法会遍历所有节点，为那些已有absolutePath但没有relativePath的节点计算并设置相对路径
     */
    private void normalizeNodePaths() {
        logger.debug("Normalizing node paths...");
        
        int count = 0;
        
        // 处理所有类型节点
        for (TypeNode typeNode : nodeIndex.getAllTypes()) {
            if (typeNode.getAbsolutePath() != null && typeNode.getRelativePath() == null) {
                String relativePath = calculateRelativePath(typeNode.getAbsolutePath());
                typeNode.setRelativePath(relativePath);
                count++;
            }
        }
        
        // 处理所有方法节点
        for (MethodNode methodNode : nodeIndex.getAllMethods()) {
            if (methodNode.getAbsolutePath() != null && methodNode.getRelativePath() == null) {
                String relativePath = calculateRelativePath(methodNode.getAbsolutePath());
                methodNode.setRelativePath(relativePath);
                count++;
            }
        }
        
        // 处理所有字段节点
        for (FieldNode fieldNode : nodeIndex.getAllFields()) {
            if (fieldNode.getAbsolutePath() != null && fieldNode.getRelativePath() == null) {
                String relativePath = calculateRelativePath(fieldNode.getAbsolutePath());
                fieldNode.setRelativePath(relativePath);
                count++;
            }
        }
        
        logger.debug("Normalized " + count + " node paths");
    }
    
    public NodeIndex getNodeIndex() {
        return nodeIndex;
    }
}

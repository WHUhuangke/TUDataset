package com.github.spoon.visitors;

import com.github.model.nodes.FieldNode;
import com.github.spoon.index.NodeIndex;
import com.github.spoon.extractors.JavadocExtractor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.code.CtComment;

import java.util.List;
import java.io.File;
import java.util.ArrayList;

/**
 * 字段访问者 - 访问所有字段
 * 作用：
 * 1. 遍历所有字段声明
 * 2. 提取字段类型、修饰符、初始值
 * 3. 创建 FieldNode 并注册到索引
 */
public class FieldVisitor extends CtScanner {
    
    private final NodeIndex nodeIndex;
    
    public FieldVisitor(NodeIndex nodeIndex) {
        this.nodeIndex = nodeIndex;
    }
    
    @Override
    public <T> void visitCtField(CtField<T> field) {
        // 跳过枚举常量（它们会被单独处理）
        if (!field.getDeclaringType().isEnum() || !field.isStatic()) {
            createFieldNode(field);
        }
        super.visitCtField(field);
    }
    
    private <T> void createFieldNode(CtField<T> field) {
        // 生成字段的唯一标识（类全限定名#字段名）
        String declaringType = field.getDeclaringType().getQualifiedName();
        String fieldName = field.getSimpleName();
        String qualifiedName = declaringType + "#" + fieldName;
        
        // 获取字段类型
        String fieldType = field.getType().getQualifiedName();
        
        // 创建 FieldNode
        FieldNode fieldNode = new FieldNode(qualifiedName, fieldName, fieldType);
        
        // 设置修饰符
        fieldNode.setStatic(field.isStatic())
                 .setFinal(field.isFinal())
                 .setVisibility(field.getVisibility() != null ? 
                     field.getVisibility().toString().toLowerCase() : "package");
        
        fieldNode.setProperty("isTransient", field.isTransient());
        fieldNode.setProperty("isVolatile", field.isVolatile());
        
        // 提取初始值
        if (field.getDefaultExpression() != null) {
            String initialValue = field.getDefaultExpression().toString();
            fieldNode.setInitialValue(initialValue);
        }
        
        // 提取字段声明代码（移除 Javadoc）
        CtField<T> fieldClone = field.clone();
        fieldClone.setComments(new ArrayList<>());
        String declaration = field.toString();
        declaration = removeLeadingJavadoc(declaration, field.getComments());
        fieldNode.setFieldDeclaration(declaration);
        
        // 提取路径信息
        if (field.getPosition() != null && field.getPosition().isValidPosition()) {
            File file = field.getPosition().getFile();
            if (file != null) {
                String absolutePath = file.getAbsolutePath();
                fieldNode.setAbsolutePath(absolutePath);
                // relativePath将在NodeExtractor中统一设置
            }
            fieldNode.setLineNumber(field.getPosition().getLine());
        }
        
        // 提取 Javadoc（严格的文档注释）
        String javadoc = JavadocExtractor.extractJavadoc(field);
        fieldNode.setDocumentation(javadoc);
        
        // 提取所有注释（包括行注释、块注释）
        String allComments = JavadocExtractor.extractAllComments(field);
        fieldNode.setComments(allComments);
        
        // 提取注解
        for (var annotation : field.getAnnotations()) {
            fieldNode.addAnnotation(annotation.getAnnotationType().getQualifiedName());
        }
        
        // 生成语义摘要
        fieldNode.generateSemanticSummary();
        
        // 添加到索引
        nodeIndex.addField(fieldNode);
    }

    private String removeLeadingJavadoc(String source, List<CtComment> comments) {
        if (source == null || comments == null) {
            return source;
        }
        String result = source;
        for (CtComment comment : comments) {
            if (comment.getCommentType() == CtComment.CommentType.JAVADOC) {
                String commentText = comment.toString();
                int idx = result.indexOf(commentText);
                if (idx >= 0) {
                    result = result.substring(0, idx) + result.substring(idx + commentText.length());
                }
                break;
            }
        }
        return result.replaceFirst("^\\s+", "");
    }
}

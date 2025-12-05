package com.github.spoon.visitors;

import com.github.model.nodes.TypeNode;
import com.github.spoon.index.NodeIndex;
import com.github.spoon.extractors.JavadocExtractor;
import spoon.reflect.declaration.*;
import spoon.reflect.visitor.CtScanner;
import java.io.File;
import java.util.List;

import spoon.reflect.code.CtComment;

/**
 * 类型访问者 - 访问所有类、接口、枚举、注解
 * 作用：
 * 1. 遍历 AST 中的所有类型定义
 * 2. 提取类型的元数据（修饰符、泛型等）
 * 3. 提取类型的源代码和文档
 * 4. 创建 TypeNode 并注册到索引
 */
public class TypeVisitor extends CtScanner {
    
    private final NodeIndex nodeIndex;
    
    public TypeVisitor(NodeIndex nodeIndex) {
        this.nodeIndex = nodeIndex;
    }
    
    @Override
    public <T> void visitCtClass(CtClass<T> ctClass) {
        // 访问类
        if (!ctClass.isAnonymous()) {  // 跳过匿名类
            createTypeNode(ctClass, TypeNode.TypeKind.CLASS);
        }
        super.visitCtClass(ctClass);
    }
    
    @Override
    public <T> void visitCtInterface(CtInterface<T> ctInterface) {
        // 访问接口
        createTypeNode(ctInterface, TypeNode.TypeKind.INTERFACE);
        super.visitCtInterface(ctInterface);
    }
    
    @Override
    public <T extends Enum<?>> void visitCtEnum(CtEnum<T> ctEnum) {
        // 访问枚举
        createTypeNode(ctEnum, TypeNode.TypeKind.ENUM);
        super.visitCtEnum(ctEnum);
    }
    
    @Override
    public <A extends java.lang.annotation.Annotation> void visitCtAnnotationType(
            CtAnnotationType<A> annotationType) {
        // 访问注解类型
        createTypeNode(annotationType, TypeNode.TypeKind.ANNOTATION);
        super.visitCtAnnotationType(annotationType);
    }
    
    private void createTypeNode(CtType<?> ctType, TypeNode.TypeKind kind) {
        String qualifiedName = ctType.getQualifiedName();
        
        // 判断是否是测试类
        if (kind == TypeNode.TypeKind.CLASS && isTestClass(ctType)) {
            kind = TypeNode.TypeKind.TEST_CLASS;
        }
        
        // 创建 TypeNode
        TypeNode typeNode = new TypeNode(qualifiedName, kind);
        
        // 设置修饰符
        typeNode.setAbstract(ctType.isAbstract())
                .setFinal(ctType.isFinal())
                .setStatic(ctType.isStatic())
                .setVisibility(ctType.getVisibility() != null ? 
                    ctType.getVisibility().toString().toLowerCase() : "package");
        
        // 提取源代码（移除顶层 Javadoc）
        String sourceCode = ctType.toString();
        sourceCode = removeLeadingJavadoc(sourceCode, ctType.getComments());
        typeNode.setSourceCode(sourceCode);
        
        // 提取类声明（不含方法体，用于快速理解）
        String declaration = extractTypeDeclaration(ctType);
        typeNode.setProperty("declaration", declaration);
        
        // 提取路径信息
        if (ctType.getPosition() != null && ctType.getPosition().isValidPosition()) {
            File file = ctType.getPosition().getFile();
            if (file != null) {
                String absolutePath = file.getAbsolutePath();
                typeNode.setAbsolutePath(absolutePath);
                // relativePath将在NodeExtractor中统一设置
            }
        }
        
        // 提取 Javadoc（严格的文档注释）
        String javadoc = JavadocExtractor.extractJavadoc(ctType);
        typeNode.setDocumentation(javadoc);
        
        // 提取所有注释（包括行注释、块注释）
        String allComments = JavadocExtractor.extractAllComments(ctType);
        typeNode.setComments(allComments);
        
        // 提取泛型信息
        if (!ctType.getFormalCtTypeParameters().isEmpty()) {
            String genericSignature = ctType.getFormalCtTypeParameters().toString();
            typeNode.setProperty("genericSignature", genericSignature);
        }
        
        // 记录行号
        if (ctType.getPosition() != null && ctType.getPosition().isValidPosition()) {
            typeNode.setLineRange(
                ctType.getPosition().getLine(),
                ctType.getPosition().getEndLine()
            );
        }
        
        // 记录父类
        if (ctType.getSuperclass() != null) {
            typeNode.setSuperClass(ctType.getSuperclass().getQualifiedName());
        }
        
        // 记录实现的接口
        for (var interfaceRef : ctType.getSuperInterfaces()) {
            typeNode.addInterface(interfaceRef.getQualifiedName());
        }
        
        // 统计方法和字段数量（后续会更新）
        typeNode.setProperty("methodCount", ctType.getMethods().size());
        typeNode.setProperty("fieldCount", ctType.getFields().size());
        
        // 生成语义摘要
        typeNode.generateSemanticSummary();
        
        // 添加到索引
        nodeIndex.addType(typeNode);
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

    private String extractTypeDeclaration(CtType<?> ctType) {
        // 提取类声明：修饰符 + 类型关键字 + 名称 + 泛型 + 继承/实现
        StringBuilder declaration = new StringBuilder();
        
        // 注解
        for (var annotation : ctType.getAnnotations()) {
            declaration.append("@").append(annotation.getAnnotationType().getSimpleName()).append(" ");
        }
        
        // 修饰符
        if (ctType.getVisibility() != null) {
            declaration.append(ctType.getVisibility().toString().toLowerCase()).append(" ");
        }
        if (ctType.isAbstract() && !(ctType instanceof CtInterface)) {
            declaration.append("abstract ");
        }
        if (ctType.isFinal()) declaration.append("final ");
        if (ctType.isStatic()) declaration.append("static ");
        
        // 类型关键字
        if (ctType instanceof CtClass) declaration.append("class ");
        else if (ctType instanceof CtInterface) declaration.append("interface ");
        else if (ctType instanceof CtEnum) declaration.append("enum ");
        else if (ctType instanceof CtAnnotationType) declaration.append("@interface ");
        
        // 名称
        declaration.append(ctType.getSimpleName());
        
        // 泛型
        if (!ctType.getFormalCtTypeParameters().isEmpty()) {
            declaration.append("<");
            boolean first = true;
            for (var typeParam : ctType.getFormalCtTypeParameters()) {
                if (!first) declaration.append(", ");
                declaration.append(typeParam.getSimpleName());
                first = false;
            }
            declaration.append(">");
        }
        
        // 继承
        if (ctType.getSuperclass() != null && 
            !ctType.getSuperclass().getQualifiedName().equals("java.lang.Object")) {
            declaration.append(" extends ").append(ctType.getSuperclass().getSimpleName());
        }
        
        // 实现
        if (!ctType.getSuperInterfaces().isEmpty()) {
            declaration.append(ctType instanceof CtInterface ? " extends " : " implements ");
            boolean first = true;
            for (var interfaceRef : ctType.getSuperInterfaces()) {
                if (!first) declaration.append(", ");
                declaration.append(interfaceRef.getSimpleName());
                first = false;
            }
        }
        
        return declaration.toString();
    }
    
    /**
     * 判断是否是测试类
     * 判断依据：
     * 1. 类名以Test结尾或Test开头
     * 2. 类有测试相关的注解（@RunWith, @ExtendWith, @SpringBootTest等）
     * 3. 类在测试路径下（src/test目录）
     * 4. 类中包含带@Test注解的方法
     */
    private boolean isTestClass(CtType<?> ctType) {
        // 1. 检查类名约定
        String simpleName = ctType.getSimpleName();
        if (simpleName.endsWith("Test") || simpleName.endsWith("Tests") ||
            simpleName.startsWith("Test") || simpleName.contains("TestCase")) {
            return true;
        }
        
        // 2. 检查测试框架注解
        for (var annotation : ctType.getAnnotations()) {
            String annotationType = annotation.getAnnotationType().getQualifiedName();
            // JUnit, Spring Boot Test, TestNG等
            if (annotationType.contains("RunWith") ||
                annotationType.contains("ExtendWith") ||
                annotationType.contains("SpringBootTest") ||
                annotationType.contains("WebMvcTest") ||
                annotationType.contains("DataJpaTest") ||
                annotationType.contains("Test")) {  // TestNG的@Test可以加在类上
                return true;
            }
        }
        
        // 3. 检查是否在测试路径下
        if (ctType.getPosition() != null && ctType.getPosition().isValidPosition()) {
            File file = ctType.getPosition().getFile();
            if (file != null) {
                String absolutePath = file.getAbsolutePath();
                if (absolutePath.contains("src/test/") || absolutePath.contains("src\\test\\")) {
                    // 如果在测试路径下，检查是否包含测试方法
                    return hasTestMethods(ctType);
                }
            }
        }
        
        // // 4. 检查是否包含测试方法（即使不在测试路径下）
        // if (hasTestMethods(ctType)) {
        //     return true;
        // }
        
        return false;
    }
    
    /**
     * 检查类是否包含测试方法
     */
    private boolean hasTestMethods(CtType<?> ctType) {
        for (CtMethod<?> method : ctType.getAllMethods()) {
            // 检查方法的注解
            for (var annotation : method.getAnnotations()) {
                String annotationType = annotation.getAnnotationType().getQualifiedName();
                if (annotationType.contains("Test") ||
                    annotationType.equals("org.junit.jupiter.api.Test") ||
                    annotationType.equals("org.junit.Test") ||
                    annotationType.equals("org.testng.annotations.Test")) {
                    return true;
                }
            }
        }
        return false;
    }
}

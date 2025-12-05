package com.github.spoon.visitors;

import com.github.model.nodes.MethodNode;
import com.github.spoon.index.NodeIndex;
import com.github.spoon.extractors.JavadocExtractor;
import spoon.reflect.declaration.*;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.visitor.CtScanner;
import java.io.File;

/**
 * 方法访问者 - 访问所有方法和构造函数
 * 作用：
 * 1. 遍历所有方法和构造函数
 * 2. 提取方法签名、参数、返回类型
 * 3. 提取方法体源代码
 * 4. 创建 MethodNode 并注册到索引
 */
public class MethodVisitor extends CtScanner {
    
    private final NodeIndex nodeIndex;
    
    public MethodVisitor(NodeIndex nodeIndex) {
        this.nodeIndex = nodeIndex;
    }
    
    @Override
    public <T> void visitCtMethod(CtMethod<T> method) {
        // 访问普通方法
        createMethodNode(method, MethodNode.MethodKind.SOURCE_METHOD);
        super.visitCtMethod(method);
    }
    
    @Override
    public <T> void visitCtConstructor(CtConstructor<T> constructor) {
        // 访问构造函数
        createMethodNode(constructor, MethodNode.MethodKind.CONSTRUCTOR);
        super.visitCtConstructor(constructor);
    }
    
    private void createMethodNode(CtExecutable<?> executable, MethodNode.MethodKind kind) {
        // 生成方法签名（作为唯一标识）
        String declaringClassName = "";
        CtType<?> declaringType = null;
        if (executable instanceof CtMethod) {
            declaringType = ((CtMethod<?>) executable).getDeclaringType();
            declaringClassName = declaringType.getQualifiedName();
        } else if (executable instanceof CtConstructor) {
            declaringType = ((CtConstructor<?>) executable).getDeclaringType();
            declaringClassName = declaringType.getQualifiedName();
        }
        String spoonSignature = executable.getSignature();
        String signature = declaringClassName + "#" + spoonSignature;
        
        // 判断是否是测试方法
        if (kind == MethodNode.MethodKind.SOURCE_METHOD && isTestMethod(executable, declaringType)) {
            kind = MethodNode.MethodKind.TEST_METHOD;
        }
        
        MethodNode methodNode = new MethodNode(signature, kind);
        
        // 设置方法名
        methodNode.setName(executable.getSimpleName());
        
        // 设置修饰符
        if (executable instanceof CtMethod) {
            CtMethod<?> method = (CtMethod<?>) executable;
            methodNode.setAbstract(method.isAbstract())
                      .setStatic(method.isStatic())
                      .setVisibility(method.getVisibility() != null ? 
                          method.getVisibility().toString().toLowerCase() : "package");
            methodNode.setProperty("isFinal", method.isFinal());
            methodNode.setProperty("isSynchronized", method.isSynchronized());
        } else if (executable instanceof CtConstructor) {
            CtConstructor<?> constructor = (CtConstructor<?>) executable;
            methodNode.setVisibility(constructor.getVisibility() != null ? 
                constructor.getVisibility().toString().toLowerCase() : "package");
        }
        
        // 设置行号范围
        if (executable.getPosition() != null && executable.getPosition().isValidPosition()) {
            methodNode.setLineRange(
                executable.getPosition().getLine(),
                executable.getPosition().getEndLine()
            );
        }
        
        // 提取方法签名声明（不含方法体）
        String declaration = extractMethodDeclaration(executable);
        methodNode.setProperty("declaration", declaration);
        
        // 构造源码（不包含 Javadoc）
        String sourceCode;
        CtBlock<?> body = executable.getBody();
        if (body != null) {
            sourceCode = declaration + " " + body.toString();
        } else {
            sourceCode = declaration + ";";
        }
        methodNode.setSourceCode(sourceCode);
        
        // 提取路径信息
        if (executable.getPosition() != null && executable.getPosition().isValidPosition()) {
            File file = executable.getPosition().getFile();
            if (file != null) {
                String absolutePath = file.getAbsolutePath();
                methodNode.setAbsolutePath(absolutePath);
                // relativePath将在NodeExtractor中统一设置
            }
        }
        
        // 提取 Javadoc（严格的文档注释）
        String javadoc = JavadocExtractor.extractJavadoc(executable);
        methodNode.setDocumentation(javadoc);
        
        // 提取所有注释（包括行注释、块注释）
        String allComments = JavadocExtractor.extractAllComments(executable);
        methodNode.setComments(allComments);
        
        // 提取返回类型（如果是方法）
        if (executable instanceof CtMethod) {
            CtMethod<?> method = (CtMethod<?>) executable;
            String returnType = method.getType().getQualifiedName();
            methodNode.setReturnType(returnType);
        }
        
        // 提取参数信息
        for (int i = 0; i < executable.getParameters().size(); i++) {
            CtParameter<?> param = executable.getParameters().get(i);
            methodNode.addParameter(
                param.getType().getQualifiedName(),
                param.getSimpleName()
            );
        }
        
        // 提取异常声明
        for (var thrownType : executable.getThrownTypes()) {
            methodNode.addException(thrownType.getQualifiedName());
        }
        
        // 提取注解
        for (var annotation : executable.getAnnotations()) {
            methodNode.addAnnotation(annotation.getAnnotationType().getQualifiedName());
        }
        
        // 计算圈复杂度（简单估算：基于控制流语句数量）
        int complexity = calculateCyclomaticComplexity(executable);
        methodNode.setCyclomaticComplexity(complexity);
        
        // 生成语义摘要
        methodNode.generateSemanticSummary();
        
        // 添加到索引
        nodeIndex.addMethod(methodNode);
    }
    
    private String extractMethodDeclaration(CtExecutable<?> executable) {
        StringBuilder declaration = new StringBuilder();
        
        // 注解
        for (var annotation : executable.getAnnotations()) {
            declaration.append("@").append(annotation.getAnnotationType().getSimpleName()).append(" ");
        }
        
        // 修饰符
        if (executable instanceof CtMethod) {
            CtMethod<?> method = (CtMethod<?>) executable;
            if (method.getVisibility() != null) {
                declaration.append(method.getVisibility().toString().toLowerCase()).append(" ");
            }
            if (method.isAbstract()) declaration.append("abstract ");
            if (method.isStatic()) declaration.append("static ");
            if (method.isFinal()) declaration.append("final ");
            if (method.isSynchronized()) declaration.append("synchronized ");
        } else if (executable instanceof CtConstructor) {
            CtConstructor<?> constructor = (CtConstructor<?>) executable;
            if (constructor.getVisibility() != null) {
                declaration.append(constructor.getVisibility().toString().toLowerCase()).append(" ");
            }
        }
        
        // 返回类型（仅对方法）
        if (executable instanceof CtMethod) {
            CtMethod<?> method = (CtMethod<?>) executable;
            declaration.append(method.getType().getSimpleName()).append(" ");
        }
        
        // 方法名
        declaration.append(executable.getSimpleName());
        
        // 参数
        declaration.append("(");
        boolean first = true;
        for (CtParameter<?> param : executable.getParameters()) {
            if (!first) declaration.append(", ");
            declaration.append(param.getType().getSimpleName())
                       .append(" ")
                       .append(param.getSimpleName());
            first = false;
        }
        declaration.append(")");
        
        // 异常
        if (!executable.getThrownTypes().isEmpty()) {
            declaration.append(" throws ");
            first = true;
            for (var thrownType : executable.getThrownTypes()) {
                if (!first) declaration.append(", ");
                declaration.append(thrownType.getSimpleName());
                first = false;
            }
        }
        
        return declaration.toString();
    }
    
    private int calculateCyclomaticComplexity(CtExecutable<?> executable) {
        // 基础复杂度为1
        int complexity = 1;
        
        // 简单估算：统计控制流语句
        String code = executable.toString();
        
        // 统计 if, for, while, case, catch, &&, ||
        complexity += countOccurrences(code, " if ");
        complexity += countOccurrences(code, " if(");
        complexity += countOccurrences(code, " for ");
        complexity += countOccurrences(code, " for(");
        complexity += countOccurrences(code, " while ");
        complexity += countOccurrences(code, " while(");
        complexity += countOccurrences(code, " case ");
        complexity += countOccurrences(code, " catch ");
        complexity += countOccurrences(code, " catch(");
        complexity += countOccurrences(code, "&&");
        complexity += countOccurrences(code, "||");
        
        return complexity;
    }
    
    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
    
    /**
     * 判断是否是测试方法
     * 判断依据：
     * 1. 检查是否有测试注解（@Test, @ParameterizedTest等）
     * 2. 检查方法名是否以test开头
     * 3. 检查所在文件路径是否在test目录下
     */
    private boolean isTestMethod(CtExecutable<?> executable, CtType<?> declaringType) {
        // 1. 检查注解（最可靠的方式）
        for (var annotation : executable.getAnnotations()) {
            String annotationType = annotation.getAnnotationType().getQualifiedName();
            // JUnit 4/5, TestNG等测试注解
            if (annotationType.contains("Test") || 
                annotationType.equals("org.junit.jupiter.api.Test") ||
                annotationType.equals("org.junit.Test") ||
                annotationType.equals("org.junit.jupiter.params.ParameterizedTest") ||
                annotationType.equals("org.junit.jupiter.api.RepeatedTest") ||
                annotationType.equals("org.testng.annotations.Test")) {
                return true;
            }
        }
        
        // 2. 检查是否在测试路径下
        if (isInTestPath(declaringType)) {
            return true;
        }
        
        // 3. 检查类是否在测试路径下（辅助判断）
        // 如果类在测试路径下且方法是public void类型，很可能是测试方法
        // if (isInTestPath(declaringType) && executable instanceof CtMethod) {
        //     CtMethod<?> method = (CtMethod<?>) executable;
        //     String returnType = method.getType().getQualifiedName();
        //     if ("void".equals(returnType) && 
        //         (method.getVisibility() == null || 
        //          method.getVisibility().toString().equalsIgnoreCase("public"))) {
        //         // 排除生命周期方法
        //         String name = method.getSimpleName();
        //         if (!name.equals("setUp") && !name.equals("tearDown") && 
        //             !name.equals("before") && !name.equals("after")) {
        //             // 检查是否有Before/After等注解
        //             boolean hasLifecycleAnnotation = false;
        //             for (var annotation : method.getAnnotations()) {
        //                 String annotationType = annotation.getAnnotationType().getSimpleName();
        //                 if (annotationType.contains("Before") || 
        //                     annotationType.contains("After") ||
        //                     annotationType.contains("BeforeEach") || 
        //                     annotationType.contains("AfterEach")) {
        //                     hasLifecycleAnnotation = true;
        //                     break;
        //                 }
        //             }
        //             if (!hasLifecycleAnnotation) {
        //                 return true;
        //             }
        //         }
        //     }
        // }
        
        return false;
    }
    
    /**
     * 检查类型是否在测试路径下
     */
    private boolean isInTestPath(CtType<?> type) {
        if (type == null || type.getPosition() == null || !type.getPosition().isValidPosition()) {
            return false;
        }
        
        File file = type.getPosition().getFile();
        if (file != null) {
            String absolutePath = file.getAbsolutePath();
            // 检查是否包含 src/test 或 src\test
            return absolutePath.contains("src/test/") || absolutePath.contains("src\\test\\");
        }
        
        return false;
    }
}

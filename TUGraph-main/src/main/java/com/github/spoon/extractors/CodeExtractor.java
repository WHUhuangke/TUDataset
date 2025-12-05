package com.github.spoon.extractors;

import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtField;

/**
 * 代码提取器 - 提取源代码文本
 * 作用：
 * 1. 提取元素的完整源代码
 * 2. 提取代码摘要（不含实现细节）
 * 3. 保留格式和注释
 */
public class CodeExtractor {
    
    /**
     * 提取完整的源代码（包含注释和格式）
     */
    public static String extractFullCode(CtElement element) {
        if (element == null) {
            return "";
        }
        return element.toString();
    }
    
    /**
     * 提取类型的声明（不含方法体）
     */
    public static String extractTypeDeclaration(CtType<?> type) {
        StringBuilder sb = new StringBuilder();
        
        // 包声明
        if (type.getPackage() != null) {
            sb.append("package ").append(type.getPackage().getQualifiedName()).append(";\n\n");
        }
        
        // 导入语句（从编译单元获取）
        if (type.getPosition() != null && type.getPosition().getCompilationUnit() != null) {
            var cu = type.getPosition().getCompilationUnit();
            for (var importRef : cu.getImports()) {
                sb.append(importRef).append("\n");
            }
            if (!cu.getImports().isEmpty()) {
                sb.append("\n");
            }
        }
        
        // 类声明头
        sb.append(type.getModifiers()).append(" ");
        
        if (type instanceof spoon.reflect.declaration.CtClass) {
            sb.append("class ");
        } else if (type instanceof spoon.reflect.declaration.CtInterface) {
            sb.append("interface ");
        } else if (type instanceof spoon.reflect.declaration.CtEnum) {
            sb.append("enum ");
        }
        
        sb.append(type.getSimpleName());
        
        // 泛型参数
        if (!type.getFormalCtTypeParameters().isEmpty()) {
            sb.append(type.getFormalCtTypeParameters());
        }
        
        // 继承和实现
        if (type.getSuperclass() != null) {
            sb.append(" extends ").append(type.getSuperclass().getSimpleName());
        }
        if (!type.getSuperInterfaces().isEmpty()) {
            sb.append(" implements ");
            boolean first = true;
            for (var iface : type.getSuperInterfaces()) {
                if (!first) sb.append(", ");
                sb.append(iface.getSimpleName());
                first = false;
            }
        }
        
        sb.append(" {\n");
        
        // 字段声明（不含初始化复杂逻辑）
        for (var field : type.getFields()) {
            sb.append("    ").append(extractFieldSignature(field)).append(";\n");
        }
        
        if (!type.getFields().isEmpty()) {
            sb.append("\n");
        }
        
        // 方法签名（不含方法体）
        for (var method : type.getMethods()) {
            sb.append("    ").append(extractMethodSignature(method)).append(";\n");
        }
        
        sb.append("}");
        
        return sb.toString();
    }
    
    /**
     * 提取字段签名
     */
    public static String extractFieldSignature(CtField<?> field) {
        StringBuilder sb = new StringBuilder();
        
        // 注解
        for (var annotation : field.getAnnotations()) {
            sb.append("@").append(annotation.getAnnotationType().getSimpleName()).append(" ");
        }
        
        // 修饰符
        sb.append(field.getVisibility() != null ? field.getVisibility().toString().toLowerCase() : "")
          .append(" ");
        if (field.isStatic()) sb.append("static ");
        if (field.isFinal()) sb.append("final ");
        if (field.isTransient()) sb.append("transient ");
        if (field.isVolatile()) sb.append("volatile ");
        
        // 类型和名称
        sb.append(field.getType().getSimpleName()).append(" ").append(field.getSimpleName());
        
        return sb.toString();
    }
    
    /**
     * 提取方法签名
     */
    public static String extractMethodSignature(CtMethod<?> method) {
        StringBuilder sb = new StringBuilder();
        
        // 注解
        for (var annotation : method.getAnnotations()) {
            sb.append("@").append(annotation.getAnnotationType().getSimpleName()).append(" ");
        }
        
        // 修饰符
        sb.append(method.getVisibility() != null ? method.getVisibility().toString().toLowerCase() : "")
          .append(" ");
        if (method.isAbstract()) sb.append("abstract ");
        if (method.isStatic()) sb.append("static ");
        if (method.isFinal()) sb.append("final ");
        if (method.isSynchronized()) sb.append("synchronized ");
        
        // 返回类型
        sb.append(method.getType().getSimpleName()).append(" ");
        
        // 方法名
        sb.append(method.getSimpleName());
        
        // 参数
        sb.append("(");
        boolean first = true;
        for (var param : method.getParameters()) {
            if (!first) sb.append(", ");
            sb.append(param.getType().getSimpleName()).append(" ").append(param.getSimpleName());
            first = false;
        }
        sb.append(")");
        
        // 异常
        if (!method.getThrownTypes().isEmpty()) {
            sb.append(" throws ");
            first = true;
            for (var exception : method.getThrownTypes()) {
                if (!first) sb.append(", ");
                sb.append(exception.getSimpleName());
                first = false;
            }
        }
        
        return sb.toString();
    }
}

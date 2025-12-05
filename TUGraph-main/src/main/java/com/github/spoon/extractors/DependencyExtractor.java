package com.github.spoon.extractors;

import com.github.model.edges.*;
import com.github.spoon.index.NodeIndex;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;

/**
 * 依赖关系提取器
 * 作用：
 * 1. 提取类之间的继承关系（Extends）
 * 2. 提取类之间的实现关系（Implements）
 * 3. 提取方法覆盖关系（Overrides）
 */
public class DependencyExtractor {
    
    private final NodeIndex nodeIndex;
    
    public DependencyExtractor(NodeIndex nodeIndex) {
        this.nodeIndex = nodeIndex;
    }
    
    /**
     * 提取继承关系
     */
    public List<ExtendsEdge> extractExtendsRelations(CtType<?> ctType) {
        List<ExtendsEdge> edges = new ArrayList<>();
        
        String subTypeQName = ctType.getQualifiedName();
        
        // 提取父类
        CtTypeReference<?> superclass = ctType.getSuperclass();
        if (superclass != null && 
            !superclass.getQualifiedName().equals("java.lang.Object")) {
            
            String superTypeQName = superclass.getQualifiedName();
            
            // 只记录项目内的类型
            if (nodeIndex.getType(superTypeQName).isPresent()) {
                ExtendsEdge edge = new ExtendsEdge(subTypeQName, superTypeQName);
                
                // 记录继承声明
                String declaration = ctType.getSimpleName() + " extends " + superclass.getSimpleName();
                edge.setInheritanceDeclaration(declaration);
                
                edges.add(edge);
            }
        }
        
        return edges;
    }
    
    /**
     * 提取实现关系
     */
    public List<ImplementsEdge> extractImplementsRelations(CtType<?> ctType) {
        List<ImplementsEdge> edges = new ArrayList<>();
        
        String implementorQName = ctType.getQualifiedName();
        
        // 提取实现的接口
        for (CtTypeReference<?> interfaceRef : ctType.getSuperInterfaces()) {
            String interfaceQName = interfaceRef.getQualifiedName();
            
            // 只记录项目内的接口
            if (nodeIndex.getType(interfaceQName).isPresent()) {
                ImplementsEdge edge = new ImplementsEdge(implementorQName, interfaceQName);
                
                String declaration = ctType.getSimpleName() + " implements " + interfaceRef.getSimpleName();
                edge.setImplementationDeclaration(declaration);
                
                edges.add(edge);
            }
        }
        
        return edges;
    }
    
    /**
     * 提取方法覆盖关系
     * 需要比较当前类的方法和父类/接口的方法
     */
    public List<OverridesEdge> extractOverridesRelations(CtType<?> ctType) {
        List<OverridesEdge> edges = new ArrayList<>();
        
        // 获取所有父类和接口
        List<CtTypeReference<?>> superTypes = new ArrayList<>();
        if (ctType.getSuperclass() != null) {
            superTypes.add(ctType.getSuperclass());
        }
        superTypes.addAll(ctType.getSuperInterfaces());
        
        // 对于当前类的每个方法，检查是否覆盖了父类/接口方法
        for (var method : ctType.getMethods()) {
            // 生成当前方法的完整签名：类名#方法签名
            String currentTypeQName = ctType.getQualifiedName();
            String methodSignature = method.getSignature();
            String fullMethodSignature = currentTypeQName + "#" + methodSignature;
            
            for (CtTypeReference<?> superTypeRef : superTypes) {
                String superTypeQName = superTypeRef.getQualifiedName();
                
                // 检查父类型是否在索引中
                if (nodeIndex.getType(superTypeQName).isPresent()) {
                    // 构造父类方法的完整签名：父类名#方法签名
                    String superMethodSignature = superTypeQName + "#" + methodSignature;
                    
                    // 检查父类方法是否存在
                    if (nodeIndex.getMethod(superMethodSignature).isPresent()) {
                        OverridesEdge edge = new OverridesEdge(fullMethodSignature, superMethodSignature);
                        
                        // 检查是否有 @Override 注解
                        boolean hasOverride = method.getAnnotations().stream()
                            .anyMatch(ann -> ann.getAnnotationType().getSimpleName().equals("Override"));
                        edge.setHasAnnotation(hasOverride);
                        
                        // 判断是接口实现还是方法覆盖
                        boolean isInterface = superTypeRef.isInterface();
                        edge.setInterfaceImplementation(isInterface);
                        
                        edges.add(edge);
                    }
                }
            }
        }
        
        return edges;
    }
}

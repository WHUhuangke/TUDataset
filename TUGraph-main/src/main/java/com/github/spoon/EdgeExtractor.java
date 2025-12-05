package com.github.spoon;

import com.github.logging.GraphLogger;
import com.github.model.KnowledgeGraph;
import com.github.model.edges.*;
import com.github.model.nodes.*;
import com.github.spoon.extractors.CallGraphExtractor;
import com.github.spoon.extractors.DependencyExtractor;
import com.github.spoon.index.NodeIndex;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 边提取器 - 第二遍遍历
 * 作用：
 * 1. 基于已建立的节点索引提取关系
 * 2. 创建各种类型的边（Declares, Extends, Implements, Calls, Reads, Writes等）
 * 3. 将边添加到知识图谱中
 */
public class EdgeExtractor {
    
    private final GraphLogger logger = GraphLogger.getInstance();
    private final NodeIndex nodeIndex;
    private final CtModel model;
    private final KnowledgeGraph knowledgeGraph;
    private final EdgeMerger edgeMerger;
    
    // 临时存储所有提取的边,最后统一合并
    private final List<CallsEdge> tempCallsEdges = new ArrayList<>();
    private final List<ReadsEdge> tempReadsEdges = new ArrayList<>();
    private final List<WritesEdge> tempWritesEdges = new ArrayList<>();
    private final List<TestsEdge> tempTestsEdges = new ArrayList<>();
    
    public EdgeExtractor(NodeIndex nodeIndex, CtModel model) {
        this.nodeIndex = nodeIndex;
        this.model = model;
        this.knowledgeGraph = new KnowledgeGraph();
        this.edgeMerger = new EdgeMerger();
        
        // 先将所有节点添加到知识图谱
        addAllNodesToGraph();
    }
    
    /**
     * 执行边提取
     */
    public KnowledgeGraph extract() {
        System.out.println("\n=== Phase 2: Extracting Edges ===");
        
        // 1. 提取项目级别的关系
        extractProjectRelations();
        
        // 2. 提取包级别的关系
        extractPackageRelations();
        
        // 3. 提取文件级别的关系
        extractFileRelations();
        
        // 4. 提取类型级别的关系
        extractTypeRelations();
        
        // 5. 提取方法级别的关系
        extractMethodRelations();
        
        // 6. 提取字段级别的关系
        extractFieldRelations();
        
        // 7. 合并重复的边
        mergeRedundantEdges();
        
        // 8. 打印统计信息
        printStatistics();
        
        return knowledgeGraph;
    }
    
    /**
     * 将所有节点添加到知识图谱
     * 注意: ParameterNode 和 AnnotationNode 已移除
     * - 参数信息现在作为 UsesParameterEdge 的属性存储
     * - 注解信息现在作为节点属性存储
     */
    private void addAllNodesToGraph() {
        nodeIndex.getAllProjects().forEach(knowledgeGraph::addNode);
        nodeIndex.getAllPackages().forEach(knowledgeGraph::addNode);
        nodeIndex.getAllFiles().forEach(knowledgeGraph::addNode);
        nodeIndex.getAllTypes().forEach(knowledgeGraph::addNode);
        nodeIndex.getAllMethods().forEach(knowledgeGraph::addNode);
        nodeIndex.getAllFields().forEach(knowledgeGraph::addNode);
        // nodeIndex.getAllParameters().forEach(knowledgeGraph::addNode); // 已移除
        // nodeIndex.getAllAnnotations().forEach(knowledgeGraph::addNode); // 已移除
    }
    
    /**
     * 提取项目级别的关系：Project -> Package, Project -> File
     */
    private void extractProjectRelations() {
        System.out.println("Extracting project relations...");
        
        if (nodeIndex.getAllProjects().isEmpty()) {
            return;
        }
        
        ProjectNode project = nodeIndex.getAllProjects().iterator().next();
        
        // Project -> Package
        for (PackageNode pkg : nodeIndex.getAllPackages()) {
            HasPackageEdge edge = new HasPackageEdge(project.getId(), pkg.getId());
            edge.setPackageContext(pkg.getQualifiedName(), 0, 0);
            knowledgeGraph.addEdge(edge);
        }
        
        // Project -> File
        for (FileNode file : nodeIndex.getAllFiles()) {
            ContainsFileEdge edge = new ContainsFileEdge(project.getId(), file.getId());
            edge.setRelativePath(file.getPath());
            knowledgeGraph.addEdge(edge);
        }
        
        System.out.println("Created project relations");
    }
    
    /**
     * 提取包级别的关系：Type -> Package
     */
    private void extractPackageRelations() {
        System.out.println("Extracting package relations...");
        
        int count = 0;
        for (CtType<?> ctType : model.getAllTypes()) {
            if (ctType.getPackage() == null || ctType.getPackage().isUnnamedPackage()) {
                continue;
            }
            
            String typeQName = ctType.getQualifiedName();
            String packageQName = ctType.getPackage().getQualifiedName();
            
            if (nodeIndex.getType(typeQName).isPresent() && 
                nodeIndex.getPackage(packageQName).isPresent()) {
                
                InPackageEdge edge = new InPackageEdge(typeQName, packageQName);
                knowledgeGraph.addEdge(edge);
                count++;
            }
        }
        
        System.out.println("Created " + count + " package relations");
    }
    
    /**
     * 提取文件级别的关系：File -> Type
     */
    private void extractFileRelations() {
        System.out.println("Extracting file relations...");
        
        int count = 0;
        for (CtType<?> ctType : model.getAllTypes()) {
            if (ctType.getPosition() == null || !ctType.getPosition().isValidPosition()) {
                continue;
            }
            
            String typeQName = ctType.getQualifiedName();
            String filePath = ctType.getPosition().getFile().getAbsolutePath();
            
            if (nodeIndex.getType(typeQName).isPresent() && 
                nodeIndex.getFile(filePath).isPresent()) {
                
                DeclaresTypeEdge edge = new DeclaresTypeEdge(filePath, typeQName);
                edge.setPrimaryType(true); // 简化处理，假设是主类型
                knowledgeGraph.addEdge(edge);
                count++;
            }
        }
        
        System.out.println("Created " + count + " file relations");
    }
    
    /**
     * 提取类型级别的关系：Extends, Implements, Declares
     */
    private void extractTypeRelations() {
        System.out.println("Extracting type relations...");
        
        DependencyExtractor dependencyExtractor = new DependencyExtractor(nodeIndex);
        
        int extendsCount = 0, implementsCount = 0, declaresCount = 0;
        
        for (CtType<?> ctType : model.getAllTypes()) {
            String typeQName = ctType.getQualifiedName();
            
            if (!nodeIndex.getType(typeQName).isPresent()) {
                continue;
            }
            
            // 提取继承关系
            List<ExtendsEdge> extendsEdges = dependencyExtractor.extractExtendsRelations(ctType);
            for (ExtendsEdge edge : extendsEdges) {
                knowledgeGraph.addEdge(edge);
                extendsCount++;
            }
            
            // 提取实现关系
            List<ImplementsEdge> implementsEdges = dependencyExtractor.extractImplementsRelations(ctType);
            for (ImplementsEdge edge : implementsEdges) {
                knowledgeGraph.addEdge(edge);
                implementsCount++;
            }
            
            // Type -> Method (Declares)
            for (CtMethod<?> method : ctType.getMethods()) {
                String methodSignature = typeQName + "#" + method.getSignature();
                if (nodeIndex.getMethod(methodSignature).isPresent()) {
                    DeclaresEdge edge = new DeclaresEdge(typeQName, methodSignature);
                    edge.setMemberKind("method");
                    knowledgeGraph.addEdge(edge);
                    declaresCount++;
                }
            }
            
            // Type -> Constructor (Declares)
            for (CtTypeMember member : ctType.getTypeMembers()) {
                if (member instanceof CtConstructor<?>) {
                    CtConstructor<?> constructor = (CtConstructor<?>) member;
                    String constructorSignature = typeQName + "#" + constructor.getSignature();
                    if (nodeIndex.getMethod(constructorSignature).isPresent()) {
                        DeclaresEdge edge = new DeclaresEdge(typeQName, constructorSignature);
                        edge.setMemberKind("constructor");
                        knowledgeGraph.addEdge(edge);
                        declaresCount++;
                    }
                }
            }
            
            // Type -> Field (Declares)
            for (CtField<?> field : ctType.getFields()) {
                String fieldQName = typeQName + "#" + field.getSimpleName();
                if (nodeIndex.getField(fieldQName).isPresent()) {
                    DeclaresEdge edge = new DeclaresEdge(typeQName, fieldQName);
                    edge.setMemberKind("field");
                    knowledgeGraph.addEdge(edge);
                    declaresCount++;
                }
            }
        }
        
        System.out.println("Created " + extendsCount + " extends, " + 
                         implementsCount + " implements, " + 
                         declaresCount + " declares relations");
    }
    
    /**
     * 提取方法级别的关系：Calls, HasParam, ReturnType, Throws, Overrides
     */
    private void extractMethodRelations() {
        System.out.println("Extracting method relations...");
        
        CallGraphExtractor callGraphExtractor = new CallGraphExtractor(nodeIndex);
        DependencyExtractor dependencyExtractor = new DependencyExtractor(nodeIndex);
        
        int callsCount = 0, readsCount = 0, writesCount = 0;
        int paramCount = 0, returnCount = 0, throwsCount = 0;
        
        for (CtType<?> ctType : model.getAllTypes()) {
            // 提取覆盖关系
            List<OverridesEdge> overridesEdges = dependencyExtractor.extractOverridesRelations(ctType);
            for (OverridesEdge edge : overridesEdges) {
                knowledgeGraph.addEdge(edge);
            }
            
            // 遍历所有方法
            Collection<CtExecutableReference<?>> executableRefs = ctType.getAllExecutables();
            for (CtExecutableReference<?> executableRef : executableRefs) {
                CtExecutable<?> executable = executableRef.getDeclaration();
                if (executable == null) {
                    continue; // 跳过无法解析的引用
                }
                
                String typeQName = ctType.getQualifiedName();
                String methodSignature = typeQName + "#" + executable.getSignature();
                
                if (!nodeIndex.getMethod(methodSignature).isPresent()) {
                    continue;
                }
                
                // 提取方法调用 - 先存储到临时列表
                List<CallsEdge> callsEdges = callGraphExtractor.extractMethodCalls(executable);
                tempCallsEdges.addAll(callsEdges);
                callsCount += callsEdges.size();
                
                // 提取字段读取 - 先存储到临时列表
                List<ReadsEdge> readsEdges = callGraphExtractor.extractFieldReads(executable);
                tempReadsEdges.addAll(readsEdges);
                readsCount += readsEdges.size();
                
                // 提取字段写入 - 先存储到临时列表
                List<WritesEdge> writesEdges = callGraphExtractor.extractFieldWrites(executable);
                tempWritesEdges.addAll(writesEdges);
                writesCount += writesEdges.size();
                
                // 提取测试关系 - 测试方法对生产方法的调用
                List<TestsEdge> testsEdges = callGraphExtractor.extractTestRelations(executable);
                tempTestsEdges.addAll(testsEdges);
                
                // Method -> Type (UsesParameter) - 直接连接方法和参数类型
                for (int i = 0; i < executable.getParameters().size(); i++) {
                    CtParameter<?> param = executable.getParameters().get(i);
                    String paramName = param.getSimpleName();
                    String paramType = param.getType().getQualifiedName();
                    
                    // 只有参数类型在项目中时才创建边
                    if (nodeIndex.getType(paramType).isPresent()) {
                        UsesParameterEdge edge = new UsesParameterEdge(
                            methodSignature, paramType, i, paramName);
                        
                        // 设置修饰符
                        edge.setFinal(param.isFinal());
                        edge.setVarArgs(param.isVarArgs());
                        
                        // 提取注解
                        for (var annotation : param.getAnnotations()) {
                            edge.addAnnotation(annotation.getAnnotationType().getSimpleName());
                        }
                        
                        // 设置参数声明
                        edge.setParameterDeclaration(param.toString());
                        
                        knowledgeGraph.addEdge(edge);
                        paramCount++;
                    }
                }
                
                // Method -> ReturnType (仅对方法)
                if (executable instanceof CtMethod) {
                    CtMethod<?> method = (CtMethod<?>) executable;
                    String returnType = method.getType().getQualifiedName();
                    
                    // 只有返回类型在项目中时才创建边
                    if (nodeIndex.getType(returnType).isPresent()) {
                        ReturnTypeEdge edge = new ReturnTypeEdge(methodSignature, returnType);
                        knowledgeGraph.addEdge(edge);
                        returnCount++;
                    }
                }
                
                // Method -> Exception (Throws)
                for (var thrownType : executable.getThrownTypes()) {
                    String exceptionType = thrownType.getQualifiedName();
                    if (nodeIndex.getType(exceptionType).isPresent()) {
                        ThrowsEdge edge = new ThrowsEdge(methodSignature, exceptionType);
                        knowledgeGraph.addEdge(edge);
                        throwsCount++;
                    }
                }
            }
        }
        
        System.out.println("Created " + callsCount + " calls, " + 
                         readsCount + " reads, " + writesCount + " writes, " + 
                         tempTestsEdges.size() + " tests");
        System.out.println("Created " + paramCount + " params, " + 
                         returnCount + " returns, " + throwsCount + " throws");
    }
    
    /**
     * 提取字段级别的关系（已在方法级别中处理 Reads/Writes）
     */
    private void extractFieldRelations() {
        System.out.println("Extracting field relations...");
        // 字段的主要关系（Reads/Writes）已在 extractMethodRelations 中处理
        System.out.println("Field relations completed");
    }
    
    /**
     * 合并重复的边关系
     * 将同一对节点之间的多条相同类型边合并为一条,并聚合信息
     */
    private void mergeRedundantEdges() {
        System.out.println("\nMerging redundant edges...");
        logger.info("开始合并重复边关系...");
        
        // 记录合并前的边数量
        int originalCallsCount = tempCallsEdges.size();
        int originalReadsCount = tempReadsEdges.size();
        int originalWritesCount = tempWritesEdges.size();
        int originalTestsCount = tempTestsEdges.size();
        int originalTotal = originalCallsCount + originalReadsCount + originalWritesCount + originalTestsCount;
        
        logger.debug(String.format("合并前: CALLS=%d, READS=%d, WRITES=%d, TESTS=%d, TOTAL=%d",
            originalCallsCount, originalReadsCount, originalWritesCount, originalTestsCount, originalTotal));
        
        // 合并各类型的边
        List<CallsEdge> mergedCallsEdges = edgeMerger.mergeEdges(tempCallsEdges);
        List<ReadsEdge> mergedReadsEdges = edgeMerger.mergeEdges(tempReadsEdges);
        List<WritesEdge> mergedWritesEdges = edgeMerger.mergeEdges(tempWritesEdges);
        List<TestsEdge> mergedTestsEdges = edgeMerger.mergeEdges(tempTestsEdges);
        
        // 添加合并后的边到知识图谱
        mergedCallsEdges.forEach(knowledgeGraph::addEdge);
        mergedReadsEdges.forEach(knowledgeGraph::addEdge);
        mergedWritesEdges.forEach(knowledgeGraph::addEdge);
        mergedTestsEdges.forEach(knowledgeGraph::addEdge);
        
        // 计算统计信息
        int mergedTotal = mergedCallsEdges.size() + mergedReadsEdges.size() + 
                         mergedWritesEdges.size() + mergedTestsEdges.size();
        int reduction = originalTotal - mergedTotal;
        double reductionPercent = originalTotal > 0 ? (double) reduction / originalTotal * 100 : 0;
        
        logger.info(String.format("✓ 边合并完成:"));
        logger.info(String.format("  - CALLS: %d → %d (减少 %d)", 
            originalCallsCount, mergedCallsEdges.size(), originalCallsCount - mergedCallsEdges.size()));
        logger.info(String.format("  - READS: %d → %d (减少 %d)", 
            originalReadsCount, mergedReadsEdges.size(), originalReadsCount - mergedReadsEdges.size()));
        logger.info(String.format("  - WRITES: %d → %d (减少 %d)", 
            originalWritesCount, mergedWritesEdges.size(), originalWritesCount - mergedWritesEdges.size()));
        logger.info(String.format("  - TESTS: %d → %d (减少 %d)", 
            originalTestsCount, mergedTestsEdges.size(), originalTestsCount - mergedTestsEdges.size()));
        logger.info(String.format("  - 总计: %d → %d (减少 %.1f%%)", 
            originalTotal, mergedTotal, reductionPercent));
        
        System.out.println(String.format("Edge merging completed: %d → %d edges (%.1f%% reduction)",
            originalTotal, mergedTotal, reductionPercent));
    }
    
    private void printStatistics() {
        System.out.println("\n=== Edge Statistics ===");
        var stats = knowledgeGraph.getEdgeStatistics();
        stats.forEach((type, count) -> 
            System.out.println(type + ": " + count));
        System.out.println("Total Edges: " + knowledgeGraph.getEdgeCount());
    }
    
    public KnowledgeGraph getKnowledgeGraph() {
        return knowledgeGraph;
    }
}

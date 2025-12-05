package com.github.spoon.extractors;

import com.github.model.edges.CallsEdge;
import com.github.model.edges.ReadsEdge;
import com.github.model.edges.WritesEdge;
import com.github.model.edges.TestsEdge;
import com.github.model.nodes.MethodNode;
import com.github.spoon.index.NodeIndex;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.CtScanner;

import java.util.ArrayList;
import java.util.List;

/**
 * 方法调用图提取器
 * 作用：
 * 1. 分析方法体内的所有方法调用
 * 2. 分析字段的读写操作
 * 3. 创建 Calls、Reads、Writes 边
 */
public class CallGraphExtractor {
    
    private final NodeIndex nodeIndex;
    
    public CallGraphExtractor(NodeIndex nodeIndex) {
        this.nodeIndex = nodeIndex;
    }
    
    /**
     * 提取方法的调用关系
     */
    public List<CallsEdge> extractMethodCalls(CtExecutable<?> executable) {
        List<CallsEdge> callsEdges = new ArrayList<>();
        
        // 生成完整的调用者签名：类名#方法签名
        String declaringType = executable.getParent(spoon.reflect.declaration.CtType.class).getQualifiedName();
        String callerSignature = declaringType + "#" + executable.getSignature();
        
        // 使用 Scanner 遍历方法体
        executable.accept(new CtScanner() {
            @Override
            public <T> void visitCtInvocation(CtInvocation<T> invocation) {
                handleMethodCall(invocation, callerSignature, callsEdges);
                super.visitCtInvocation(invocation);
            }
            
            @Override
            public <T> void visitCtConstructorCall(CtConstructorCall<T> constructorCall) {
                handleConstructorCall(constructorCall, callerSignature, callsEdges);
                super.visitCtConstructorCall(constructorCall);
            }
        });
        
        return callsEdges;
    }
    
    /**
     * 提取测试关系（测试方法 -> 被测方法）
     * 只有当调用者是测试方法，被调用者是非测试方法时，才创建TESTS边
     */
    public List<TestsEdge> extractTestRelations(CtExecutable<?> executable) {
        List<TestsEdge> testsEdges = new ArrayList<>();
        
        // 生成完整的调用者签名：类名#方法签名
        String declaringType = executable.getParent(spoon.reflect.declaration.CtType.class).getQualifiedName();
        String testMethodSignature = declaringType + "#" + executable.getSignature();
        
        // 检查调用者是否是测试方法
        var testMethodNode = nodeIndex.getMethod(testMethodSignature);
        if (testMethodNode.isEmpty()) {
            return testsEdges;
        }
        
        MethodNode methodNode = testMethodNode.get();
        String kind = (String) methodNode.getProperty("kind");
        if (!"TEST_METHOD".equals(kind)) {
            return testsEdges; // 不是测试方法，不创建TESTS边
        }
        
        // 使用 Scanner 遍历方法体，查找对非测试方法的调用
        executable.accept(new CtScanner() {
            @Override
            public <T> void visitCtInvocation(CtInvocation<T> invocation) {
                handleTestMethodCall(invocation, testMethodSignature, testsEdges);
                super.visitCtInvocation(invocation);
            }
            
            @Override
            public <T> void visitCtConstructorCall(CtConstructorCall<T> constructorCall) {
                handleTestConstructorCall(constructorCall, testMethodSignature, testsEdges);
                super.visitCtConstructorCall(constructorCall);
            }
        });
        
        return testsEdges;
    }
    
    /**
     * 提取字段的读取关系
     */
    public List<ReadsEdge> extractFieldReads(CtExecutable<?> executable) {
        List<ReadsEdge> readsEdges = new ArrayList<>();
        
        // 生成完整的方法签名：类名#方法签名
        String declaringType = executable.getParent(spoon.reflect.declaration.CtType.class).getQualifiedName();
        String methodSignature = declaringType + "#" + executable.getSignature();
        
        executable.accept(new CtScanner() {
            @Override
            public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
                handleFieldRead(fieldRead, methodSignature, readsEdges);
                super.visitCtFieldRead(fieldRead);
            }
        });
        
        return readsEdges;
    }
    
    /**
     * 提取字段的写入关系
     */
    public List<WritesEdge> extractFieldWrites(CtExecutable<?> executable) {
        List<WritesEdge> writesEdges = new ArrayList<>();
        
        // 生成完整的方法签名：类名#方法签名
        String declaringType = executable.getParent(spoon.reflect.declaration.CtType.class).getQualifiedName();
        String methodSignature = declaringType + "#" + executable.getSignature();
        
        executable.accept(new CtScanner() {
            @Override
            public <T> void visitCtFieldWrite(CtFieldWrite<T> fieldWrite) {
                handleFieldWrite(fieldWrite, methodSignature, writesEdges);
                super.visitCtFieldWrite(fieldWrite);
            }
        });
        
        return writesEdges;
    }
    
    private <T> void handleMethodCall(CtInvocation<T> invocation, String callerSignature, 
                                     List<CallsEdge> edges) {
        CtExecutableReference<T> execRef = invocation.getExecutable();
        
        // 检查声明类型是否存在（处理不完整的 AST）
        if (execRef.getDeclaringType() == null) {
            // 跳过无法解析的方法调用（通常来自排除的文件或外部依赖）
            return;
        }
        
        // 生成完整的被调用方法签名：类名#方法签名
        String declaringType = execRef.getDeclaringType().getQualifiedName();
        String calleeSignature = declaringType + "#" + execRef.getSignature();
        
        // 检查被调用方法是否在索引中（项目内方法）
        var calleeMethodNode = nodeIndex.getMethod(calleeSignature);
        if (calleeMethodNode.isPresent()) {
            // 检查是否是测试方法调用非测试方法的情况
            // 如果是，则跳过 CALLS 边的创建（因为已经有 TESTS 边了）
            var callerMethodNode = nodeIndex.getMethod(callerSignature);
            if (callerMethodNode.isPresent()) {
                String callerKind = (String) callerMethodNode.get().getProperty("kind");
                String calleeKind = (String) calleeMethodNode.get().getProperty("kind");
                
                // 如果调用者是测试方法，被调用者是非测试方法，则跳过
                if ("TEST_METHOD".equals(callerKind) && !"TEST_METHOD".equals(calleeKind)) {
                    return; // 跳过，由 TESTS 边处理
                }
            }
            
            int lineNumber = invocation.getPosition() != null && invocation.getPosition().isValidPosition()
                    ? invocation.getPosition().getLine()
                    : -1;
            
            CallsEdge edge = new CallsEdge(callerSignature, calleeSignature, lineNumber);
            
            // 记录调用语句
            String statement = invocation.toString();
            edge.setCallStatement(statement);
            
            // 判断调用类型
            if (invocation.getTarget() != null) {
                edge.setCallType("virtual");
            } else {
                edge.setCallType("static");
            }
            
            edges.add(edge);
        }
    }
    
    private <T> void handleConstructorCall(CtConstructorCall<T> constructorCall, 
                                          String callerSignature, List<CallsEdge> edges) {
        CtExecutableReference<T> execRef = constructorCall.getExecutable();
        
        // 检查声明类型是否存在（处理不完整的 AST）
        if (execRef.getDeclaringType() == null) {
            // 跳过无法解析的构造函数调用
            return;
        }
        
        // 生成完整的构造函数签名：类名#构造函数签名
        String declaringType = execRef.getDeclaringType().getQualifiedName();
        String calleeSignature = declaringType + "#" + execRef.getSignature();
        
        var calleeMethodNode = nodeIndex.getMethod(calleeSignature);
        if (calleeMethodNode.isPresent()) {
            // 检查是否是测试方法调用非测试类构造函数的情况
            var callerMethodNode = nodeIndex.getMethod(callerSignature);
            if (callerMethodNode.isPresent()) {
                String callerKind = (String) callerMethodNode.get().getProperty("kind");
                
                // 如果调用者是测试方法，检查被调用构造函数是否在测试路径下
                if ("TEST_METHOD".equals(callerKind)) {
                    String absolutePath = (String) calleeMethodNode.get().getProperty("absolutePath");
                    // 如果构造函数不在测试路径下，跳过 CALLS 边（由 TESTS 边处理）
                    if (absolutePath != null && 
                        !absolutePath.contains("/test/") && 
                        !absolutePath.contains("\\test\\")) {
                        return; // 跳过，由 TESTS 边处理
                    }
                }
            }
            
            int lineNumber = constructorCall.getPosition() != null && constructorCall.getPosition().isValidPosition()
                    ? constructorCall.getPosition().getLine()
                    : -1;
            
            CallsEdge edge = new CallsEdge(callerSignature, calleeSignature, lineNumber);
            edge.setCallStatement(constructorCall.toString());
            edge.setCallType("special");
            
            edges.add(edge);
        }
    }
    
    private <T> void handleFieldRead(CtFieldRead<T> fieldRead, String methodSignature, 
                                    List<ReadsEdge> edges) {
        CtFieldReference<T> fieldRef = fieldRead.getVariable();
        
        // 检查声明类型是否存在（处理不完整的 AST）
        if (fieldRef.getDeclaringType() == null) {
            return;
        }
        
        // 构造字段的唯一标识：类名#字段名
        String fieldQualifiedName = fieldRef.getDeclaringType().getQualifiedName() + 
                                   "#" + fieldRef.getSimpleName();
        
        if (nodeIndex.getField(fieldQualifiedName).isPresent()) {
            int lineNumber = fieldRead.getPosition() != null && fieldRead.getPosition().isValidPosition()
                    ? fieldRead.getPosition().getLine()
                    : -1;
            
            ReadsEdge edge = new ReadsEdge(methodSignature, fieldQualifiedName, lineNumber);
            edge.setReadStatement(fieldRead.toString());
            
            edges.add(edge);
        }
    }
    
    private <T> void handleFieldWrite(CtFieldWrite<T> fieldWrite, String methodSignature, 
                                     List<WritesEdge> edges) {
        CtFieldReference<T> fieldRef = fieldWrite.getVariable();
        
        // 检查声明类型是否存在（处理不完整的 AST）
        if (fieldRef.getDeclaringType() == null) {
            return;
        }
        
        // 构造字段的唯一标识：类名#字段名
        String fieldQualifiedName = fieldRef.getDeclaringType().getQualifiedName() + 
                                   "#" + fieldRef.getSimpleName();
        
        if (nodeIndex.getField(fieldQualifiedName).isPresent()) {
            int lineNumber = fieldWrite.getPosition() != null && fieldWrite.getPosition().isValidPosition()
                    ? fieldWrite.getPosition().getLine()
                    : -1;
            
            WritesEdge edge = new WritesEdge(methodSignature, fieldQualifiedName, lineNumber);
            edge.setWriteStatement(fieldWrite.toString());
            
            edges.add(edge);
        }
    }
    
    /**
     * 处理测试方法中的方法调用
     * 只有当被调用方法是非测试方法时，才创建TESTS边
     */
    private <T> void handleTestMethodCall(CtInvocation<T> invocation, String testMethodSignature, 
                                         List<TestsEdge> edges) {
        CtExecutableReference<T> execRef = invocation.getExecutable();
        
        // 检查声明类型是否存在（处理不完整的 AST）
        if (execRef.getDeclaringType() == null) {
            return;
        }
        
        // 生成完整的被调用方法签名：类名#方法签名
        String declaringType = execRef.getDeclaringType().getQualifiedName();
        String testedMethodSignature = declaringType + "#" + execRef.getSignature();
        
        // 检查被调用方法是否在索引中（项目内方法）
        var testedMethodNode = nodeIndex.getMethod(testedMethodSignature);
        if (testedMethodNode.isPresent()) {
            // 检查被调用方法是否是非测试方法
            MethodNode methodNode = testedMethodNode.get();
            String kind = (String) methodNode.getProperty("kind");
            
            // 只有当被测方法不是测试方法时，才创建TESTS边
            if (!"TEST_METHOD".equals(kind)) {
                int lineNumber = invocation.getPosition() != null && invocation.getPosition().isValidPosition()
                        ? invocation.getPosition().getLine()
                        : -1;
                
                TestsEdge edge = new TestsEdge(testMethodSignature, testedMethodSignature, lineNumber);
                edge.setTestStatement(invocation.toString());
                edge.setDirectTest(true);
                
                edges.add(edge);
            }
        }
    }
    
    /**
     * 处理测试方法中的构造函数调用
     * 只有当被调用构造函数是非测试类的时，才创建TESTS边
     */
    private <T> void handleTestConstructorCall(CtConstructorCall<T> constructorCall, 
                                              String testMethodSignature, List<TestsEdge> edges) {
        CtExecutableReference<T> execRef = constructorCall.getExecutable();
        
        // 检查声明类型是否存在（处理不完整的 AST）
        if (execRef.getDeclaringType() == null) {
            return;
        }
        
        // 生成完整的构造函数签名：类名#构造函数签名
        String declaringType = execRef.getDeclaringType().getQualifiedName();
        String testedConstructorSignature = declaringType + "#" + execRef.getSignature();
        
        var testedMethodNode = nodeIndex.getMethod(testedConstructorSignature);
        if (testedMethodNode.isPresent()) {
            // 构造函数本身不是TEST_METHOD，但需要检查所属类是否是测试类
            // 通过检查类是否在测试路径下来判断
            MethodNode methodNode = testedMethodNode.get();
            String absolutePath = (String) methodNode.getProperty("absolutePath");
            
            // 如果不在测试路径下，则创建TESTS边
            if (absolutePath != null && 
                !absolutePath.contains("/test/") && 
                !absolutePath.contains("\\test\\")) {
                int lineNumber = constructorCall.getPosition() != null && constructorCall.getPosition().isValidPosition()
                        ? constructorCall.getPosition().getLine()
                        : -1;
                
                TestsEdge edge = new TestsEdge(testMethodSignature, testedConstructorSignature, lineNumber);
                edge.setTestStatement(constructorCall.toString());
                edge.setDirectTest(true);
                
                edges.add(edge);
            }
        }
    }
}

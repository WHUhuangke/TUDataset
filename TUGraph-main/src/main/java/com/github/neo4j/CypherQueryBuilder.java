package com.github.neo4j;

import com.github.model.Edge;
import com.github.model.GraphElement;
import com.github.model.Node;
import com.github.model.edges.*;
import com.github.model.nodes.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Cypher 查询构建器
 * 负责根据节点和边类型生成 Cypher 查询语句
 */
public class CypherQueryBuilder {
    
    /**
     * 构建创建节点的 Cypher 查询
     */
    public static String buildCreateNodeQuery(Node node) {
        if (node instanceof ProjectNode) {
            return buildProjectNodeQuery((ProjectNode) node);
        } else if (node instanceof PackageNode) {
            return buildPackageNodeQuery((PackageNode) node);
        } else if (node instanceof FileNode) {
            return buildFileNodeQuery((FileNode) node);
        } else if (node instanceof TypeNode) {
            return buildTypeNodeQuery((TypeNode) node);
        } else if (node instanceof MethodNode) {
            return buildMethodNodeQuery((MethodNode) node);
        } else if (node instanceof FieldNode) {
            return buildFieldNodeQuery((FieldNode) node);
        }
        // ParameterNode 已移除 - 参数信息现在作为 UsesParameterEdge 的属性存储
        // AnnotationNode 已移除 - 注解信息现在作为节点属性存储
        throw new IllegalArgumentException("Unknown node type: " + node.getClass().getName());
    }
    
    /**
     * 构建创建边的 Cypher 查询
     */
    public static String buildCreateEdgeQuery(Edge edge) {
        if (edge instanceof DeclaresEdge) {
            return buildDeclaresEdgeQuery((DeclaresEdge) edge);
        } else if (edge instanceof InPackageEdge) {
            return buildInPackageEdgeQuery((InPackageEdge) edge);
        } else if (edge instanceof ExtendsEdge) {
            return buildExtendsEdgeQuery((ExtendsEdge) edge);
        } else if (edge instanceof ImplementsEdge) {
            return buildImplementsEdgeQuery((ImplementsEdge) edge);
        } else if (edge instanceof OverridesEdge) {
            return buildOverridesEdgeQuery((OverridesEdge) edge);
        } else if (edge instanceof CallsEdge) {
            return buildCallsEdgeQuery((CallsEdge) edge);
        } else if (edge instanceof ReadsEdge) {
            return buildReadsEdgeQuery((ReadsEdge) edge);
        } else if (edge instanceof WritesEdge) {
            return buildWritesEdgeQuery((WritesEdge) edge);
        } else if (edge instanceof TestsEdge) {
            return buildTestsEdgeQuery((TestsEdge) edge);
        } else if (edge instanceof UsesParameterEdge) {
            return buildUsesParameterEdgeQuery((UsesParameterEdge) edge);
        } else if (edge instanceof ReturnTypeEdge) {
            return buildReturnTypeEdgeQuery((ReturnTypeEdge) edge);
        } else if (edge instanceof ThrowsEdge) {
            return buildThrowsEdgeQuery((ThrowsEdge) edge);
        } else if (edge instanceof DeclaresTypeEdge) {
            return buildDeclaresTypeEdgeQuery((DeclaresTypeEdge) edge);
        } else if (edge instanceof ContainsFileEdge) {
            return buildContainsFileEdgeQuery((ContainsFileEdge) edge);
        } else if (edge instanceof HasPackageEdge) {
            return buildHasPackageEdgeQuery((HasPackageEdge) edge);
        }
        // AnnotatedByEdge 已移除 - 注解信息现在作为节点属性存储
        throw new IllegalArgumentException("Unknown edge type: " + edge.getClass().getName());
    }
    
    // ==================== 节点查询构建 ====================
    
    private static String buildProjectNodeQuery(ProjectNode node) {
        return "CREATE (n:Project {" +
                "id: $id, " +
                "name: $name, " +
                "version: $version, " +
                "groupId: $groupId, " +
                "artifactId: $artifactId, " +
                "description: $description})";
    }
    
    private static String buildPackageNodeQuery(PackageNode node) {
        return "CREATE (n:Package {" +
                "id: $id, " +
                "qualifiedName: $qualifiedName, " +
                "typeCount: $typeCount, " +
                "isTestPackage: $isTestPackage})";
    }
    
    private static String buildFileNodeQuery(FileNode node) {
        return "CREATE (n:File {" +
                "id: $id, " +
                "path: $path, " +
                "absolutePath: $absolutePath, " +
                "relativePath: $relativePath, " +
                "fileName: $fileName, " +
                "packageName: $packageName, " +
                "imports: $imports, " +
                "content: $content})";
    }
    
    private static String buildTypeNodeQuery(TypeNode node) {
        String kind = node.getKind().name();
        return "CREATE (n:Type:" + kind + " {" +
                "id: $id, " +
                "qualifiedName: $qualifiedName, " +
                "simpleName: $simpleName, " +
                "kind: $kind, " +
                "isAbstract: $isAbstract, " +
                "isFinal: $isFinal, " +
                "isStatic: $isStatic, " +
                "visibility: $visibility, " +
                "superClass: $superClass, " +
                "interfaces: $interfaces, " +
                "absolutePath: $absolutePath, " +
                "relativePath: $relativePath, " +
                "sourceCode: $sourceCode, " +
                "documentation: $documentation, " +
                "comments: $comments, " +
                "semanticSummary: $semanticSummary})";
    }
    
    private static String buildMethodNodeQuery(MethodNode node) {
        String kind = node.getKind().name();
        return "CREATE (n:Method:" + kind + " {" +
                "id: $id, " +
                "signature: $signature, " +
                "name: $name, " +
                "kind: $kind, " +
                "returnType: $returnType, " +
                "parameters: $parameters, " +
                "exceptions: $exceptions, " +
                "isAbstract: $isAbstract, " +
                "isStatic: $isStatic, " +
                "isFinal: $isFinal, " +
                "visibility: $visibility, " +
                "cyclomaticComplexity: $cyclomaticComplexity, " +
                "linesOfCode: $linesOfCode, " +
                "absolutePath: $absolutePath, " +
                "relativePath: $relativePath, " +
                "sourceCode: $sourceCode, " +
                "documentation: $documentation, " +
                "comments: $comments, " +
                "semanticSummary: $semanticSummary})";
    }
    
    private static String buildFieldNodeQuery(FieldNode node) {
        return "CREATE (n:Field {" +
                "id: $id, " +
                "qualifiedName: $qualifiedName, " +
                "name: $name, " +
                "type: $type, " +
                "isStatic: $isStatic, " +
                "isFinal: $isFinal, " +
                "visibility: $visibility, " +
                "initialValue: $initialValue, " +
                "absolutePath: $absolutePath, " +
                "relativePath: $relativePath, " +
                "sourceCode: $sourceCode, " +
                "documentation: $documentation, " +
                "comments: $comments, " +
                "semanticSummary: $semanticSummary})";
    }
    
    // buildParameterNodeQuery 已移除 - ParameterNode 不再使用
    // buildAnnotationNodeQuery 已移除 - AnnotationNode 不再使用
    
    // ==================== 边查询构建 ====================
    
    private static String buildDeclaresEdgeQuery(DeclaresEdge edge) {
        return "MATCH (source {id: $sourceId}) " +
                "MATCH (target {id: $targetId}) " +
                "CREATE (source)-[:DECLARES {memberKind: $memberKind}]->(target)";
    }
    
    private static String buildInPackageEdgeQuery(InPackageEdge edge) {
        return "MATCH (type:Type {qualifiedName: $sourceId}) " +
                "MATCH (pkg:Package {qualifiedName: $targetId}) " +
                "CREATE (type)-[:IN_PACKAGE]->(pkg)";
    }
    
    private static String buildExtendsEdgeQuery(ExtendsEdge edge) {
        return "MATCH (child:Type {qualifiedName: $sourceId}) " +
                "MATCH (parent:Type {qualifiedName: $targetId}) " +
                "CREATE (child)-[:EXTENDS {" +
                "inheritanceDeclaration: $inheritanceDeclaration, " +
                "description: $description}]->(parent)";
    }
    
    private static String buildImplementsEdgeQuery(ImplementsEdge edge) {
        return "MATCH (impl:Type {qualifiedName: $sourceId}) " +
                "MATCH (iface:Type {qualifiedName: $targetId}) " +
                "CREATE (impl)-[:IMPLEMENTS {" +
                "implementationDeclaration: $implementationDeclaration, " +
                "description: $description}]->(iface)";
    }
    
    private static String buildOverridesEdgeQuery(OverridesEdge edge) {
        return "MATCH (override:Method {signature: $sourceId}) " +
                "MATCH (original:Method {signature: $targetId}) " +
                "CREATE (override)-[:OVERRIDES {" +
                "hasAnnotation: $hasAnnotation, " +
                "isInterfaceImpl: $isInterfaceImpl, " +
                "description: $description}]->(original)";
    }
    
    private static String buildCallsEdgeQuery(CallsEdge edge) {
        return "MATCH (caller:Method {signature: $sourceId}) " +
                "MATCH (callee:Method {signature: $targetId}) " +
                "CREATE (caller)-[:CALLS {" +
                "lineNumber: $lineNumber, " +
                "callType: $callType, " +
                "callCount: $callCount, " +
                "contextSnippet: $contextSnippet, " +
                "description: $description}]->(callee)";
    }
    
    private static String buildReadsEdgeQuery(ReadsEdge edge) {
        return "MATCH (method:Method {signature: $sourceId}) " +
                "MATCH (field:Field {id: $targetId}) " +
                "CREATE (method)-[:READS {" +
                "lineNumber: $lineNumber, " +
                "accessType: $accessType, " +
                "contextSnippet: $contextSnippet, " +
                "description: $description}]->(field)";
    }
    
    private static String buildWritesEdgeQuery(WritesEdge edge) {
        return "MATCH (method:Method {signature: $sourceId}) " +
                "MATCH (field:Field {id: $targetId}) " +
                "CREATE (method)-[:WRITES {" +
                "lineNumber: $lineNumber, " +
                "accessType: $accessType, " +
                "contextSnippet: $contextSnippet, " +
                "description: $description}]->(field)";
    }
    
    private static String buildTestsEdgeQuery(TestsEdge edge) {
        return "MATCH (tester:Method {signature: $sourceId}) " +
                "MATCH (tested:Method {signature: $targetId}) " +
                "CREATE (tester)-[:TESTS {" +
                "lineNumber: $lineNumber, " +
                "testType: $testType, " +
                "isDirectTest: $isDirectTest, " +
                "testStatement: $testStatement, " +
                "assertionType: $assertionType, " +
                "testScenario: $testScenario, " +
                "description: $description}]->(tested)";
    }
    
    private static String buildUsesParameterEdgeQuery(UsesParameterEdge edge) {
        return "MATCH (method:Method {signature: $sourceId}) " +
                "MATCH (type:Type {qualifiedName: $targetId}) " +
                "CREATE (method)-[:USES_PARAMETER {" +
                "paramName: $paramName, " +
                "paramIndex: $paramIndex, " +
                "isFinal: $isFinal, " +
                "isVarArgs: $isVarArgs, " +
                "annotations: $annotations, " +
                "semanticRole: $semanticRole" +
                "}]->(type)";
    }
    
    private static String buildReturnTypeEdgeQuery(ReturnTypeEdge edge) {
        return "MATCH (method:Method {signature: $sourceId}) " +
                "MATCH (type:Type {qualifiedName: $targetId}) " +
                "CREATE (method)-[:RETURN_TYPE {genericInfo: $genericInfo}]->(type)";
    }
    
    private static String buildThrowsEdgeQuery(ThrowsEdge edge) {
        return "MATCH (method:Method {signature: $sourceId}) " +
                "MATCH (exception:Type {qualifiedName: $targetId}) " +
                "CREATE (method)-[:THROWS {throwLocations: $throwLocations}]->(exception)";
    }
    
    // buildAnnotatedByEdgeQuery 已移除 - AnnotatedByEdge 不再使用
    
    private static String buildDeclaresTypeEdgeQuery(DeclaresTypeEdge edge) {
        return "MATCH (file:File {path: $sourceId}) " +
                "MATCH (type:Type {qualifiedName: $targetId}) " +
                "CREATE (file)-[:DECLARES_TYPE {" +
                "isPrimaryType: $isPrimaryType, " +
                "lineRange: $lineRange}]->(type)";
    }
    
    private static String buildContainsFileEdgeQuery(ContainsFileEdge edge) {
        return "MATCH (project:Project {id: $sourceId}) " +
                "MATCH (file:File {id: $targetId}) " +
                "CREATE (project)-[:CONTAINS_FILE {" +
                "relativePath: $relativePath, " +
                "isTestFile: $isTestFile}]->(file)";
    }
    
    private static String buildHasPackageEdgeQuery(HasPackageEdge edge) {
        return "MATCH (project:Project {id: $sourceId}) " +
                "MATCH (pkg:Package {qualifiedName: $targetId}) " +
                "CREATE (project)-[:HAS_PACKAGE {" +
                "fileCount: $fileCount, " +
                "typeCount: $typeCount}]->(pkg)";
    }
    
    /**
     * 将节点转换为参数 Map
     */
    public static Map<String, Object> nodeToParameters(Node node) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", node.getId());
        
        if (node instanceof ProjectNode) {
            ProjectNode pn = (ProjectNode) node;
            params.put("name", pn.getName() != null ? pn.getName() : "");
            params.put("version", pn.getVersion() != null ? pn.getVersion() : "");
            Object groupId = pn.getProperty("groupId");
            params.put("groupId", groupId != null ? groupId : "");
            Object artifactId = pn.getProperty("artifactId");
            params.put("artifactId", artifactId != null ? artifactId : "");
            Object description = pn.getProperty("description");
            params.put("description", description != null ? description : "");
            
        } else if (node instanceof PackageNode) {
            PackageNode pn = (PackageNode) node;
            params.put("qualifiedName", pn.getQualifiedName());
            params.put("typeCount", getIntProperty(pn, "typeCount", 0));
            params.put("isTestPackage", getBooleanProperty(pn, "isTestPackage", false));
            
        } else if (node instanceof FileNode) {
            FileNode fn = (FileNode) node;
            params.put("path", fn.getAbsolutePath() != null ? fn.getAbsolutePath() : "");
            params.put("absolutePath", fn.getAbsolutePath() != null ? fn.getAbsolutePath() : "");
            params.put("relativePath", fn.getRelativePath() != null ? fn.getRelativePath() : "");
            params.put("fileName", fn.getName() != null ? fn.getName() : "");
            Object packageName = fn.getProperty("packageName");
            params.put("packageName", packageName != null ? packageName : "");
            @SuppressWarnings("unchecked")
            java.util.List<String> imports = (java.util.List<String>) fn.getProperty("imports");
            params.put("imports", imports != null ? String.join(",", imports) : "");
            params.put("content", fn.getSourceCode() != null ? fn.getSourceCode() : "");
            
        } else if (node instanceof TypeNode) {
            TypeNode tn = (TypeNode) node;
            params.put("qualifiedName", tn.getQualifiedName() != null ? tn.getQualifiedName() : "");
            Object simpleName = tn.getProperty("simpleName");
            params.put("simpleName", simpleName != null ? simpleName : "");
            params.put("kind", tn.getKind().name());
            params.put("isAbstract", getBooleanProperty(tn, "isAbstract", false));
            params.put("isFinal", getBooleanProperty(tn, "isFinal", false));
            params.put("isStatic", getBooleanProperty(tn, "isStatic", false));
            Object visibility = tn.getProperty("visibility");
            params.put("visibility", visibility != null ? visibility : "");
            Object superClass = tn.getProperty("superClass");
            params.put("superClass", superClass != null ? superClass : "");
            @SuppressWarnings("unchecked")
            java.util.List<String> interfaces = (java.util.List<String>) tn.getProperty("interfaces");
            params.put("interfaces", interfaces != null ? String.join(",", interfaces) : "");
            params.put("absolutePath", tn.getAbsolutePath() != null ? tn.getAbsolutePath() : "");
            params.put("relativePath", tn.getRelativePath() != null ? tn.getRelativePath() : "");
            params.put("sourceCode", tn.getSourceCode() != null ? tn.getSourceCode() : "");
            params.put("documentation", tn.getDocumentation() != null ? tn.getDocumentation() : "");
            params.put("comments", tn.getComments() != null ? tn.getComments() : "");
            params.put("semanticSummary", tn.getSemanticSummary() != null ? tn.getSemanticSummary() : "");
            
        } else if (node instanceof MethodNode) {
            MethodNode mn = (MethodNode) node;
            params.put("signature", mn.getSignature() != null ? mn.getSignature() : "");
            Object name = mn.getProperty("name");
            params.put("name", name != null ? name : "");
            params.put("kind", mn.getKind().name());
            Object returnType = mn.getProperty("returnType");
            params.put("returnType", returnType != null ? returnType : "");
            @SuppressWarnings("unchecked")
            java.util.List<String> parameters = (java.util.List<String>) mn.getProperty("parameters");
            params.put("parameters", parameters != null ? String.join(",", parameters) : "");
            @SuppressWarnings("unchecked")
            java.util.List<String> exceptions = (java.util.List<String>) mn.getProperty("exceptions");
            params.put("exceptions", exceptions != null ? String.join(",", exceptions) : "");
            params.put("isAbstract", getBooleanProperty(mn, "isAbstract", false));
            params.put("isStatic", getBooleanProperty(mn, "isStatic", false));
            params.put("isFinal", getBooleanProperty(mn, "isFinal", false));
            Object visibility = mn.getProperty("visibility");
            params.put("visibility", visibility != null ? visibility : "");
            params.put("cyclomaticComplexity", getIntProperty(mn, "cyclomaticComplexity", 0));
            params.put("linesOfCode", getIntProperty(mn, "linesOfCode", 0));
            params.put("absolutePath", mn.getAbsolutePath() != null ? mn.getAbsolutePath() : "");
            params.put("relativePath", mn.getRelativePath() != null ? mn.getRelativePath() : "");
            params.put("sourceCode", mn.getSourceCode() != null ? mn.getSourceCode() : "");
            params.put("documentation", mn.getDocumentation() != null ? mn.getDocumentation() : "");
            params.put("comments", mn.getComments() != null ? mn.getComments() : "");
            params.put("semanticSummary", mn.getSemanticSummary() != null ? mn.getSemanticSummary() : "");
            
        } else if (node instanceof FieldNode) {
            FieldNode fn = (FieldNode) node;
            Object qualifiedName = fn.getProperty("qualifiedName");
            params.put("qualifiedName", qualifiedName != null ? qualifiedName : "");
            params.put("name", fn.getName() != null ? fn.getName() : "");
            Object type = fn.getProperty("type");
            params.put("type", type != null ? type : "");
            params.put("isStatic", getBooleanProperty(fn, "isStatic", false));
            params.put("isFinal", getBooleanProperty(fn, "isFinal", false));
            Object visibility = fn.getProperty("visibility");
            params.put("visibility", visibility != null ? visibility : "");
            Object initialValue = fn.getProperty("initialValue");
            params.put("initialValue", initialValue != null ? initialValue : "");
            params.put("absolutePath", fn.getAbsolutePath() != null ? fn.getAbsolutePath() : "");
            params.put("relativePath", fn.getRelativePath() != null ? fn.getRelativePath() : "");
            params.put("sourceCode", fn.getSourceCode() != null ? fn.getSourceCode() : "");
            params.put("documentation", fn.getDocumentation() != null ? fn.getDocumentation() : "");
            params.put("comments", fn.getComments() != null ? fn.getComments() : "");
            params.put("semanticSummary", fn.getSemanticSummary() != null ? fn.getSemanticSummary() : "");
        }
        // ParameterNode 处理已移除 - 参数信息现在作为 UsesParameterEdge 的属性存储
        // AnnotationNode 处理已移除 - 注解信息现在作为节点属性存储
        
        return params;
    }
    
    /**
     * 将边转换为参数 Map
     */
    public static Map<String, Object> edgeToParameters(Edge edge) {
        Map<String, Object> params = new HashMap<>();
        params.put("sourceId", edge.getSourceId());
        params.put("targetId", edge.getTargetId());
        
        // 通用属性 - 确保始终提供这些参数，即使为空字符串
        params.put("contextSnippet", edge.getContextSnippet() != null ? edge.getContextSnippet() : "");
        params.put("description", edge.getDescription() != null ? edge.getDescription() : "");
        
        // 特定边类型的属性
        if (edge instanceof DeclaresEdge) {
            DeclaresEdge de = (DeclaresEdge) edge;
            Object memberKind = de.getProperty("memberKind");
            params.put("memberKind", memberKind != null ? memberKind : "");
            
        } else if (edge instanceof ExtendsEdge) {
            ExtendsEdge ee = (ExtendsEdge) edge;
            Object inheritanceDeclaration = ee.getProperty("inheritanceDeclaration");
            params.put("inheritanceDeclaration", inheritanceDeclaration != null ? inheritanceDeclaration : "");
            
        } else if (edge instanceof ImplementsEdge) {
            ImplementsEdge ie = (ImplementsEdge) edge;
            Object implementationDeclaration = ie.getProperty("implementationDeclaration");
            params.put("implementationDeclaration", implementationDeclaration != null ? implementationDeclaration : "");
            
        } else if (edge instanceof OverridesEdge) {
            OverridesEdge oe = (OverridesEdge) edge;
            params.put("hasAnnotation", getBooleanProperty(oe, "hasAnnotation", false));
            params.put("isInterfaceImpl", getBooleanProperty(oe, "isInterfaceImpl", false));
            
        } else if (edge instanceof CallsEdge) {
            CallsEdge ce = (CallsEdge) edge;
            params.put("lineNumber", getIntProperty(ce, "lineNumber", 0));
            Object callType = ce.getProperty("callType");
            params.put("callType", callType != null ? callType : "");
            params.put("callCount", getIntProperty(ce, "callCount", 1));
            
            // 聚合属性
            @SuppressWarnings("unchecked")
            java.util.List<Integer> callLocations = (java.util.List<Integer>) ce.getProperty("callLocations");
            if (callLocations != null) {
                params.put("callLocations", callLocations);
                params.put("firstCallLine", getIntProperty(ce, "firstCallLine", 0));
                params.put("lastCallLine", getIntProperty(ce, "lastCallLine", 0));
            }
            
            Boolean isHotspot = (Boolean) ce.getProperty("isHotspot");
            if (isHotspot != null && isHotspot) {
                params.put("isHotspot", true);
            }
            
        } else if (edge instanceof ReadsEdge) {
            ReadsEdge re = (ReadsEdge) edge;
            params.put("lineNumber", getIntProperty(re, "lineNumber", 0));
            Object accessType = re.getProperty("accessType");
            params.put("accessType", accessType != null ? accessType : "");
            params.put("readCount", getIntProperty(re, "readCount", 1));
            
            // 聚合属性
            @SuppressWarnings("unchecked")
            java.util.List<Integer> readLocations = (java.util.List<Integer>) re.getProperty("readLocations");
            if (readLocations != null) {
                params.put("readLocations", readLocations);
            }
            
        } else if (edge instanceof WritesEdge) {
            WritesEdge we = (WritesEdge) edge;
            params.put("lineNumber", getIntProperty(we, "lineNumber", 0));
            Object accessType = we.getProperty("accessType");
            params.put("accessType", accessType != null ? accessType : "");
            params.put("writeCount", getIntProperty(we, "writeCount", 1));
            
            // 聚合属性
            @SuppressWarnings("unchecked")
            java.util.List<Integer> writeLocations = (java.util.List<Integer>) we.getProperty("writeLocations");
            if (writeLocations != null) {
                params.put("writeLocations", writeLocations);
            }
            
        } else if (edge instanceof TestsEdge) {
            TestsEdge te = (TestsEdge) edge;
            params.put("lineNumber", getIntProperty(te, "lineNumber", 0));
            params.put("testType", getStringProperty(te, "testType", "unit"));
            params.put("isDirectTest", getBooleanProperty(te, "isDirectTest", true));
            params.put("testStatement", getStringProperty(te, "testStatement", ""));
            params.put("assertionType", getStringProperty(te, "assertionType", ""));
            params.put("testScenario", getStringProperty(te, "testScenario", ""));
            
        } else if (edge instanceof UsesParameterEdge) {
            UsesParameterEdge upe = (UsesParameterEdge) edge;
            params.put("paramName", getStringProperty(upe, "paramName", ""));
            params.put("paramIndex", getIntProperty(upe, "paramIndex", 0));
            params.put("isFinal", getBooleanProperty(upe, "isFinal", false));
            params.put("isVarArgs", getBooleanProperty(upe, "isVarArgs", false));
            params.put("semanticRole", getStringProperty(upe, "semanticRole", ""));
            
            @SuppressWarnings("unchecked")
            java.util.List<String> annotations = (java.util.List<String>) upe.getProperty("annotations");
            params.put("annotations", annotations != null ? annotations : new java.util.ArrayList<String>());
            
        } else if (edge instanceof ReturnTypeEdge) {
            ReturnTypeEdge rte = (ReturnTypeEdge) edge;
            Object genericInfo = rte.getProperty("genericInfo");
            params.put("genericInfo", genericInfo != null ? genericInfo : "");
            
        } else if (edge instanceof ThrowsEdge) {
            ThrowsEdge te = (ThrowsEdge) edge;
            @SuppressWarnings("unchecked")
            java.util.List<String> throwLocations = (java.util.List<String>) te.getProperty("throwLocations");
            params.put("throwLocations", throwLocations != null ? String.join(",", throwLocations) : "");
            
        } else if (edge instanceof DeclaresTypeEdge) {
            DeclaresTypeEdge dte = (DeclaresTypeEdge) edge;
            params.put("isPrimaryType", getBooleanProperty(dte, "isPrimaryType", false));
            Object lineRange = dte.getProperty("lineRange");
            params.put("lineRange", lineRange != null ? lineRange : "");
            
        } else if (edge instanceof ContainsFileEdge) {
            ContainsFileEdge cfe = (ContainsFileEdge) edge;
            Object relativePath = cfe.getProperty("relativePath");
            params.put("relativePath", relativePath != null ? relativePath : "");
            params.put("isTestFile", getBooleanProperty(cfe, "isTestFile", false));
            
        } else if (edge instanceof HasPackageEdge) {
            HasPackageEdge hpe = (HasPackageEdge) edge;
            params.put("fileCount", getIntProperty(hpe, "fileCount", 0));
            params.put("typeCount", getIntProperty(hpe, "typeCount", 0));
        }
        
        return params;
    }
    
    /**
     * 转义字符串属性（防止注入）
     */
    public static String escapeString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("'", "\\'")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    // ==================== 辅助方法 ====================
    
    private static int getIntProperty(GraphElement element, String key, int defaultValue) {
        Object value = element.getProperty(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return defaultValue;
    }
    
    private static boolean getBooleanProperty(GraphElement element, String key, boolean defaultValue) {
        Object value = element.getProperty(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    private static String getStringProperty(GraphElement element, String key, String defaultValue) {
        Object value = element.getProperty(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }
}

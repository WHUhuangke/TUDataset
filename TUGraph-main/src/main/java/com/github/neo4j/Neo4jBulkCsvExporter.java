package com.github.neo4j;

import com.github.logging.GraphLogger;
import com.github.model.KnowledgeGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Neo4j 批量导入 CSV 导出器 (优化版)
 * 将知识图谱导出为符合 Neo4j bulk import 格式的 CSV 文件
 * 
 * 优化点:
 * 1. 展开所有属性为独立的 CSV 列,而不是序列化为 JSON
 * 2. 移除 --database 参数,使用 Neo4j 默认配置
 */
public class Neo4jBulkCsvExporter {
    
    private static final GraphLogger logger = GraphLogger.getInstance();
    
    private final String outputDir;
    
    public Neo4jBulkCsvExporter(String outputDir) {
        this.outputDir = outputDir;
    }
    
    public Neo4jBulkCsvExporter() {
        this("csv_export");
    }
    
    /**
     * 导出知识图谱为 Neo4j bulk import 格式的 CSV
     */
    public String exportForBulkImport(KnowledgeGraph graph, String projectName) {
        logger.info("========================================");
        logger.info("导出 Neo4j Bulk Import 格式 CSV");
        logger.info("========================================");
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 创建输出目录
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String exportDir = outputDir + "/" + projectName + "_" + timestamp;
            File dir = new File(exportDir);
            
            if (!dir.exists()) {
                dir.mkdirs();
                logger.info("创建导出目录: " + exportDir);
            }
            
            // 导出节点
            String nodesFile = exportDir + "/nodes_bulk.csv";
            int nodeCount = exportNodes(graph, nodesFile);
            logger.info(String.format("✓ 节点导出完成: %d 个节点 -> %s", nodeCount, nodesFile));
            
            // 导出边
            String edgesFile = exportDir + "/edges_bulk.csv";
            int edgeCount = exportEdges(graph, edgesFile);
            logger.info(String.format("✓ 边导出完成: %d 条边 -> %s", edgeCount, edgesFile));
            
            // 生成导入脚本
            String scriptFile = exportDir + "/import.sh";
            generateImportScript(scriptFile, nodesFile, edgesFile);
            logger.info("✓ 导入脚本已生成: " + scriptFile);
            
            // 生成说明文件
            String readmeFile = exportDir + "/README.md";
            generateReadme(readmeFile, projectName, nodeCount, edgeCount);
            logger.info("✓ 说明文件已生成: " + readmeFile);
            
            long endTime = System.currentTimeMillis();
            double duration = (endTime - startTime) / 1000.0;
            
            logger.info("========================================");
            logger.info(String.format("✓ CSV 导出完成! 耗时: %.2f 秒", duration));
            logger.info("导出目录: " + exportDir);
            logger.info("========================================");
            
            return exportDir;
            
        } catch (Exception e) {
            logger.error("导出 CSV 失败: " + e.getMessage(), e);
            throw new RuntimeException("Failed to export CSV for bulk import", e);
        }
    }
    
    /**
     * 导出节点 - 展开所有属性
     */
    private int exportNodes(KnowledgeGraph graph, String filename) throws IOException {
        logger.info("导出节点到: " + filename);
        
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8))) {
            
            // 收集所有可能的属性名
            Set<String> allPropertyKeys = collectAllPropertyKeys(graph.getAllNodes());
            List<String> propertyKeysList = new ArrayList<>(allPropertyKeys);
            Collections.sort(propertyKeysList); // 排序保证一致性
            
            // 写入表头 - Neo4j import 格式
            // 固定列：ID, LABEL, name
            writer.write(":ID,:LABEL,name");
            
            // 演化信息列（如果是演化分析的图谱）
            boolean hasEvolutionInfo = checkIfEvolutionGraph(graph);
            if (hasEvolutionInfo) {
                writer.write(",versionStatus:string,versions:string[],firstVersion:string,lastVersion:string");
            }
            
            // 动态属性列
            for (String key : propertyKeysList) {
                writer.write(",");
                writer.write(formatPropertyHeader(key));
            }
            writer.newLine();
            
            int count = 0;
            for (var node : graph.getAllNodes()) {
                // 基础列
                com.github.model.Node nodeObj = (com.github.model.Node) node;
                String id = escapeValue(nodeObj.getId());

                // 1) 计算 label: 使用多标签策略
                //    - METHOD 节点: METHOD;{kind} (如 METHOD;CONSTRUCTOR)
                //    - TYPE 节点: TYPE;{kind} (如 TYPE;CLASS)
                //    - 其他节点: 仅使用节点类型
                String label = computeNodeLabels(nodeObj);

                // 2) 计算 name: 优先使用 properties 中的 name, 否则回退到 getLabel() 并清洗可能的 "(KIND)" 附加信息
                String rawName = (String) nodeObj.getProperty("name");
                if (rawName == null || rawName.isBlank()) {
                    rawName = nodeObj.getLabel();
                }
                String name = escapeValue(cleanName(rawName));

                writer.write(String.format("%s,%s,%s", id, label, name));
                
                // 演化信息列（如果是演化图谱）
                if (hasEvolutionInfo) {
                    // versionStatus
                    String versionStatus = nodeObj.getVersionStatus() != null ? 
                        nodeObj.getVersionStatus().name() : "";
                    writer.write("," + escapeValue(versionStatus));
                    
                    // versions (数组格式)
                    String versions = nodeObj.getVersions().isEmpty() ? "" : 
                        String.join(";", nodeObj.getVersions());
                    writer.write("," + escapeValue(versions));
                    
                    // firstVersion
                    String firstVersion = nodeObj.getFirstVersion() != null ? 
                        nodeObj.getFirstVersion() : "";
                    writer.write("," + escapeValue(firstVersion));
                    
                    // lastVersion
                    String lastVersion = nodeObj.getLastVersion() != null ? 
                        nodeObj.getLastVersion() : "";
                    writer.write("," + escapeValue(lastVersion));
                }
                
                // 属性列
                Map<String, Object> props = node.getProperties();
                for (String key : propertyKeysList) {
                    writer.write(",");
                    Object value = null;

                    // 优先从属性 Map 中取
                    if (props.containsKey(key)) {
                        value = props.get(key);
                    } else {
                        // 对于 Node 类的一些额外字段, 从 getter 中取值
                        switch (key) {
                            case "sourceCode":
                                value = nodeObj.getSourceCode();
                                break;
                            case "documentation":
                                value = nodeObj.getDocumentation();
                                break;
                            case "comments":
                                value = nodeObj.getComments();
                                break;
                            case "semanticSummary":
                                value = nodeObj.getSemanticSummary();
                                break;
                            case "absolutePath":
                                value = nodeObj.getAbsolutePath();
                                break;
                            case "relativePath":
                                value = nodeObj.getRelativePath();
                                break;
                            default:
                                value = null;
                        }
                    }

                    writer.write(formatPropertyValue(value));
                }
                
                writer.newLine();
                count++;
                
                if (count % 5000 == 0) {
                    logger.debug(String.format("已导出 %d 个节点...", count));
                }
            }
            
            return count;
        }
    }
    
    /**
     * 导出边 - 展开所有属性
     */
    private int exportEdges(KnowledgeGraph graph, String filename) throws IOException {
        logger.info("导出边到: " + filename);
        
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8))) {
            
            // 收集所有可能的属性名
            Set<String> allPropertyKeys = collectAllPropertyKeys(graph.getAllEdges());
            List<String> propertyKeysList = new ArrayList<>(allPropertyKeys);
            Collections.sort(propertyKeysList);
            
            // 写入表头
            writer.write(":START_ID,:END_ID,:TYPE");
            
            for (String key : propertyKeysList) {
                writer.write(",");
                writer.write(formatPropertyHeader(key));
            }
            writer.newLine();
            
            int count = 0;
            for (var edge : graph.getAllEdges()) {
                // 基础列
                String sourceId = escapeValue(edge.getSourceId());
                String targetId = escapeValue(edge.getTargetId());
                String type = edge.getEdgeType();
                
                writer.write(String.format("%s,%s,%s", sourceId, targetId, type));
                
                // 属性列
                Map<String, Object> props = edge.getProperties();
                for (String key : propertyKeysList) {
                    writer.write(",");
                    Object value = props.get(key);
                    writer.write(formatPropertyValue(value));
                }
                
                writer.newLine();
                count++;
                
                if (count % 5000 == 0) {
                    logger.debug(String.format("已导出 %d 条边...", count));
                }
            }
            
            return count;
        }
    }
    
    /**
     * 检查图谱是否包含演化信息
     * 通过检查节点是否有版本状态来判断
     */
    private boolean checkIfEvolutionGraph(KnowledgeGraph graph) {
        for (var node : graph.getAllNodes()) {
            com.github.model.Node nodeObj = (com.github.model.Node) node;
            if (nodeObj.getVersionStatus() != null || !nodeObj.getVersions().isEmpty()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 收集所有元素的属性键
     * 排除已经作为固定列的属性(id, label, name 等)
     */
    private <T> Set<String> collectAllPropertyKeys(Collection<T> elements) {
        Set<String> allKeys = new LinkedHashSet<>();
        Map<String, Boolean> hasMeaningfulValue = new LinkedHashMap<>();
        Set<String> excludedKeys = Set.of("id", "label", "name");

        boolean containsNode = false;

        for (T element : elements) {
            Map<String, Object> props = null;

            if (element instanceof com.github.model.Node) {
                containsNode = true;
                props = ((com.github.model.Node) element).getProperties();
            } else if (element instanceof com.github.model.Edge) {
                props = ((com.github.model.Edge) element).getProperties();
            }

            if (props != null) {
                for (Map.Entry<String, Object> entry : props.entrySet()) {
                    String key = entry.getKey();
                    if (excludedKeys.contains(key)) {
                        continue;
                    }
                    allKeys.add(key);
                    if (hasPropertyValue(entry.getValue())) {
                        hasMeaningfulValue.put(key, true);
                    } else {
                        hasMeaningfulValue.putIfAbsent(key, false);
                    }
                }
            }
        }

        if (containsNode) {
            addNodeExtendedKeys(elements, allKeys, hasMeaningfulValue);
        }

        allKeys.removeIf(key -> !hasMeaningfulValue.getOrDefault(key, false));
        return allKeys;
    }

    private <T> void addNodeExtendedKeys(Collection<T> elements,
                                         Set<String> allKeys,
                                         Map<String, Boolean> hasMeaningfulValue) {
        String[] extendedKeys = {
            "sourceCode",
            "documentation",
            "comments",
            "semanticSummary",
            "absolutePath",
            "relativePath",
            "refactoringTypes"
        };

        for (String key : extendedKeys) {
            boolean found = false;
            for (T element : elements) {
                if (!(element instanceof com.github.model.Node)) {
                    continue;
                }
                Object value = extractNodeExtendedValue((com.github.model.Node) element, key);
                if (hasPropertyValue(value)) {
                    found = true;
                    break;
                }
            }

            if (found) {
                allKeys.add(key);
                hasMeaningfulValue.put(key, true);
            }
        }
    }

    private Object extractNodeExtendedValue(com.github.model.Node node, String key) {
        switch (key) {
            case "sourceCode":
                return node.getSourceCode();
            case "documentation":
                return node.getDocumentation();
            case "comments":
                return node.getComments();
            case "semanticSummary":
                return node.getSemanticSummary();
            case "absolutePath":
                return node.getAbsolutePath();
            case "relativePath":
                return node.getRelativePath();
            case "refactoringTypes":
                return node.getProperty("refactoringTypes");
            default:
                return null;
        }
    }

    private boolean hasPropertyValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String) {
            return !((String) value).isBlank();
        }
        if (value instanceof java.util.Collection) {
            return !((java.util.Collection<?>) value).isEmpty();
        }
        if (value instanceof java.util.Map) {
            return !((java.util.Map<?, ?>) value).isEmpty();
        }
        return true;
    }

    /**
     * 计算节点的标签（支持多标签）
     * Neo4j bulk import 格式: 多个标签用分号分隔, 如 "METHOD;CONSTRUCTOR"
     * 
     * 策略:
     * - METHOD 节点: METHOD;{kind} (kind 可能是 METHOD/CONSTRUCTOR/TEST_METHOD)
     * - TYPE 节点: TYPE;{kind} (kind 可能是 CLASS/INTERFACE/ENUM/ANNOTATION/TEST_CLASS)
     * - 其他节点: 仅使用节点类型本身
     */
    private String computeNodeLabels(com.github.model.Node node) {
        String baseType = node.getNodeType();
        Object kind = node.getProperty("kind");
        
        // 对于有 kind 属性的节点，使用多标签
        if (kind != null && !kind.toString().isBlank()) {
            String kindStr = kind.toString().trim();
            
            // METHOD 节点: 添加大类标签 + 细分标签
            if ("METHOD".equalsIgnoreCase(baseType)) {
                return "METHOD;" + kindStr;
            }
            
            // TYPE 节点: 添加大类标签 + 细分标签
            if ("TYPE".equalsIgnoreCase(baseType)) {
                return "TYPE;" + kindStr;
            }
        }
        
        // 其他节点或没有 kind 属性的节点，仅使用基础类型
        return baseType;
    }
    
    /**
     * 清洗 name 字段, 移除像 "name(kind)" 这样的附加信息, 以及前缀如 "Package:" / "Project:" / "File:"
     */
    private String cleanName(String raw) {
        if (raw == null) return "";
        String s = raw.trim();

        // 如果包含 '(' 并且以 ')' 结束, 认为可能是 getLabel() 生成的 name(kind) 格式, 去掉括号部分
        int paren = s.indexOf('(');
        if (paren > 0) {
            s = s.substring(0, paren).trim();
        }

        // 去除常见前缀（不区分大小写）
        String lower = s.toLowerCase();
        if (lower.startsWith("package:")) {
            s = s.substring("package:".length()).trim();
        } else if (lower.startsWith("project:")) {
            s = s.substring("project:".length()).trim();
        } else if (lower.startsWith("file:")) {
            s = s.substring("file:".length()).trim();
        } else if (s.contains(": ")) {
            // 处理 FieldNode 的 "name: type" 格式，只保留冒号前面的部分
            int colonIndex = s.indexOf(": ");
            if (colonIndex > 0) {
                s = s.substring(0, colonIndex).trim();
            }
        }

        return s;
    }
    
    /**
     * 格式化属性表头,根据属性名推断类型
     */
    private String formatPropertyHeader(String propertyName) {
        // 数组类型
        if (isArrayProperty(propertyName)) {
            return propertyName + ":string[]";
        }
        
        // 推断基本类型
        String type = inferPropertyType(propertyName);
        return propertyName + ":" + type;
    }
    
    /**
     * 判断是否是数组属性
     */
    private boolean isArrayProperty(String propertyName) {
        return propertyName.equals("parameterTypes") ||
               propertyName.equals("parameterNames") ||
               propertyName.equals("annotations") ||
               propertyName.equals("exceptions") ||
               propertyName.equals("calledMethods") ||
               propertyName.equals("accessedFields") ||
               propertyName.equals("localVariables") ||
               propertyName.equals("interfaces") ||
               propertyName.equals("genericTypes") ||
               propertyName.equals("readBy") ||
               propertyName.equals("writtenBy") ||
               propertyName.equals("callLocations") ||
               propertyName.equals("readLocations") ||
               propertyName.equals("writeLocations") ||
               propertyName.equals("callTypes") ||
               propertyName.equals("accessTypes") ||
               propertyName.equals("descriptions") ||
               propertyName.equals("leftLocations") ||
               propertyName.equals("rightLocations") ||
               propertyName.equals("leftElements") ||
               propertyName.equals("rightElements");
    }
    
    /**
     * 推断属性类型
     */
    private String inferPropertyType(String propertyName) {
        // 整数类型
        if (propertyName.equals("occurrences") ||
            propertyName.contains("Count") || 
            propertyName.contains("Number") ||
            propertyName.contains("Line") ||
            propertyName.equals("complexity") ||
            propertyName.equals("cyclomaticComplexity") ||
            propertyName.equals("linesOfCode") ||
            propertyName.equals("fieldCount") ||
            propertyName.equals("methodCount") ||
            propertyName.equals("lineStart") ||
            propertyName.equals("lineEnd")) {
            return "int";
        }
        
        // 布尔类型
        if (propertyName.startsWith("is") || 
            propertyName.startsWith("has")) {
            return "boolean";
        }
        
        if (propertyName.equals("confidence") || propertyName.equals("similarityScore")) {
            return "float";
        }
        
        return "string";
    }
    
    /**
     * 格式化属性值
     */
    private String formatPropertyValue(Object value) {
        if (value == null) {
            // 使用空字符串作为占位，确保属性在 Neo4j 中存在
            return "\"\"";
        }
        
        if (value instanceof List) {
            return formatListValue((List<?>) value);
        }
        
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }

        if (value instanceof String) {
            String encoded = CsvTextSanitizer.encode((String) value);
            if (encoded.isEmpty()) {
                return "\"\"";
            }
            return escapeValue(encoded);
        }
        
        String encoded = CsvTextSanitizer.encode(value.toString());
        return escapeValue(encoded);
    }
    
    /**
     * 格式化列表值为 Neo4j 数组格式
     */
    private String formatListValue(List<?> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        
        // Neo4j 数组格式: 使用分号分隔
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(";");
            }
            Object item = list.get(i);
            if (item != null) {
                String itemStr = CsvTextSanitizer.encode(item.toString())
                    .replace(";", "\\;");
                sb.append(itemStr);
            }
        }
        
        return escapeValue(sb.toString());
    }
    
    /**
     * 转义 CSV 值, 保留换行并在需要时使用引号包裹
     */
    private String escapeValue(String value) {
        if (value == null) {
            return "";
        }
        
        // 标准化换行符，避免同一字段出现多种换行形式
        String cleaned = value
            .replace("\r\n", "\n")  // Windows 换行
            .replace("\r", "\n");   // Mac 换行
        
        // 转义引号
        if (cleaned.contains("\"")) {
            cleaned = cleaned.replace("\"", "\"\"");
        }
        
        // 只要包含分隔符、换行或制表符，就需要用引号包裹
        if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains("\n") || cleaned.contains("\t")) {
            return "\"" + cleaned + "\"";
        }
        
        return cleaned;
    }
    
    /**
     * 将导出时编码过的字符串还原为原始格式。
     * 提供给读取 CSV / Neo4j 数据后需要恢复源码或注释的调用方使用。
     */
    public static String restoreMultilineValue(String value) {
        return CsvTextSanitizer.decode(value);
    }
    
    /**
     * 生成导入脚本 (移除 --database 参数)
     */
    private void generateImportScript(String filename, String nodesFile, String edgesFile) 
            throws IOException {
        
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8))) {
            
            writer.write("#!/bin/bash\n");
            writer.write("\n");
            writer.write("# Neo4j Bulk Import Script\n");
            writer.write("# 自动生成的导入脚本\n");
            writer.write("\n");
            writer.write("# 使用方法:\n");
            writer.write("# 1. 停止 Neo4j 服务: neo4j stop\n");
            writer.write("# 2. 执行此脚本: bash import.sh\n");
            writer.write("# 3. 启动 Neo4j 服务: neo4j start\n");
            writer.write("\n");
            writer.write("# 检查 NEO4J_HOME 环境变量\n");
            writer.write("if [ -z \"$NEO4J_HOME\" ]; then\n");
            writer.write("    echo \"错误: 未设置 NEO4J_HOME 环境变量\"\n");
            writer.write("    echo \"请设置: export NEO4J_HOME=/path/to/neo4j\"\n");
            writer.write("    exit 1\n");
            writer.write("fi\n");
            writer.write("\n");
            writer.write("# 检查 Neo4j 是否正在运行\n");
            writer.write("if $NEO4J_HOME/bin/neo4j status > /dev/null 2>&1; then\n");
            writer.write("    echo \"错误: Neo4j 正在运行,请先停止服务\"\n");
            writer.write("    echo \"执行: neo4j stop\"\n");
            writer.write("    exit 1\n");
            writer.write("fi\n");
            writer.write("\n");
            writer.write("echo \"开始批量导入...\"\n");
            writer.write("echo \"\"\n");
            writer.write("\n");
            writer.write("# 执行导入命令 (不指定 --database,使用默认配置)\n");
            writer.write("$NEO4J_HOME/bin/neo4j-admin database import full \\\n");
            writer.write("  --overwrite-destination=true \\\n");
            writer.write("  --nodes=\"" + new File(nodesFile).getAbsolutePath() + "\" \\\n");
            writer.write("  --relationships=\"" + new File(edgesFile).getAbsolutePath() + "\" \\\n");
            writer.write("  --skip-duplicate-nodes=true \\\n");
            writer.write("  --skip-bad-relationships=true\n");
            writer.write("\n");
            writer.write("if [ $? -eq 0 ]; then\n");
            writer.write("    echo \"\"\n");
            writer.write("    echo \"========================================\"\n");
            writer.write("    echo \"✓ 导入成功!\"\n");
            writer.write("    echo \"========================================\"\n");
            writer.write("    echo \"\"\n");
            writer.write("    echo \"后续步骤:\"\n");
            writer.write("    echo \"1. 启动 Neo4j: neo4j start\"\n");
            writer.write("    echo \"2. 访问浏览器: http://localhost:7474\"\n");
            writer.write("    echo \"3. 验证数据: MATCH (n) RETURN count(n)\"\n");
            writer.write("else\n");
            writer.write("    echo \"\"\n");
            writer.write("    echo \"✗ 导入失败\"\n");
            writer.write("    exit 1\n");
            writer.write("fi\n");
        }
        
        // 设置执行权限
        new File(filename).setExecutable(true);
    }
    
    /**
     * 生成 README 文件
     */
    private void generateReadme(String filename, String projectName, int nodeCount, int edgeCount) 
            throws IOException {
        
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8))) {
            
            writer.write("# Neo4j 批量导入数据包\n\n");
            writer.write("## 项目信息\n\n");
            writer.write("- **项目名称**: " + projectName + "\n");
            writer.write("- **节点数量**: " + String.format("%,d", nodeCount) + "\n");
            writer.write("- **边数量**: " + String.format("%,d", edgeCount) + "\n");
            writer.write("- **导出时间**: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n");
            writer.write("\n");
            
            writer.write("## 优化说明\n\n");
            writer.write("✓ **属性展开**: 所有节点和边的属性已展开为独立的 CSV 列\n");
            writer.write("✓ **类型推断**: 自动推断属性类型 (int, boolean, string, string[])\n");
            writer.write("✓ **默认配置**: 使用 Neo4j 默认数据库配置,无需指定 --database 参数\n");
            writer.write("\n");
            
            writer.write("## 文件说明\n\n");
            writer.write("- `nodes_bulk.csv` - 节点数据文件 (属性已展开)\n");
            writer.write("- `edges_bulk.csv` - 边数据文件 (属性已展开)\n");
            writer.write("- `import.sh` - 自动导入脚本\n");
            writer.write("- `README.md` - 本文件\n");
            writer.write("\n");
            
            writer.write("## 快速开始\n\n");
            writer.write("```bash\n");
            writer.write("# 1. 设置 Neo4j 路径\n");
            writer.write("export NEO4J_HOME=/path/to/neo4j\n");
            writer.write("\n");
            writer.write("# 2. 停止 Neo4j 服务\n");
            writer.write("neo4j stop\n");
            writer.write("\n");
            writer.write("# 3. 执行导入脚本\n");
            writer.write("bash import.sh\n");
            writer.write("\n");
            writer.write("# 4. 启动 Neo4j 服务\n");
            writer.write("neo4j start\n");
            writer.write("```\n\n");
            
            writer.write("## 验证导入\n\n");
            writer.write("访问 Neo4j 浏览器: http://localhost:7474\n\n");
            writer.write("```cypher\n");
            writer.write("// 统计节点数量\n");
            writer.write("MATCH (n) RETURN count(n) as nodeCount\n");
            writer.write("\n");
            writer.write("// 统计边数量\n");
            writer.write("MATCH ()-[r]->() RETURN count(r) as edgeCount\n");
            writer.write("\n");
            writer.write("// 查看节点类型分布\n");
            writer.write("MATCH (n) RETURN labels(n)[0] as type, count(*) as count ORDER BY count DESC\n");
            writer.write("\n");
            writer.write("// 查看某个方法的属性 (属性已展开,可直接访问)\n");
            writer.write("MATCH (m:METHOD) RETURN m.name, m.cyclomaticComplexity, m.linesOfCode LIMIT 10\n");
            writer.write("```\n\n");
            
            writer.write("## 注意事项\n\n");
            writer.write("1. **必须停止 Neo4j**: 批量导入只能在 Neo4j 停止时执行\n");
            writer.write("2. **数据会覆盖**: 使用 `--overwrite-destination=true` 会清空现有数据\n");
            writer.write("3. **属性已展开**: 所有属性都是独立的列,可以直接在 Cypher 中访问\n");
            writer.write("4. **数组格式**: 数组类型使用分号(;)分隔\n");
            writer.write("\n");
        }
    }
}

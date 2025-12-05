package com.github.spoon;

import com.github.logging.GraphLogger;
import com.github.model.KnowledgeGraph;
import com.github.spoon.index.NodeIndex;
import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.SpoonModelBuilder;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.support.compiler.jdt.CompilationUnitFilter;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;

import java.util.regex.Pattern;

/**
 * 项目分析器 - 主入口
 * 作用：
 * 1. 初始化 Spoon MavenLauncher
 * 2. 协调两遍遍历过程
 * 3. 返回构建好的知识图谱
 */
public class ProjectAnalyzer {

    private final String projectPath;
    private Launcher launcher;
    private CtModel model;
    private final GraphLogger logger = GraphLogger.getInstance();

    public ProjectAnalyzer(String projectPath) {
        this.projectPath = projectPath;
    }

    /**
     * 分析项目并构建知识图谱
     */
    public KnowledgeGraph analyze() {
        // 开始分析
        logger.startAnalysis(projectPath);

        try {
            // 1. 初始化 Spoon
            initializeSpoon();

            // 2. 第一遍遍历：提取节点
            NodeIndex nodeIndex = extractNodes();

            // 3. 第二遍遍历：提取边
            KnowledgeGraph knowledgeGraph = extractEdges(nodeIndex);

            // 结束分析，打印摘要
            logger.endAnalysis();

            return knowledgeGraph;

        } catch (Exception e) {
            logger.error("Analysis failed", e);
            throw new RuntimeException("Failed to analyze project", e);
        }
    }

    /**
     * 初始化 Spoon MavenLauncher
     */
    private void initializeSpoon() {
        logger.startPhase("Initializing Spoon");
        logger.info("Creating MavenLauncher for project: " + projectPath);

        // 解析 Maven excludes
        java.util.List<String> excludePatterns = parseMavenExcludes();
        if (!excludePatterns.isEmpty()) {
            logger.info("Found " + excludePatterns.size() + " exclude pattern(s) from pom.xml:");
            for (String pattern : excludePatterns) {
                logger.info("  - " + pattern);
            }
        }

        logger.info("Building Spoon model...");
        try {
            setupLauncher(excludePatterns, false);
            buildModelAndLogSuccess(false);
        } catch (Exception e) {
            System.out.println(e);
            if (isCommentAttachmentFailure(e)) {
                handleCommentAttachmentFailure(excludePatterns, e);
            } else {
                handleGeneralModelBuildFailure(e);
            }
        }

        logger.endPhase();
    }

    private void setupLauncher(java.util.List<String> excludePatterns, boolean enableComments) {
        launcher = new MavenLauncher(
                projectPath,
                MavenLauncher.SOURCE_TYPE.ALL_SOURCE // 包含 src/main/java + src/test/java
        );

        applyExcludeFilters(excludePatterns);
        configureEnvironment(enableComments);

        if (!enableComments) {
            logger.info("Retrying model build with comment parsing disabled");
        }
    }

    private void configureEnvironment(boolean enableComments) {
        Environment environment = launcher.getEnvironment();
        environment.setNoClasspath(false);
        environment.setAutoImports(true);
        environment.setCommentEnabled(enableComments);
        environment.setComplianceLevel(11); // Java 11
        environment.setIgnoreDuplicateDeclarations(true);
    }

    private void buildModelAndLogSuccess(boolean commentsEnabled) {
        launcher.buildModel();
        model = launcher.getModel();

        int typeCount = model.getAllTypes().size();
        if (commentsEnabled) {
            logger.info("✓ Model built successfully: " + typeCount + " types found");
        } else {
            logger.info("✓ Model built successfully (comments disabled): " + typeCount + " types found");
        }
        logger.getStatisticsCollector().setTotalFiles(typeCount);
    }

    private void handleCommentAttachmentFailure(java.util.List<String> excludePatterns, Exception originalException) {
        logger.warn("Comment attachment failed during model build; retrying without comment metadata");
        logger.debug("Comment attachment error: " + originalException.getMessage());

        try {
            setupLauncher(excludePatterns, false);
            buildModelAndLogSuccess(false);
        } catch (Exception retryException) {
            handleGeneralModelBuildFailure(retryException);
        }
    }

    private void handleGeneralModelBuildFailure(Exception exception) {
        String warnMessage = "Model build failed with " + exception.getClass().getSimpleName() + ": "
                + exception.getMessage();
        logger.warn(warnMessage);

        java.io.StringWriter sw = new java.io.StringWriter();
        exception.printStackTrace(new java.io.PrintWriter(sw));
        logger.debug("Model build stack trace:\n" + sw);

        model = launcher.getModel();
        int typeCount = model != null ? model.getAllTypes().size() : 0;

        if (typeCount > 0) {
            logger.info("✓ Recovered Spoon model contains " + typeCount
                    + " types; continuing analysis with available elements");
            logger.getStatisticsCollector().setTotalFiles(typeCount);
        } else {
            throw new RuntimeException("Failed to build Spoon model: no types found", exception);
        }
    }

    private boolean isCommentAttachmentFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("JDTCommentBuilder")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 解析 pom.xml 中的 testExcludes 和 excludes 配置
     */
    private java.util.List<String> parseMavenExcludes() {
        java.util.List<String> patterns = new java.util.ArrayList<>();

        try {
            java.io.File pomFile = new java.io.File(projectPath, "pom.xml");
            if (!pomFile.exists()) {
                return patterns;
            }

            // 使用 DOM 解析 pom.xml
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(pomFile);

            // 查找所有的 testExclude 元素
            org.w3c.dom.NodeList testExcludes = doc.getElementsByTagName("testExclude");
            for (int i = 0; i < testExcludes.getLength(); i++) {
                org.w3c.dom.Node node = testExcludes.item(i);
                String pattern = node.getTextContent().trim();
                if (!pattern.isEmpty()) {
                    patterns.add(pattern);
                }
            }

            // 同样处理 exclude 元素
            org.w3c.dom.NodeList excludes = doc.getElementsByTagName("exclude");
            for (int i = 0; i < excludes.getLength(); i++) {
                org.w3c.dom.Node node = excludes.item(i);
                String pattern = node.getTextContent().trim();
                if (!pattern.isEmpty()
                        && (pattern.contains("**") || pattern.contains("*") || pattern.endsWith(".java"))) {
                    patterns.add(pattern);
                }
            }

        } catch (Exception e) {
            logger.debug("Could not parse pom.xml for excludes: " + e.getMessage());
        }

        return patterns;
    }

    /**
     * 基于 pom.xml 中的 exclude/testExclude 配置为 Spoon 添加编译单元过滤器
     */
    private void applyExcludeFilters(java.util.List<String> excludePatterns) {
        if (excludePatterns == null || excludePatterns.isEmpty()) {
            return;
        }

        SpoonModelBuilder modelBuilder = launcher.getModelBuilder();
        if (!(modelBuilder instanceof JDTBasedSpoonCompiler)) {
            logger.debug("Model builder does not support exclusion filters; skipping pom excludes");
            return;
        }

        java.util.List<Pattern> compiledPatterns = new java.util.ArrayList<>();
        java.util.List<String> originalPatterns = new java.util.ArrayList<>();
        for (String pattern : excludePatterns) {
            compileGlob(pattern).ifPresent(regex -> {
                compiledPatterns.add(regex);
                originalPatterns.add(pattern);
            });
        }

        if (compiledPatterns.isEmpty()) {
            logger.debug("No valid exclude patterns compiled from pom.xml");
            return;
        }

        final String normalizedProjectPath = new java.io.File(projectPath)
                .getAbsolutePath()
                .replace("\\", "/");

        ((JDTBasedSpoonCompiler) modelBuilder).addCompilationUnitFilter(new CompilationUnitFilter() {
            @Override
            public boolean exclude(String path) {
                if (path == null) {
                    return false;
                }

                String normalized = path.replace("\\", "/");
                if (matches(normalized)) {
                    return true;
                }

                if (normalized.startsWith(normalizedProjectPath)) {
                    String relative = normalized.substring(normalizedProjectPath.length());
                    if (relative.startsWith("/")) {
                        relative = relative.substring(1);
                    }
                    return matches(relative);
                }

                return false;
            }

            private boolean matches(String candidate) {
                for (int i = 0; i < compiledPatterns.size(); i++) {
                    if (compiledPatterns.get(i).matcher(candidate).matches()) {
                        logger.debug("Excluding compilation unit: " + candidate +
                                " (matched pattern: " + originalPatterns.get(i) + ")");
                        return true;
                    }
                }
                return false;
            }
        });
    }

    /**
     * 将 Maven/Ant 风格的 glob 模式转换为正则表达式
     */
    private java.util.Optional<Pattern> compileGlob(String glob) {
        if (glob == null || glob.isEmpty()) {
            return java.util.Optional.empty();
        }

        StringBuilder regex = new StringBuilder();
        regex.append(".*"); // 允许前缀任意路径

        char[] chars = glob.replace("\\", "/").toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            switch (c) {
                case '*':
                    if (i + 1 < chars.length && chars[i + 1] == '*') {
                        regex.append(".*");
                        i++;
                    } else {
                        regex.append("[^/]*");
                    }
                    break;
                case '?':
                    regex.append("[^/]");
                    break;
                case '.':
                    regex.append("\\.");
                    break;
                case '/':
                    regex.append("/");
                    break;
                case '{':
                    regex.append("\\{");
                    break;
                case '}':
                    regex.append("\\}");
                    break;
                case '(':
                case ')':
                case '+':
                case '|':
                case '^':
                case '$':
                case '[':
                case ']':
                    regex.append("\\").append(c);
                    break;
                default:
                    regex.append(c);
                    break;
            }
        }

        regex.append("$"); // 匹配到字符串末尾

        try {
            return java.util.Optional.of(Pattern.compile(regex.toString()));
        } catch (Exception e) {
            logger.debug("Invalid glob pattern \"" + glob + "\": " + e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * 第一遍遍历：提取所有节点
     */
    private NodeIndex extractNodes() {
        NodeExtractor nodeExtractor = new NodeExtractor(model, projectPath);
        return nodeExtractor.extract();
    }

    /**
     * 第二遍遍历：提取所有关系
     */
    private KnowledgeGraph extractEdges(NodeIndex nodeIndex) {
        EdgeExtractor edgeExtractor = new EdgeExtractor(nodeIndex, model);
        return edgeExtractor.extract();
    }

    /**
     * 获取 Spoon 模型（用于后续处理）
     */
    public CtModel getModel() {
        return model;
    }

}

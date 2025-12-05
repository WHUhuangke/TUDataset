package com.github.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * 应用程序配置管理类
 * 统一管理所有配置项，支持从配置文件、环境变量或代码直接设置
 */
public class AppConfig {
    
    private static AppConfig instance;
    
    // ========== 分析模式配置 ==========
    private AnalysisMode mode;
    
    // Neo4j 配置
    private String neo4jHome;
    private String neo4jUri;
    private String neo4jUsername;
    private String neo4jPassword;
    private String neo4jDatabase;
    
    // 项目配置
    // 注意：在演化模式下，projectPath 同时作为 Git 仓库路径使用
    private String projectPath;
    private String projectName;
    
    // ========== 演化分析配置 ==========
    private String commit;       // 目标版本 commit hash（自动与其父提交比较）
    private boolean useRefactoringMiner;  // 是否使用 RefactoringMiner
    private int evolutionHistoryWindow;   // 多版本分析时向前回溯的提交数量
    
    // 时间线策略配置
    private TimelineStrategy timelineStrategy;  // 时间线构建策略
    private int refactoringTimelineMaxDepth;    // RefactoringTimeline 最大深度
    private int refactoringTimelineMaxDays;     // RefactoringTimeline 时间窗口（天）
    
    // Spoon 配置
    private boolean spoonAutoImports;
    private int spoonComplianceLevel;
    private boolean spoonPreserveFormatting;
    
    // 导出配置
    private String exportBaseDir;
    
    // 日志配置
    private String logLevel;
    private String logDirectory;
    
    // 性能配置
    private int batchSize;
    private int maxRetries;
    
    /**
     * 私有构造函数，使用默认配置
     */
    private AppConfig() {
        initializeDefaults();
    }
    
    /**
     * 获取单例实例
     */
    public static AppConfig getInstance() {
        if (instance == null) {
            synchronized (AppConfig.class) {
                if (instance == null) {
                    instance = new AppConfig();
                }
            }
        }
        return instance;
    }
    
    /**
     * 初始化默认配置
     */
    private void initializeDefaults() {
        // ========== 分析模式默认配置 ==========
        mode = AnalysisMode.SINGLE_VERSION;  // 默认为单版本模式
        
        // Neo4j 默认配置
        neo4jHome = System.getenv("NEO4J_HOME");
        if (neo4jHome == null || neo4jHome.isEmpty()) {
            neo4jHome = "/usr/local/neo4j";
        }
        neo4jUri = "bolt://localhost:7687";
        neo4jUsername = "neo4j";
        neo4jPassword = "12345678";
        neo4jDatabase = "neo4j";
        
        // 项目默认配置
        projectPath = "";
        projectName = "";
        
        // ========== 演化分析默认配置 ==========
        commit = "";
        useRefactoringMiner = true;  // 默认使用 RefactoringMiner
        evolutionHistoryWindow = 5;
        
        // 时间线策略默认配置
        timelineStrategy = TimelineStrategy.LINEAR;  // 默认使用线性策略
        refactoringTimelineMaxDepth = 50;
        refactoringTimelineMaxDays = 180;
        
        // Spoon 默认配置
        spoonAutoImports = true;
        spoonComplianceLevel = 11;
        spoonPreserveFormatting = false;
        
        // 导出默认配置
        exportBaseDir = "./csv_export";
        
        // 日志默认配置
        logLevel = "INFO";
        logDirectory = "./logs";
        
        // 性能默认配置
        batchSize = 2000;
        maxRetries = 3;
    }
    
    /**
     * 从配置文件加载配置
     */
    public void loadFromFile(String configPath) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configPath)) {
            props.load(fis);
        }
        
        // ========== 分析模式配置 ==========
        if (props.containsKey("analysis.mode")) {
            String modeStr = props.getProperty("analysis.mode");
            mode = AnalysisMode.fromString(modeStr);
        }
        
        // Neo4j 配置
        if (props.containsKey("neo4j.home")) {
            neo4jHome = props.getProperty("neo4j.home");
        }
        if (props.containsKey("neo4j.uri")) {
            neo4jUri = props.getProperty("neo4j.uri");
        }
        if (props.containsKey("neo4j.username")) {
            neo4jUsername = props.getProperty("neo4j.username");
        }
        if (props.containsKey("neo4j.password")) {
            neo4jPassword = props.getProperty("neo4j.password");
        }
        if (props.containsKey("neo4j.database")) {
            neo4jDatabase = props.getProperty("neo4j.database");
        }
        
        // 项目配置
        if (props.containsKey("project.path")) {
            projectPath = props.getProperty("project.path");
        }
        if (props.containsKey("project.name")) {
            projectName = props.getProperty("project.name");
        }
        
        // ========== 演化分析配置 ==========
        // 兼容旧配置：如果设置了 evolution.repoPath，将其作为 project.path
        if (props.containsKey("evolution.repoPath")) {
            String legacyRepoPath = props.getProperty("evolution.repoPath");
            if (!legacyRepoPath.isEmpty()) {
                System.err.println("警告：evolution.repoPath 已废弃，请改用 project.path");
                System.err.println("      将 evolution.repoPath 的值设置为 project.path: " + legacyRepoPath);
                projectPath = legacyRepoPath;
            }
        }
        if (props.containsKey("evolution.commit")) {
            commit = props.getProperty("evolution.commit");
        }
        if (props.containsKey("evolution.useRefactoringMiner")) {
            useRefactoringMiner = Boolean.parseBoolean(
                props.getProperty("evolution.useRefactoringMiner")
            );
        }
        if (props.containsKey("evolution.historyWindow")) {
            evolutionHistoryWindow = Integer.parseInt(props.getProperty("evolution.historyWindow"));
        }
        
        // 时间线策略配置
        if (props.containsKey("evolution.timeline.strategy")) {
            String strategyStr = props.getProperty("evolution.timeline.strategy");
            try {
                timelineStrategy = TimelineStrategy.valueOf(strategyStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("警告：无效的时间线策略 '" + strategyStr + "'，使用默认策略 LINEAR");
                timelineStrategy = TimelineStrategy.LINEAR;
            }
        }
        if (props.containsKey("evolution.refactoringTimeline.maxDepth")) {
            refactoringTimelineMaxDepth = Integer.parseInt(
                props.getProperty("evolution.refactoringTimeline.maxDepth"));
        }
        if (props.containsKey("evolution.refactoringTimeline.maxDays")) {
            refactoringTimelineMaxDays = Integer.parseInt(
                props.getProperty("evolution.refactoringTimeline.maxDays"));
        }
        
        // Spoon 配置
        if (props.containsKey("spoon.autoImports")) {
            spoonAutoImports = Boolean.parseBoolean(props.getProperty("spoon.autoImports"));
        }
        if (props.containsKey("spoon.complianceLevel")) {
            spoonComplianceLevel = Integer.parseInt(props.getProperty("spoon.complianceLevel"));
        }
        if (props.containsKey("spoon.preserveFormatting")) {
            spoonPreserveFormatting = Boolean.parseBoolean(props.getProperty("spoon.preserveFormatting"));
        }
        
        // 导出配置
        if (props.containsKey("export.baseDir")) {
            exportBaseDir = props.getProperty("export.baseDir");
        }
        
        // 日志配置
        if (props.containsKey("log.level")) {
            logLevel = props.getProperty("log.level");
        }
        if (props.containsKey("log.directory")) {
            logDirectory = props.getProperty("log.directory");
        }
        
        // 性能配置
        if (props.containsKey("performance.batchSize")) {
            batchSize = Integer.parseInt(props.getProperty("performance.batchSize"));
        }
        if (props.containsKey("performance.maxRetries")) {
            maxRetries = Integer.parseInt(props.getProperty("performance.maxRetries"));
        }
    }
    
    /**
     * 从命令行参数加载配置
     */
    public void loadFromArgs(String[] args) {
        if (args.length > 0 && !args[0].isEmpty()) {
            projectPath = args[0];
        }
        if (args.length > 1 && !args[1].isEmpty()) {
            projectName = args[1];
        }
        if (args.length > 2 && !args[2].isEmpty()) {
            neo4jHome = args[2];
        }
        
        // 如果没有指定项目名，从路径自动提取
        if (projectName.isEmpty() && !projectPath.isEmpty()) {
            File projectDir = new File(projectPath);
            projectName = projectDir.getName();
        }
    }
    
    /**
     * 验证配置
     */
    public boolean validate() {
        if (mode == AnalysisMode.SINGLE_VERSION) {
            return validateSingleVersionConfig();
        } else {
            return validateEvolutionConfig();
        }
    }
    
    /**
     * 验证单版本模式配置
     */
    private boolean validateSingleVersionConfig() {
        if (projectPath == null || projectPath.isEmpty()) {
            System.err.println("错误：未设置项目路径");
            return false;
        }
        
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            System.err.println("错误：项目路径不存在或不是目录: " + projectPath);
            return false;
        }
        
        if (neo4jHome == null || neo4jHome.isEmpty()) {
            System.err.println("错误：未设置 NEO4J_HOME");
            return false;
        }
        
        File neo4jDir = new File(neo4jHome);
        if (!neo4jDir.exists() || !neo4jDir.isDirectory()) {
            System.err.println("错误：Neo4j 路径不存在或不是目录: " + neo4jHome);
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证演化模式配置
     */
    private boolean validateEvolutionConfig() {
        // 在演化模式下，project.path 同时作为 Git 仓库路径
        if (projectPath == null || projectPath.isEmpty()) {
            System.err.println("错误：演化模式未设置项目路径 (project.path)");
            System.err.println("      在演化模式下，project.path 应指向 Git 仓库根目录");
            return false;
        }
        
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            System.err.println("错误：项目路径不存在: " + projectPath);
            return false;
        }
        
        // 验证是否是 Git 仓库
        File gitDir = new File(projectPath, ".git");
        if (!gitDir.exists()) {
            System.err.println("错误：演化模式要求项目路径必须是 Git 仓库: " + projectPath);
            System.err.println("      找不到 .git 目录，请确保 project.path 指向 Git 仓库根目录");
            return false;
        }
        
        if (commit == null || commit.isEmpty()) {
            System.err.println("错误：演化模式未设置 commit (evolution.commit)");
            System.err.println("      示例: evolution.commit=abc123def456");
            return false;
        }

        if (mode == AnalysisMode.MULTI_EVOLUTION && evolutionHistoryWindow < 1) {
            System.err.println("错误：evolution.historyWindow 必须至少为 1");
            return false;
        }
        
        if (neo4jHome == null || neo4jHome.isEmpty()) {
            System.err.println("错误：未设置 NEO4J_HOME");
            return false;
        }
        
        File neo4jDir = new File(neo4jHome);
        if (!neo4jDir.exists() || !neo4jDir.isDirectory()) {
            System.err.println("错误：Neo4j 路径不存在或不是目录: " + neo4jHome);
            return false;
        }
        
        // 如果没有设置项目名，从项目路径自动提取
        if (projectName == null || projectName.isEmpty()) {
            projectName = projectDir.getName();
        }
        
        return true;
    }
    
    // ==================== Getters and Setters ====================
    
    public String getNeo4jHome() {
        return neo4jHome;
    }
    
    public void setNeo4jHome(String neo4jHome) {
        this.neo4jHome = neo4jHome;
    }
    
    public String getNeo4jUri() {
        return neo4jUri;
    }
    
    public void setNeo4jUri(String neo4jUri) {
        this.neo4jUri = neo4jUri;
    }
    
    public String getNeo4jUsername() {
        return neo4jUsername;
    }
    
    public void setNeo4jUsername(String neo4jUsername) {
        this.neo4jUsername = neo4jUsername;
    }
    
    public String getNeo4jPassword() {
        return neo4jPassword;
    }
    
    public void setNeo4jPassword(String neo4jPassword) {
        this.neo4jPassword = neo4jPassword;
    }
    
    public String getNeo4jDatabase() {
        return neo4jDatabase;
    }
    
    public void setNeo4jDatabase(String neo4jDatabase) {
        this.neo4jDatabase = neo4jDatabase;
    }
    
    public String getProjectPath() {
        return projectPath;
    }
    
    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }
    
    public String getProjectName() {
        return projectName;
    }
    
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
    
    public boolean isSpoonAutoImports() {
        return spoonAutoImports;
    }
    
    public void setSpoonAutoImports(boolean spoonAutoImports) {
        this.spoonAutoImports = spoonAutoImports;
    }
    
    public int getSpoonComplianceLevel() {
        return spoonComplianceLevel;
    }
    
    public void setSpoonComplianceLevel(int spoonComplianceLevel) {
        this.spoonComplianceLevel = spoonComplianceLevel;
    }
    
    public boolean isSpoonPreserveFormatting() {
        return spoonPreserveFormatting;
    }
    
    public void setSpoonPreserveFormatting(boolean spoonPreserveFormatting) {
        this.spoonPreserveFormatting = spoonPreserveFormatting;
    }
    
    public String getExportBaseDir() {
        return exportBaseDir;
    }
    
    public void setExportBaseDir(String exportBaseDir) {
        this.exportBaseDir = exportBaseDir;
    }
    
    public String getLogLevel() {
        return logLevel;
    }
    
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
    
    public String getLogDirectory() {
        return logDirectory;
    }
    
    public void setLogDirectory(String logDirectory) {
        this.logDirectory = logDirectory;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    // ========== 分析模式配置 Getters/Setters ==========
    
    public AnalysisMode getMode() {
        return mode;
    }
    
    public void setMode(AnalysisMode mode) {
        this.mode = mode;
    }
    
    // ========== 演化分析配置 Getters/Setters ==========
    
    /**
     * 获取仓库路径（演化模式下等同于项目路径）
     * @deprecated 使用 getProjectPath() 代替
     */
    @Deprecated
    public String getRepoPath() {
        return projectPath;
    }
    
    /**
     * 设置仓库路径（演化模式下等同于项目路径）
     * @deprecated 使用 setProjectPath() 代替
     */
    @Deprecated
    public void setRepoPath(String repoPath) {
        this.projectPath = repoPath;
    }
    
    /**
     * 获取演化分析的目标 commit（自动与父提交比较）
     */
    public String getCommit() {
        return commit;
    }
    
    public void setCommit(String commit) {
        this.commit = commit;
    }
    
    public boolean isUseRefactoringMiner() {
        return useRefactoringMiner;
    }
    
    public void setUseRefactoringMiner(boolean useRefactoringMiner) {
        this.useRefactoringMiner = useRefactoringMiner;
    }

    public int getEvolutionHistoryWindow() {
        return evolutionHistoryWindow;
    }

    public void setEvolutionHistoryWindow(int evolutionHistoryWindow) {
        this.evolutionHistoryWindow = evolutionHistoryWindow;
    }
    
    public TimelineStrategy getTimelineStrategy() {
        return timelineStrategy;
    }
    
    public void setTimelineStrategy(TimelineStrategy timelineStrategy) {
        this.timelineStrategy = timelineStrategy;
    }
    
    public int getRefactoringTimelineMaxDepth() {
        return refactoringTimelineMaxDepth;
    }
    
    public void setRefactoringTimelineMaxDepth(int refactoringTimelineMaxDepth) {
        this.refactoringTimelineMaxDepth = refactoringTimelineMaxDepth;
    }
    
    public int getRefactoringTimelineMaxDays() {
        return refactoringTimelineMaxDays;
    }
    
    public void setRefactoringTimelineMaxDays(int refactoringTimelineMaxDays) {
        this.refactoringTimelineMaxDays = refactoringTimelineMaxDays;
    }
}

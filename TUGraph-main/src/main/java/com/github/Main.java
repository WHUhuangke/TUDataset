package com.github;

import com.github.config.AnalysisMode;
import com.github.config.AppConfig;
import com.github.logging.GraphLogger;
import com.github.logging.LogLevel;
import com.github.pipeline.EvolutionPipeline;
import com.github.pipeline.MultiVersionEvolutionPipeline;
import com.github.pipeline.Pipeline;
import com.github.pipeline.SingleVersionPipeline;

import java.io.File;

/**
 * 主程序入口
 *
 * 支持三种分析模式:
 * 1. 单版本模式 (SINGLE_VERSION): 构建单个版本的知识图谱
 * 2. 演化模式 (EVOLUTION): 分析目标提交与其父提交之间的演化
 * 3. 多版本演化模式 (MULTI_EVOLUTION): 分析目标提交及其最近若干历史提交
 */
public class Main {

    private static final GraphLogger logger = GraphLogger.getInstance();

    public static void main(String[] args) {
        AppConfig config = AppConfig.getInstance();

        File configFile = new File("config.properties");
        if (configFile.exists()) {
            try {
                config.loadFromFile(configFile.getAbsolutePath());
                logger.info("已从配置文件加载配置: " + configFile.getAbsolutePath());
            } catch (Exception e) {
                logger.warn("加载配置文件失败，使用默认配置: " + e.getMessage());
            }
        }

        config.loadFromArgs(args);
        logger.setLogLevel(LogLevel.valueOf(config.getLogLevel()));

        if (!config.validate()) {
            printUsage();
            System.exit(1);
        }

        try {
            Pipeline pipeline = createPipeline(config.getMode());

            logger.info("========================================");
            logger.info("TUGraph 知识图谱构建系统");
            logger.info("模式: " + pipeline.getName());
            logger.info("========================================");
            logger.info("");

            pipeline.execute(config);

        } catch (Exception e) {
            logger.error("执行失败: " + e.getMessage(), e);
            System.exit(1);
        } finally {
            logger.close();
        }
    }

    private static Pipeline createPipeline(AnalysisMode mode) {
        switch (mode) {
            case SINGLE_VERSION:
                return new SingleVersionPipeline();
            case EVOLUTION:
                return new EvolutionPipeline();
            case MULTI_EVOLUTION:
                return new MultiVersionEvolutionPipeline();
            default:
                throw new IllegalArgumentException("Unknown analysis mode: " + mode);
        }
    }

    private static void printUsage() {
        System.err.println("========================================");
        System.err.println("TUGraph - Java 项目知识图谱构建系统");
        System.err.println("========================================");
        System.err.println();
        System.err.println("使用方法:");
        System.err.println();
        System.err.println("1. 单版本模式 (默认):");
        System.err.println("   analysis.mode=SINGLE_VERSION");
        System.err.println("   project.path=<项目根目录>");
        System.err.println();
        System.err.println("2. 演化模式:");
        System.err.println("   analysis.mode=EVOLUTION");
        System.err.println("   project.path=<Git 仓库根目录>");
        System.err.println("   evolution.commit=<目标提交>");
        System.err.println();
        System.err.println("3. 多版本演化模式:");
        System.err.println("   analysis.mode=MULTI_EVOLUTION");
        System.err.println("   project.path=<Git 仓库根目录>");
        System.err.println("   evolution.commit=<目标提交>");
        System.err.println("   evolution.historyWindow=<回溯提交数量, 默认 5>");
        System.err.println();
        System.err.println("配置文件示例请参阅 config.properties.example");
        System.err.println("详细文档请参阅 README.md");
        System.err.println("========================================");
    }
}

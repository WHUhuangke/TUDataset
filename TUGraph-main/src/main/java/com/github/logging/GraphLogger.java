package com.github.logging;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 图谱构建日志器（单例模式）
 * 负责统一管理日志输出、进度跟踪和统计收集
 */
public class GraphLogger {
    
    // 单例实例
    private static GraphLogger instance;
    
    // 日志级别
    private LogLevel logLevel = LogLevel.INFO;
    
    // 日志文件
    private File logFile;
    private BufferedWriter fileWriter;
    
    // 异步写入队列
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>(1000);
    private Thread logWriterThread;
    private volatile boolean running = false;
    
    // 进度跟踪器
    private final ProgressTracker progressTracker = new ProgressTracker();
    
    // 统计收集器
    private final StatisticsCollector statisticsCollector = new StatisticsCollector();
    
    // 是否记录到文件
    private boolean logToFile = true;
    
    // 是否输出到控制台
    private boolean logToConsole = true;
    
    /**
     * 私有构造函数
     */
    private GraphLogger() {
        initializeLogFile();
        startAsyncWriter();
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized GraphLogger getInstance() {
        if (instance == null) {
            instance = new GraphLogger();
        }
        return instance;
    }
    
    /**
     * 初始化日志文件
     */
    private void initializeLogFile() {
        try {
            // 创建日志目录
            File logDir = new File("./logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // 使用固定文件名，每次运行追加到同一个文件
            logFile = new File(logDir, "application.log");
            
            fileWriter = new BufferedWriter(new FileWriter(logFile, true));
            
        } catch (IOException e) {
            System.err.println("Failed to initialize log file: " + e.getMessage());
            logToFile = false;
        }
    }
    
    /**
     * 启动异步写入线程
     */
    private void startAsyncWriter() {
        running = true;
        logWriterThread = new Thread(() -> {
            while (running || !logQueue.isEmpty()) {
                try {
                    String message = logQueue.poll(200, TimeUnit.MILLISECONDS);
                    if (message == null) {
                        continue;
                    }
                    if (fileWriter != null && logToFile) {
                        fileWriter.write(message);
                        fileWriter.newLine();
                        fileWriter.flush();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    System.err.println("Failed to write log: " + e.getMessage());
                }
            }
        }, "LogWriter");
        logWriterThread.setDaemon(true);
        logWriterThread.start();
    }
    
    /**
     * 记录 DEBUG 级别日志
     */
    public void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }
    
    /**
     * 记录 INFO 级别日志
     */
    public void info(String message) {
        log(LogLevel.INFO, message, null);
    }
    
    /**
     * 记录 WARN 级别日志
     */
    public void warn(String message) {
        log(LogLevel.WARN, message, null);
    }
    
    /**
     * 记录 ERROR 级别日志
     */
    public void error(String message) {
        log(LogLevel.ERROR, message, null);
    }
    
    /**
     * 记录 ERROR 级别日志（带异常）
     */
    public void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, message, throwable);
    }
    
    /**
     * 记录日志的核心方法
     */
    private void log(LogLevel level, String message, Throwable throwable) {
        if (!level.shouldLog(logLevel)) {
            return;
        }
        
        // 获取调用者信息
        String className = getCallerClassName();
        
        // 输出到控制台（简洁版）
        if (logToConsole) {
            String consoleMsg = LogFormatter.formatConsoleLog(level, message);
            if (level == LogLevel.ERROR) {
                System.err.println(consoleMsg);
                if (throwable != null) {
                    throwable.printStackTrace(System.err);
                }
            } else {
                System.out.println(consoleMsg);
            }
        }
        
        // 写入文件（详细版）
        if (logToFile) {
            String fileMsg = LogFormatter.formatFileLog(level, className, message);
            logQueue.offer(fileMsg);
            
            if (throwable != null) {
                String exceptionMsg = LogFormatter.formatException(throwable);
                logQueue.offer(exceptionMsg);
            }
        }
    }
    
    /**
     * 获取调用者类名
     */
    private String getCallerClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // 跳过 getStackTrace, getCallerClassName, log, 和具体的日志方法
        for (int i = 4; i < stackTrace.length; i++) {
            String className = stackTrace[i].getClassName();
            if (!className.equals(this.getClass().getName())) {
                return className.substring(className.lastIndexOf('.') + 1);
            }
        }
        return "Unknown";
    }
    
    /**
     * 开始新阶段
     */
    public void startPhase(String phaseName) {
        info("========================================");
        info("Phase: " + phaseName);
        info("========================================");
    }
    
    /**
     * 开始新阶段（带进度跟踪）
     */
    public void startPhase(String phaseName, int totalItems) {
        info("========================================");
        info("Phase: " + phaseName);
        info("========================================");
        progressTracker.startPhase(phaseName, totalItems);
    }
    
    /**
     * 结束当前阶段
     */
    public void endPhase() {
        ProgressTracker.Phase phase = progressTracker.endPhase();
        if (phase != null) {
            long duration = System.currentTimeMillis() - phase.startTime;
            statisticsCollector.recordPhaseTiming(phase.name, duration);
            info("Phase completed: " + phase.name + " - " + LogFormatter.formatDuration(duration));
        }
        info("");
    }
    
    /**
     * 记录节点创建
     */
    public void logNodeCreation(String nodeType, String nodeName) {
        debug("Created " + nodeType + ": " + nodeName);
        statisticsCollector.recordNodeCreation(nodeType);
        progressTracker.increment();
    }
    
    /**
     * 记录边创建
     */
    public void logEdgeCreation(String edgeType, String sourceNode, String targetNode) {
        debug("Created " + edgeType + ": " + sourceNode + " -> " + targetNode);
        statisticsCollector.recordEdgeCreation(edgeType);
        progressTracker.increment();
    }
    
    /**
     * 记录文件处理
     */
    public void logFileProcessed(String filePath, long lines) {
        debug("Processed file: " + filePath + " (" + lines + " lines)");
        statisticsCollector.recordProcessedFile();
        statisticsCollector.recordLines(lines);
    }
    
    /**
     * 打印分隔线
     */
    public void printSeparator() {
        String separator = LogFormatter.formatSeparator('=', 50);
        if (logToConsole) {
            System.out.println(separator);
        }
        if (logToFile) {
            logQueue.offer(separator);
        }
    }
    
    /**
     * 打印标题
     */
    public void printTitle(String title) {
        printSeparator();
        String formattedTitle = LogFormatter.formatTitle(title, 50);
        if (logToConsole) {
            System.out.println(formattedTitle);
        }
        if (logToFile) {
            logQueue.offer(formattedTitle);
        }
        printSeparator();
    }
    
    /**
     * 开始分析（初始化统计）
     */
    public void startAnalysis(String projectPath) {
        statisticsCollector.recordStart();
        printTitle("Starting Project Analysis");
        info("Project Path: " + projectPath);
        info("Log File: " + logFile.getAbsolutePath());
        printSeparator();
        info("");
    }
    
    /**
     * 结束分析（打印摘要）
     */
    public void endAnalysis() {
        info("");
        
        // 控制台输出简洁版
        if (logToConsole) {
            System.out.println(statisticsCollector.generateConsoleSummary());
        }
        
        // 文件输出详细版
        if (logToFile) {
            String detailedSummary = statisticsCollector.generateSummary();
            for (String line : detailedSummary.split("\n")) {
                logQueue.offer(line);
            }
        }
        
        info("Log file saved to: " + logFile.getAbsolutePath());
    }
    
    /**
     * 获取进度跟踪器
     */
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }
    
    /**
     * 获取统计收集器
     */
    public StatisticsCollector getStatisticsCollector() {
        return statisticsCollector;
    }
    
    /**
     * 设置日志级别
     */
    public void setLogLevel(LogLevel level) {
        this.logLevel = level;
    }
    
    /**
     * 设置是否记录到文件
     */
    public void setLogToFile(boolean logToFile) {
        this.logToFile = logToFile;
    }
    
    /**
     * 设置是否输出到控制台
     */
    public void setLogToConsole(boolean logToConsole) {
        this.logToConsole = logToConsole;
    }
    
    /**
     * 获取日志文件路径
     */
    public String getLogFilePath() {
        return logFile != null ? logFile.getAbsolutePath() : null;
    }
    
    /**
     * 关闭日志器
     */
    public void close() {
        running = false;
        
        if (logWriterThread != null) {
            try {
                logWriterThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (fileWriter != null) {
            try {
                fileWriter.close();
            } catch (IOException e) {
                System.err.println("Failed to close log file: " + e.getMessage());
            }
        }
    }
}

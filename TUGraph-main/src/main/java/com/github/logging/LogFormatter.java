package com.github.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日志格式化器
 * 负责格式化日志消息，区分控制台和文件格式
 */
public class LogFormatter {
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * 格式化文件日志（详细版）
     * 格式: 2024-01-15 10:30:45.123 [INFO] [ClassName] Message
     */
    public static String formatFileLog(LogLevel level, String className, String message) {
        return String.format("%s [%s] [%s] %s",
                LocalDateTime.now().format(TIME_FORMATTER),
                level.getName(),
                className,
                message
        );
    }
    
    /**
     * 格式化控制台日志（简洁版）
     * 格式: [INFO] Message
     */
    public static String formatConsoleLog(LogLevel level, String message) {
        String prefix = getConsolePrefix(level);
        return String.format("%s %s", prefix, message);
    }
    
    /**
     * 获取控制台前缀（带颜色）
     */
    private static String getConsolePrefix(LogLevel level) {
        switch (level) {
            case DEBUG:
                return "[DEBUG]";
            case INFO:
                return "[INFO] ";
            case WARN:
                return "[WARN] ";
            case ERROR:
                return "[ERROR]";
            default:
                return "[INFO] ";
        }
    }
    
    /**
     * 格式化异常堆栈
     */
    public static String formatException(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getName())
          .append(": ")
          .append(throwable.getMessage())
          .append("\n");
        
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        
        if (throwable.getCause() != null) {
            sb.append("Caused by: ").append(formatException(throwable.getCause()));
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化进度条
     * 格式: [████████████        ] 60% (600/1000)
     */
    public static String formatProgressBar(int current, int total, int barLength) {
        if (total == 0) {
            return "[" + " ".repeat(barLength) + "] 0% (0/0)";
        }
        
        // 确保 current 不超过 total
        int safeCurrent = Math.min(current, total);
        
        int percentage = (int) ((safeCurrent * 100.0) / total);
        int filled = (int) ((safeCurrent * barLength) / total);
        
        // 确保 filled 不超过 barLength
        filled = Math.min(filled, barLength);
        
        String bar = "█".repeat(filled) + " ".repeat(barLength - filled);
        
        return String.format("[%s] %d%% (%d/%d)", bar, percentage, current, total);
    }
    
    /**
     * 格式化耗时
     * 将毫秒转换为易读格式: 1h 23m 45s 或 12.3s 或 234ms
     */
    public static String formatDuration(long milliseconds) {
        if (milliseconds < 1000) {
            return milliseconds + "ms";
        }
        
        double seconds = milliseconds / 1000.0;
        if (seconds < 60) {
            return String.format("%.1fs", seconds);
        }
        
        long minutes = (long) (seconds / 60);
        seconds = seconds % 60;
        
        if (minutes < 60) {
            return String.format("%dm %.0fs", minutes, seconds);
        }
        
        long hours = minutes / 60;
        minutes = minutes % 60;
        
        return String.format("%dh %dm %.0fs", hours, minutes, seconds);
    }
    
    /**
     * 格式化文件大小
     * 将字节数转换为易读格式: KB, MB, GB
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1fKB", kb);
        }
        
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1fMB", mb);
        }
        
        double gb = mb / 1024.0;
        return String.format("%.2fGB", gb);
    }
    
    /**
     * 格式化分隔线
     */
    public static String formatSeparator(char character, int length) {
        return String.valueOf(character).repeat(length);
    }
    
    /**
     * 格式化标题（居中）
     */
    public static String formatTitle(String title, int totalWidth) {
        int padding = (totalWidth - title.length()) / 2;
        String leftPad = " ".repeat(Math.max(0, padding));
        return leftPad + title;
    }
}

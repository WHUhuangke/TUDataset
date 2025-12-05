package com.github.logging;

/**
 * 日志级别枚举
 */
public enum LogLevel {
    DEBUG("DEBUG", 0),
    INFO("INFO", 1),
    WARN("WARN", 2),
    ERROR("ERROR", 3);
    
    private final String name;
    private final int priority;
    
    LogLevel(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }
    
    public String getName() {
        return name;
    }
    
    public int getPriority() {
        return priority;
    }
    
    /**
     * 判断当前级别是否应该被记录
     */
    public boolean shouldLog(LogLevel configLevel) {
        return this.priority >= configLevel.priority;
    }
}

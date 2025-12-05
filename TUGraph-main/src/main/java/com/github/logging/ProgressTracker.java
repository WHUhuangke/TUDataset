package com.github.logging;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 进度跟踪器
 * 负责跟踪各阶段进度并显示进度条
 */
public class ProgressTracker {
    
    /**
     * 阶段信息
     */
    public static class Phase {
        public String name;
        public int current;
        public int total;
        public long startTime;
        
        Phase(String name, int total) {
            this.name = name;
            this.current = 0;
            this.total = total;
            this.startTime = System.currentTimeMillis();
        }
    }
    
    // 阶段栈（支持嵌套阶段）
    private final Deque<Phase> phaseStack = new ArrayDeque<>();
    
    // 是否启用进度条
    private boolean enableProgressBar = true;
    
    // 进度条长度
    private static final int PROGRESS_BAR_LENGTH = 20;
    
    // 上次更新时间（避免刷新过快）
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 100;
    
    /**
     * 开始新阶段
     */
    public void startPhase(String phaseName, int total) {
        Phase phase = new Phase(phaseName, total);
        phaseStack.push(phase);
    }
    
    /**
     * 结束当前阶段
     */
    public Phase endPhase() {
        if (phaseStack.isEmpty()) {
            return null;
        }
        
        Phase phase = phaseStack.pop();
        
        // 打印完成信息
        if (enableProgressBar) {
            printProgress(phase, true);
        }
        
        return phase;
    }
    
    /**
     * 更新当前阶段进度
     */
    public void updateProgress(int current) {
        if (phaseStack.isEmpty()) {
            return;
        }
        
        Phase phase = phaseStack.peek();
        phase.current = current;
        
        // 限制更新频率
        long now = System.currentTimeMillis();
        if (enableProgressBar && (now - lastUpdateTime > UPDATE_INTERVAL_MS)) {
            printProgress(phase, false);
            lastUpdateTime = now;
        }
    }
    
    /**
     * 增量更新进度
     */
    public void increment() {
        if (phaseStack.isEmpty()) {
            return;
        }
        
        Phase phase = phaseStack.peek();
        phase.current++;
        
        // 限制更新频率
        long now = System.currentTimeMillis();
        if (enableProgressBar && (now - lastUpdateTime > UPDATE_INTERVAL_MS)) {
            printProgress(phase, false);
            lastUpdateTime = now;
        }
    }
    
    /**
     * 打印进度信息
     */
    private void printProgress(Phase phase, boolean isComplete) {
        String progressBar = LogFormatter.formatProgressBar(
                phase.current, phase.total, PROGRESS_BAR_LENGTH);
        
        if (isComplete) {
            long duration = System.currentTimeMillis() - phase.startTime;
            String durationStr = LogFormatter.formatDuration(duration);
            
            System.out.println("\r  Progress: " + progressBar + " - " + durationStr);
        } else {
            // 使用 \r 覆盖当前行
            System.out.print("\r  Progress: " + progressBar);
        }
    }
    
    /**
     * 获取当前阶段名称
     */
    public String getCurrentPhaseName() {
        if (phaseStack.isEmpty()) {
            return null;
        }
        return phaseStack.peek().name;
    }
    
    /**
     * 获取当前阶段进度
     */
    public int getCurrentProgress() {
        if (phaseStack.isEmpty()) {
            return 0;
        }
        return phaseStack.peek().current;
    }
    
    /**
     * 获取当前阶段总数
     */
    public int getCurrentTotal() {
        if (phaseStack.isEmpty()) {
            return 0;
        }
        return phaseStack.peek().total;
    }
    
    /**
     * 获取当前阶段耗时
     */
    public long getCurrentDuration() {
        if (phaseStack.isEmpty()) {
            return 0;
        }
        return System.currentTimeMillis() - phaseStack.peek().startTime;
    }
    
    /**
     * 获取当前阶段百分比
     */
    public int getCurrentPercentage() {
        if (phaseStack.isEmpty()) {
            return 0;
        }
        Phase phase = phaseStack.peek();
        if (phase.total == 0) {
            return 0;
        }
        return (phase.current * 100) / phase.total;
    }
    
    /**
     * 设置是否启用进度条
     */
    public void setEnableProgressBar(boolean enable) {
        this.enableProgressBar = enable;
    }
    
    /**
     * 重置所有状态
     */
    public void reset() {
        phaseStack.clear();
        lastUpdateTime = 0;
    }
}

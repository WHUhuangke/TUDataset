package com.github.pipeline;

import com.github.config.AppConfig;

/**
 * 分析流程接口
 * 定义知识图谱构建流程的统一接口
 * 
 * 所有的分析流程（单版本、演化等）都需要实现此接口
 */
public interface Pipeline {
    
    /**
     * 执行分析流程
     * 
     * @param config 应用配置对象，包含所有必要的配置信息
     * @throws Exception 当执行过程中发生错误时抛出异常
     */
    void execute(AppConfig config) throws Exception;
    
    /**
     * 获取流程的显示名称
     * 用于日志输出和用户提示
     * 
     * @return 流程名称，如 "Single Version Analysis"
     */
    String getName();
    
    /**
     * 获取流程的描述信息
     * 
     * @return 流程的详细描述
     */
    default String getDescription() {
        return "Knowledge graph construction pipeline";
    }
}

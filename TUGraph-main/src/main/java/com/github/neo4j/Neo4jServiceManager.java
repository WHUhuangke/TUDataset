package com.github.neo4j;

import com.github.logging.GraphLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Neo4j 服务管理器
 * 负责启动和停止 Neo4j 服务
 */
public class Neo4jServiceManager {
    
    private final String neo4jHome;
    private final GraphLogger logger = GraphLogger.getInstance();
    
    public Neo4jServiceManager(String neo4jHome) {
        this.neo4jHome = neo4jHome;
    }
    
    /**
     * 停止 Neo4j 服务
     */
    public boolean stop() {
        try {
            logger.info("正在停止 Neo4j 服务...");
            String neo4jBin = neo4jHome + "/bin/neo4j";
            
            ProcessBuilder pb = new ProcessBuilder(neo4jBin, "stop");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 读取输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("neo4j stop: " + line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("✓ Neo4j 服务已停止");
                // 等待服务完全停止
                Thread.sleep(2000);
                return true;
            } else {
                logger.warn("neo4j stop 返回非零退出码: " + exitCode);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("停止 Neo4j 失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 启动 Neo4j 服务
     */
    public boolean start() {
        try {
            logger.info("正在启动 Neo4j 服务...");
            String neo4jBin = neo4jHome + "/bin/neo4j";
            
            ProcessBuilder pb = new ProcessBuilder(neo4jBin, "start");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 读取输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("neo4j start: " + line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                // 等待服务完全启动
                Thread.sleep(5000);
                logger.info("✓ Neo4j 服务已启动");
                return true;
            } else {
                logger.error("neo4j start 返回非零退出码: " + exitCode);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("启动 Neo4j 失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 重启 Neo4j 服务
     */
    public boolean restart() {
        logger.info("正在重启 Neo4j 服务...");
        if (!stop()) {
            logger.warn("停止服务失败，继续尝试启动...");
        }
        return start();
    }
    
    /**
     * 检查 Neo4j 服务状态
     */
    public boolean isRunning() {
        try {
            String neo4jBin = neo4jHome + "/bin/neo4j";
            
            ProcessBuilder pb = new ProcessBuilder(neo4jBin, "status");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            logger.debug("检查 Neo4j 状态失败: " + e.getMessage());
            return false;
        }
    }
}

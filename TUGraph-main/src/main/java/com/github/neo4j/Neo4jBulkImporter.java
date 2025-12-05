package com.github.neo4j;

import com.github.logging.GraphLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Neo4j 批量导入器
 * 使用 neo4j-admin import 命令进行离线批量导入
 */
public class Neo4jBulkImporter {
    
    private static final GraphLogger logger = GraphLogger.getInstance();
    
    private final String neo4jHome;
    private final String databaseName;
    private final boolean overwriteDatabase;
    
    /**
     * 构造函数
     * @param neo4jHome Neo4j 安装目录 (例如: /usr/local/neo4j)
     * @param databaseName 数据库名称 (默认: neo4j)
     * @param overwriteDatabase 是否覆盖已存在的数据库
     */
    public Neo4jBulkImporter(String neo4jHome, String databaseName, boolean overwriteDatabase) {
        this.neo4jHome = neo4jHome;
        this.databaseName = databaseName;
        this.overwriteDatabase = overwriteDatabase;
    }
    
    /**
     * 使用默认配置
     */
    public Neo4jBulkImporter(String neo4jHome) {
        this(neo4jHome, "neo4j", true);
    }
    
    /**
     * 执行批量导入
     * @param nodesFile 节点CSV文件路径
     * @param edgesFile 边CSV文件路径
     * @return 导入是否成功
     */
    public boolean bulkImport(String nodesFile, String edgesFile) {
        logger.info("========================================");
        logger.info("开始 Neo4j 批量离线导入");
        logger.info("========================================");
        logger.info("节点文件: " + nodesFile);
        logger.info("边文件: " + edgesFile);
        logger.info("数据库名称: " + databaseName);
        logger.info("Neo4j Home: " + neo4jHome);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 验证文件存在
            if (!validateFiles(nodesFile, edgesFile)) {
                return false;
            }
            
            // 2. 验证 Neo4j 安装
            if (!validateNeo4jInstallation()) {
                return false;
            }
            
            // 3. 检查数据库状态
            if (!checkDatabaseStatus()) {
                return false;
            }
            
            // 4. 构建并执行导入命令
            boolean success = executeImportCommand(nodesFile, edgesFile);
            
            long endTime = System.currentTimeMillis();
            double duration = (endTime - startTime) / 1000.0;
            
            if (success) {
                logger.info("========================================");
                logger.info("✓ 批量导入成功完成!");
                logger.info(String.format("总耗时: %.2f 秒", duration));
                logger.info("========================================");
                logger.info("\n后续步骤:");
                logger.info("1. 启动 Neo4j: neo4j start");
                logger.info("2. 访问浏览器: http://localhost:7474");
                logger.info("3. 验证数据: MATCH (n) RETURN count(n)");
            } else {
                logger.error("✗ 批量导入失败");
                logger.error(String.format("耗时: %.2f 秒", duration));
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("批量导入过程中发生异常: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 验证文件是否存在
     */
    private boolean validateFiles(String nodesFile, String edgesFile) {
        File nodes = new File(nodesFile);
        File edges = new File(edgesFile);
        
        if (!nodes.exists() || !nodes.isFile()) {
            logger.error("节点文件不存在: " + nodesFile);
            return false;
        }
        
        if (!edges.exists() || !edges.isFile()) {
            logger.error("边文件不存在: " + edgesFile);
            return false;
        }
        
        logger.info("✓ CSV 文件验证通过");
        return true;
    }
    
    /**
     * 验证 Neo4j 安装
     */
    private boolean validateNeo4jInstallation() {
        File neo4jDir = new File(neo4jHome);
        if (!neo4jDir.exists() || !neo4jDir.isDirectory()) {
            logger.error("Neo4j 安装目录不存在: " + neo4jHome);
            logger.error("请设置正确的 NEO4J_HOME 环境变量");
            return false;
        }
        
        // 检查 neo4j-admin 命令
        File neo4jAdminBin = new File(neo4jHome, "bin/neo4j-admin");
        File neo4jAdminCmd = new File(neo4jHome, "bin/neo4j-admin.bat");
        
        if (!neo4jAdminBin.exists() && !neo4jAdminCmd.exists()) {
            logger.error("找不到 neo4j-admin 命令: " + neo4jAdminBin.getAbsolutePath());
            return false;
        }
        
        logger.info("✓ Neo4j 安装验证通过");
        return true;
    }
    
    /**
     * 检查数据库状态
     */
    private boolean checkDatabaseStatus() {
        logger.info("检查 Neo4j 服务状态...");
        
        try {
            String neo4jCommand = neo4jHome + "/bin/neo4j";
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                neo4jCommand = neo4jHome + "\\bin\\neo4j.bat";
            }
            
            ProcessBuilder pb = new ProcessBuilder(neo4jCommand, "status");
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String line;
            boolean isRunning = false;
            
            while ((line = reader.readLine()) != null) {
                logger.debug("Neo4j 状态: " + line);
                if (line.toLowerCase().contains("running") || 
                    line.toLowerCase().contains("active")) {
                    isRunning = true;
                }
            }
            
            process.waitFor(10, TimeUnit.SECONDS);
            
            if (isRunning) {
                logger.warn("⚠ Neo4j 服务正在运行!");
                logger.warn("批量导入需要停止 Neo4j 服务");
                logger.warn("请执行以下命令:");
                logger.warn("  1. 停止服务: neo4j stop");
                logger.warn("  2. 等待服务完全停止");
                logger.warn("  3. 重新运行导入程序");
                return false;
            } else {
                logger.info("✓ Neo4j 服务未运行,可以开始导入");
                return true;
            }
            
        } catch (Exception e) {
            logger.warn("无法检测 Neo4j 状态: " + e.getMessage());
            logger.warn("假设服务未运行,继续导入...");
            return true;
        }
    }
    
    /**
     * 执行导入命令
     */
    private boolean executeImportCommand(String nodesFile, String edgesFile) {
        logger.info("构建导入命令...");
        
        try {
            // 构建命令
            List<String> command = new ArrayList<>();
            
            // neo4j-admin 命令路径
            String neo4jAdminCmd = neo4jHome + "/bin/neo4j-admin";
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                neo4jAdminCmd = neo4jHome + "\\bin\\neo4j-admin.bat";
            }
            
            command.add(neo4jAdminCmd);
            command.add("database");
            command.add("import");
            command.add("full");
            
            // 移除 --database 参数,使用默认配置
            // Neo4j 会使用默认数据库配置,显式指定反而可能导致错误
            
            // 覆盖选项
            if (overwriteDatabase) {
                command.add("--overwrite-destination=true");
            }
            
            // 节点文件
            command.add("--nodes=" + new File(nodesFile).getAbsolutePath());
            
            // 边文件
            command.add("--relationships=" + new File(edgesFile).getAbsolutePath());
            
            // 其他优化选项
            command.add("--skip-duplicate-nodes=true");
            command.add("--skip-bad-relationships=true");
//            command.add("--verbose");  // 启用详细输出以便调试
            
            // 打印命令
            logger.info("执行命令:");
            logger.info(String.join(" ", command));
            logger.info("");
            
            // 执行命令
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // 实时输出日志
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                // Neo4j 导入工具会输出进度信息
                if (line.contains("IMPORT DONE") || 
                    line.contains("Imported:") ||
                    line.contains("Nodes:") ||
                    line.contains("Relationships:") ||
                    line.contains("Properties:") ||
                    line.contains("Peak memory usage:")) {
                    logger.info("  " + line);
                } else if (line.toLowerCase().contains("error")) {
                    logger.error("  " + line);
                } else if (line.toLowerCase().contains("warn")) {
                    logger.warn("  " + line);
                } else {
                    logger.debug("  " + line);
                }
            }
            
            // 等待进程完成
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("✓ 导入命令执行成功");
                return true;
            } else {
                logger.error("✗ 导入命令执行失败,退出码: " + exitCode);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("执行导入命令时发生异常: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取 Neo4j 版本信息
     */
    public String getNeo4jVersion() {
        try {
            String neo4jCmd = neo4jHome + "/bin/neo4j";
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                neo4jCmd = neo4jHome + "\\bin\\neo4j.bat";
            }
            
            ProcessBuilder pb = new ProcessBuilder(neo4jCmd, "version");
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String version = reader.readLine();
            
            process.waitFor(5, TimeUnit.SECONDS);
            
            return version != null ? version : "Unknown";
            
        } catch (Exception e) {
            return "Unknown: " + e.getMessage();
        }
    }
}

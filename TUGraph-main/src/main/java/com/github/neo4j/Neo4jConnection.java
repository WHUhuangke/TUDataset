package com.github.neo4j;

import com.github.logging.GraphLogger;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.Neo4jException;

import java.util.concurrent.TimeUnit;

/**
 * Neo4j 连接管理器（单例模式）
 * 负责管理 Neo4j Driver 实例和连接状态
 */
public class Neo4jConnection {
    
    private static Neo4jConnection instance;
    private final GraphLogger logger = GraphLogger.getInstance();
    
    private Driver driver;
    private Neo4jConfig config;
    private boolean connected = false;
    
    /**
     * 私有构造函数
     */
    private Neo4jConnection() {
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized Neo4jConnection getInstance() {
        if (instance == null) {
            instance = new Neo4jConnection();
        }
        return instance;
    }
    
    /**
     * 初始化连接
     */
    public void initialize(Neo4jConfig config) {
        if (this.driver != null) {
            logger.warn("Driver already initialized, closing existing connection");
            close();
        }
        
        this.config = config;
        
        if (!config.validate()) {
            throw new IllegalArgumentException("Invalid Neo4j configuration");
        }
        
        logger.info("Initializing Neo4j connection...");
        logger.debug("Config: " + config.toString());
        
        try {
            // 创建 Driver
            this.driver = GraphDatabase.driver(
                    config.getUri(),
                    AuthTokens.basic(config.getUsername(), config.getPassword()),
                    Config.builder()
                            .withMaxConnectionPoolSize(config.getMaxConnectionPoolSize())
                            .withConnectionTimeout(config.getConnectionTimeoutMs(), TimeUnit.MILLISECONDS)
                            .withLogging(Logging.none())  // 使用我们自己的日志系统
                            .build()
            );
            
            this.connected = true;
            logger.info("✓ Neo4j Driver initialized");
            
        } catch (Exception e) {
            this.connected = false;
            logger.error("Failed to initialize Neo4j Driver", e);
            throw new RuntimeException("Failed to initialize Neo4j connection", e);
        }
    }
    
    /**
     * 测试连接
     */
    public ConnectionStatus testConnection() {
        logger.info("Testing Neo4j connection...");
        
        if (driver == null) {
            return ConnectionStatus.failure("Driver not initialized");
        }
        
        long startTime = System.currentTimeMillis();
        
        try (Session session = driver.session(SessionConfig.forDatabase(config.getDatabase()))) {
            // 测试简单查询
            Result result = session.run("RETURN 1 AS num");
            if (!result.hasNext() || result.single().get("num").asInt() != 1) {
                return ConnectionStatus.failure("Connection test query failed");
            }
            
            // 获取 Neo4j 版本信息
            Result versionResult = session.run(
                    "CALL dbms.components() YIELD name, versions, edition " +
                    "WHERE name = 'Neo4j Kernel' " +
                    "RETURN versions[0] AS version, edition"
            );
            
            String version = "Unknown";
            String edition = "Unknown";
            
            if (versionResult.hasNext()) {
                org.neo4j.driver.Record record = versionResult.single();
                version = record.get("version").asString();
                edition = record.get("edition").asString();
            }
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            logger.info("✓ Connected to Neo4j " + version + " (" + edition + ")");
            logger.info("✓ Response time: " + responseTime + "ms");
            
            this.connected = true;
            return ConnectionStatus.success(version, edition, responseTime);
            
        } catch (Neo4jException e) {
            logger.error("Neo4j connection test failed", e);
            this.connected = false;
            return ConnectionStatus.failure("Neo4j error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Connection test failed", e);
            this.connected = false;
            return ConnectionStatus.failure("Error: " + e.getMessage());
        }
    }
    
    /**
     * 创建会话
     */
    public Session createSession() {
        if (driver == null) {
            throw new IllegalStateException("Driver not initialized");
        }
        return driver.session(SessionConfig.forDatabase(config.getDatabase()));
    }
    
    /**
     * 获取 Driver
     */
    public Driver getDriver() {
        if (driver == null) {
            throw new IllegalStateException("Driver not initialized");
        }
        return driver;
    }
    
    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected && driver != null;
    }
    
    /**
     * 获取配置
     */
    public Neo4jConfig getConfig() {
        return config;
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (driver != null) {
            logger.info("Closing Neo4j connection...");
            try {
                driver.close();
                this.connected = false;
                logger.info("✓ Neo4j connection closed");
            } catch (Exception e) {
                logger.error("Error closing Neo4j connection", e);
            }
        }
    }
    
    /**
     * 连接状态类
     */
    public static class ConnectionStatus {
        private final boolean success;
        private final String version;
        private final String edition;
        private final long responseTimeMs;
        private final String errorMessage;
        
        private ConnectionStatus(boolean success, String version, String edition, 
                                 long responseTimeMs, String errorMessage) {
            this.success = success;
            this.version = version;
            this.edition = edition;
            this.responseTimeMs = responseTimeMs;
            this.errorMessage = errorMessage;
        }
        
        public static ConnectionStatus success(String version, String edition, long responseTimeMs) {
            return new ConnectionStatus(true, version, edition, responseTimeMs, null);
        }
        
        public static ConnectionStatus failure(String errorMessage) {
            return new ConnectionStatus(false, null, null, 0, errorMessage);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getVersion() {
            return version;
        }
        
        public String getEdition() {
            return edition;
        }
        
        public long getResponseTimeMs() {
            return responseTimeMs;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        @Override
        public String toString() {
            if (success) {
                return "Connected to Neo4j " + version + " (" + edition + "), " +
                       "response time: " + responseTimeMs + "ms";
            } else {
                return "Connection failed: " + errorMessage;
            }
        }
    }
}

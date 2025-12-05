package com.github.neo4j;

/**
 * Neo4j 配置类
 * 管理 Neo4j 数据库连接配置
 */
public class Neo4jConfig {
    
    // 连接配置
    private String uri;
    private String username;
    private String password;
    private String database;
    
    // 性能配置
    private int maxConnectionPoolSize;
    private long connectionTimeoutMs;
    private int batchSize;
    
    // 重试配置
    private int maxRetries;
    private long retryDelayMs;
    
    /**
     * 使用默认配置
     */
    public Neo4jConfig() {
        this.uri = "bolt://localhost:7687";
        this.username = "neo4j";
        this.password = "12345678";
        this.database = "neo4j";
        this.maxConnectionPoolSize = 50;
        this.connectionTimeoutMs = 30000;  // 30秒
        this.batchSize = 2000;
        this.maxRetries = 3;
        this.retryDelayMs = 1000;  // 1秒
    }
    
    /**
     * 自定义配置
     */
    public Neo4jConfig(String uri, String username, String password) {
        this();
        this.uri = uri;
        this.username = username;
        this.password = password;
    }
    
    /**
     * 验证配置
     */
    public boolean validate() {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        if (username == null || username.isEmpty()) {
            return false;
        }
        if (password == null || password.isEmpty()) {
            return false;
        }
        if (batchSize <= 0) {
            return false;
        }
        return true;
    }
    
    // Getters and Setters
    
    public String getUri() {
        return uri;
    }
    
    public void setUri(String uri) {
        this.uri = uri;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getDatabase() {
        return database;
    }
    
    public void setDatabase(String database) {
        this.database = database;
    }
    
    public int getMaxConnectionPoolSize() {
        return maxConnectionPoolSize;
    }
    
    public void setMaxConnectionPoolSize(int maxConnectionPoolSize) {
        this.maxConnectionPoolSize = maxConnectionPoolSize;
    }
    
    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }
    
    public void setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
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
    
    public long getRetryDelayMs() {
        return retryDelayMs;
    }
    
    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }
    
    @Override
    public String toString() {
        return "Neo4jConfig{" +
                "uri='" + uri + '\'' +
                ", username='" + username + '\'' +
                ", database='" + database + '\'' +
                ", maxConnectionPoolSize=" + maxConnectionPoolSize +
                ", connectionTimeoutMs=" + connectionTimeoutMs +
                ", batchSize=" + batchSize +
                ", maxRetries=" + maxRetries +
                ", retryDelayMs=" + retryDelayMs +
                '}';
    }
}

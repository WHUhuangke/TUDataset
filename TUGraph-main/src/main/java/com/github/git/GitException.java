package com.github.git;

/**
 * Git 操作异常
 * 
 * <p>封装所有 Git 操作相关的异常，提供清晰的错误信息。
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class GitException extends Exception {
    
    /**
     * 构造函数
     * 
     * @param message 错误信息
     */
    public GitException(String message) {
        super(message);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误信息
     * @param cause 原始异常
     */
    public GitException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 构造函数
     * 
     * @param cause 原始异常
     */
    public GitException(Throwable cause) {
        super(cause);
    }
}

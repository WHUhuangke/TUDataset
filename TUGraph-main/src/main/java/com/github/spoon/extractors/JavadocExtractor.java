package com.github.spoon.extractors;

import spoon.reflect.declaration.CtElement;
import spoon.reflect.code.CtComment;

/**
 * Javadoc 提取器 - 提取文档注释
 * 作用：
 * 1. 提取 Javadoc 注释
 * 2. 解析 @param, @return, @throws 等标签
 * 3. 提取注释摘要
 */
public class JavadocExtractor {
    
    /**
     * 提取完整的 Javadoc
     */
    public static String extractJavadoc(CtElement element) {
        if (element == null) {
            return "";
        }
        
        return element.getComments().stream()
                .filter(comment -> comment.getCommentType() == CtComment.CommentType.JAVADOC)
                .map(CtComment::getContent)
                .findFirst()
                .orElse("");
    }
    
    /**
     * 提取 Javadoc 的摘要（第一句话）
     */
    public static String extractJavadocSummary(CtElement element) {
        String javadoc = extractJavadoc(element);
        if (javadoc.isEmpty()) {
            return "";
        }
        
        // 移除星号和多余空格
        javadoc = javadoc.replaceAll("(?m)^\\s*\\*\\s?", "").trim();
        
        // 提取第一句话（到第一个句号、问号或感叹号）
        int endIndex = javadoc.length();
        for (int i = 0; i < javadoc.length(); i++) {
            char c = javadoc.charAt(i);
            if (c == '.' || c == '?' || c == '!') {
                // 确保不是小数点或缩写
                if (i + 1 < javadoc.length() && 
                    (Character.isWhitespace(javadoc.charAt(i + 1)) || javadoc.charAt(i + 1) == '\n')) {
                    endIndex = i + 1;
                    break;
                }
            } else if (c == '@') {
                // 遇到标签，停止
                endIndex = i;
                break;
            }
        }
        
        return javadoc.substring(0, Math.min(endIndex, javadoc.length())).trim();
    }
    
    /**
     * 提取所有注释（包括行注释、块注释和Javadoc）
     * 这个方法更宽松，能捕获所有类型的注释
     */
    public static String extractAllComments(CtElement element) {
        if (element == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (CtComment comment : element.getComments()) {
            String content = comment.getContent();
            if (content != null && !content.trim().isEmpty()) {
                // 标注注释类型
                String type = comment.getCommentType().name();
                sb.append("/* [").append(type).append("] */\n");
                sb.append(content.trim()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }
    
    /**
     * 智能提取注释：优先Javadoc，如果没有则返回所有注释
     * 这是推荐的注释提取方法
     */
    public static String extractCommentsIntelligent(CtElement element) {
        if (element == null) {
            return "";
        }
        
        // 先尝试提取Javadoc
        String javadoc = extractJavadoc(element);
        if (!javadoc.isEmpty()) {
            return javadoc;
        }
        
        // 如果没有Javadoc，提取所有注释
        return extractAllComments(element);
    }
}

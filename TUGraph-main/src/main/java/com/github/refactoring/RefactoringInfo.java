package com.github.refactoring;

import java.util.ArrayList;
import java.util.List;

/**
 * 重构信息数据类
 * 
 * <p>封装 RefactoringMiner 检测到的重构操作信息。
 * 包括重构类型、描述、代码位置等信息。
 * 
 * @author TUGraph Team
 * @since 2.0.0
 */
public class RefactoringInfo {
    
    /**
     * 重构类型（来自 RefactoringMiner）
     * 例如：RENAME_METHOD, EXTRACT_METHOD, MOVE_CLASS 等
     */
    private String type;
    
    /**
     * 重构描述（人类可读）
     * 例如："Rename Method calculateTotal to computeSum in class OrderService"
     */
    private String description;
    
    /**
     * 旧版本（左侧）的代码位置信息
     * 每个位置包含：文件路径、类名、方法名、行号等
     */
    private List<CodeLocation> leftSideLocations;
    
    /**
     * 新版本（右侧）的代码位置信息
     */
    private List<CodeLocation> rightSideLocations;
    
    /**
     * 置信度（默认 1.0，表示 RefactoringMiner 的检测结果）
     */
    private double confidence;
    
    public RefactoringInfo() {
        this.leftSideLocations = new ArrayList<>();
        this.rightSideLocations = new ArrayList<>();
        this.confidence = 1.0;
    }
    
    // ==================== Getter/Setter ====================
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<CodeLocation> getLeftSideLocations() {
        return leftSideLocations;
    }
    
    public void setLeftSideLocations(List<CodeLocation> leftSideLocations) {
        this.leftSideLocations = leftSideLocations;
    }
    
    public void addLeftSideLocation(CodeLocation location) {
        this.leftSideLocations.add(location);
    }
    
    public List<CodeLocation> getRightSideLocations() {
        return rightSideLocations;
    }
    
    public void setRightSideLocations(List<CodeLocation> rightSideLocations) {
        this.rightSideLocations = rightSideLocations;
    }
    
    public void addRightSideLocation(CodeLocation location) {
        this.rightSideLocations.add(location);
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
    
    // ==================== 辅助方法 ====================
    
    @Override
    public String toString() {
        return "RefactoringInfo{" +
                "type='" + type + '\'' +
                ", description='" + description + '\'' +
                ", leftSideLocations=" + leftSideLocations.size() +
                ", rightSideLocations=" + rightSideLocations.size() +
                ", confidence=" + confidence +
                '}';
    }
    
    // ==================== 内部类：代码位置 ====================
    
    /**
     * 代码位置信息
     */
    public static class CodeLocation {
        private String filePath;       // 文件路径
        private String className;      // 类名（全限定名）
        private String methodName;     // 方法名（如果是方法级重构）
        private String fieldName;      // 字段名（如果是字段级重构）
        private int startLine;         // 起始行号
        private int endLine;           // 结束行号
        private String codeElement;    // 代码元素的字符串表示
        
        public CodeLocation() {
        }
        
        public CodeLocation(String filePath, String className) {
            this.filePath = filePath;
            this.className = className;
        }
        
        // Getter/Setter
        
        public String getFilePath() {
            return filePath;
        }
        
        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
        
        public String getClassName() {
            return className;
        }
        
        public void setClassName(String className) {
            this.className = className;
        }
        
        public String getMethodName() {
            return methodName;
        }
        
        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }
        
        public String getFieldName() {
            return fieldName;
        }
        
        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }
        
        public int getStartLine() {
            return startLine;
        }
        
        public void setStartLine(int startLine) {
            this.startLine = startLine;
        }
        
        public int getEndLine() {
            return endLine;
        }
        
        public void setEndLine(int endLine) {
            this.endLine = endLine;
        }
        
        public String getCodeElement() {
            return codeElement;
        }
        
        public void setCodeElement(String codeElement) {
            this.codeElement = codeElement;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (className != null) {
                sb.append(className);
            }
            if (methodName != null) {
                sb.append(".").append(methodName);
            }
            if (fieldName != null) {
                sb.append(".").append(fieldName);
            }
            if (filePath != null) {
                sb.append(" (").append(filePath).append(")");
            }
            return sb.toString();
        }
    }
}

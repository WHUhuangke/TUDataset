package com.github.model.edges;

import com.github.model.Edge;

/**
 * 测试关系（TestMethod→Method 或 TestClass→Class）
 * 表示测试方法/类测试业务方法/类
 */
public class TestsEdge extends Edge {
    
    /**
     * 标准构造函数（用于反射创建边副本）
     * 
     * @param testerId 测试者 ID（测试方法或测试类）
     * @param testedId 被测试者 ID（业务方法或业务类）
     */
    public TestsEdge(String testerId, String testedId) {
        super(testerId, testedId);
        setProperty("lineNumber", 0); // 默认行号
        setProperty("testType", "unit");
        setProperty("isDirectTest", true);
    }
    
    /**
     * 完整构造函数（用于创建新边）
     * 
     * @param testerId 测试者 ID
     * @param testedId 被测试者 ID
     * @param lineNumber 调用行号
     */
    public TestsEdge(String testerId, String testedId, int lineNumber) {
        super(testerId, testedId);
        setProperty("lineNumber", lineNumber);
        setProperty("testType", "unit"); // unit/integration/functional
        setProperty("isDirectTest", true); // 是否是直接测试
    }
    
    @Override
    public String getLabel() {
        return "TESTS";
    }
    
    @Override
    public String getEdgeType() {
        return "TESTS";
    }
    
    public int getLineNumber() {
        return (int) getProperty("lineNumber");
    }
    
    public void setTestType(String testType) {
        setProperty("testType", testType);
    }
    
    public void setDirectTest(boolean isDirectTest) {
        setProperty("isDirectTest", isDirectTest);
    }
    
    /**
     * 设置测试调用的代码片段
     * 例如: "calculator.add(2, 3);"
     */
    public void setTestStatement(String statement) {
        if (statement != null && statement.length() > 500) {
            statement = statement.substring(0, 497) + "...";
        }
        setProperty("testStatement", statement);
    }
    
    /**
     * 设置测试方法使用的断言类型
     * 例如: assertEquals, assertTrue, assertNotNull
     */
    public void setAssertionType(String assertionType) {
        setProperty("assertionType", assertionType);
    }
    
    /**
     * 记录测试场景描述
     */
    public void setTestScenario(String scenario) {
        setProperty("testScenario", scenario);
    }
}

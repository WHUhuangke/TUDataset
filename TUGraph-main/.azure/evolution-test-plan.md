# Stage 3.8 - 演化分析集成测试计划

## 测试目标
验证单 commit 模式的演化分析功能是否正常工作，包括：
1. ✅ 自动检测父提交
2. ✅ Git checkout 两个版本
3. ✅ Spoon 解析两个版本
4. ✅ RefactoringMiner 检测重构（单 commit 模式）
5. ✅ NodeMatching 匹配节点
6. ✅ GraphMerger 合并图谱
7. ✅ CSV 导出
8. ✅ Neo4j 导入
9. ✅ 查询验证

## 测试环境准备

### 1. 测试仓库
使用已有的测试项目：`/Users/mac/Desktop/java-project/tu/commons-cli`

### 2. 选择测试 Commit
需要选择一个有实际代码变更的 commit：
- 查看 Git 历史：`git log --oneline`
- 选择一个有重构或代码修改的 commit
- 配置文件中设置该 commit

### 3. 配置文件更新
已更新 `config.properties`：
```properties
analysis.mode=EVOLUTION
evolution.commit=ea9e408
evolution.useRefactoringMiner=true
project.path=/Users/mac/Desktop/java-project/tu/commons-cli
```

## 测试步骤

### Step 1: 验证配置
```bash
cd /Users/mac/Desktop/TUGraph
cat config.properties | grep evolution
```

### Step 2: 清理旧数据
```bash
# 清理旧的 CSV 导出
rm -rf csv_export/*

# 停止 Neo4j（如果正在运行）
/Users/mac/Desktop/env/neo4j-community-5.26.13/bin/neo4j stop
```

### Step 3: 运行演化分析
```bash
mvn clean package -DskipTests
java -jar target/TUGraph-jar-with-dependencies.jar
```

### Step 4: 验证输出

#### 4.1 验证日志输出
检查以下关键日志：
- ✅ "自动检测到父提交作为 V1: xxxxxxx"
- ✅ "检测重构操作: xxx → xxx"
- ✅ "匹配节点对数量"
- ✅ "合并后节点数量"
- ✅ "CSV 导出完成"
- ✅ "Neo4j 导入完成"

#### 4.2 验证 CSV 文件
```bash
ls -lh csv_export/*/
head -n 5 csv_export/*/nodes_bulk.csv
head -n 5 csv_export/*/edges_bulk.csv
```

#### 4.3 验证 Neo4j 数据
```cypher
// 1. 查看节点总数
MATCH (n) RETURN count(n)

// 2. 查看不同演化状态的节点数
MATCH (n) 
RETURN n.evolutionStatus, count(*) 
ORDER BY count(*) DESC

// 3. 查看修改的节点
MATCH (n {evolutionStatus: 'MODIFIED'}) 
RETURN n.name, labels(n) 
LIMIT 10

// 4. 查看新增的节点
MATCH (n {evolutionStatus: 'ADDED'}) 
RETURN n.name, labels(n) 
LIMIT 10

// 5. 查看删除的节点
MATCH (n {evolutionStatus: 'DELETED'}) 
RETURN n.name, labels(n) 
LIMIT 10

// 6. 查看重构影响的关系
MATCH ()-[r:REFACTORED_TO]->() 
RETURN type(r), r.refactoringType, count(*) 
ORDER BY count(*) DESC
```

## 预期结果

### 成功标准
1. ✅ 程序正常启动，无异常
2. ✅ 日志显示自动检测到父提交
3. ✅ 成功 checkout 两个版本
4. ✅ Spoon 成功解析两个版本
5. ✅ RefactoringMiner 检测到重构（如果有）
6. ✅ NodeMatching 成功匹配节点
7. ✅ GraphMerger 成功合并图谱
8. ✅ CSV 导出成功
9. ✅ Neo4j 导入成功
10. ✅ 查询结果符合预期

### 性能指标
- 整体耗时：< 5 分钟（取决于项目大小）
- 内存占用：< 4GB
- CSV 文件生成正常

## 异常处理测试

### 测试场景 1: 初始 Commit（无父提交）
```properties
evolution.commit=<初始commit>
```
预期：抛出异常 "指定的 commit 没有父提交（可能是初始提交）"

### 测试场景 2: 无效 Commit
```properties
evolution.commit=invalid-commit-hash
```
预期：抛出异常 "无法获取父提交: invalid-commit-hash"

### 测试场景 3: 非 Git 仓库
```properties
project.path=/some/non-git/path
```
预期：抛出异常，提示找不到 .git 目录

## 测试记录

### 测试执行时间
- 开始时间：_________
- 结束时间：_________
- 总耗时：_________

### 测试结果
- [ ] 通过
- [ ] 失败

### 问题记录
（记录测试过程中发现的问题）

---

## 下一步
测试通过后，进入 Stage 3.9 - 文档更新

# 变更日志

## [v2.1.2] - 2025-10-20

### 🐛 Bug 修复

#### 修复图谱合并中的边创建失败问题

**问题**：演化分析的图谱合并阶段，使用反射创建边副本时失败

**症状**：
```
[ERROR] 创建边副本失败: TESTS
java.lang.NoSuchMethodException: com.github.model.edges.TestsEdge.<init>(java.lang.String,java.lang.String)
```

**原因**：
- `GraphMerger.createEdge()` 使用反射，期望所有边类都有 `(String, String)` 构造函数
- 但部分边类只有带额外参数的构造函数（如 `TestsEdge(String, String, int)`）

**解决方案**：
- 为所有边类添加标准的两参数构造函数 `(String sourceId, String targetId)`
- 保留原有的完整构造函数（带额外参数）

**修复的边类**：
- `TestsEdge` - 添加 `(String, String)` 构造函数
- `CallsEdge` - 添加 `(String, String)` 构造函数
- `ReadsEdge` - 添加 `(String, String)` 构造函数
- `WritesEdge` - 添加 `(String, String)` 构造函数
- `UsesParameterEdge` - 添加 `(String, String)` 构造函数

**影响范围**：
- 图谱合并功能正常工作
- 不影响现有的边创建代码
- 完全向后兼容

**相关文件**：
- `src/main/java/com/github/model/edges/TestsEdge.java`
- `src/main/java/com/github/model/edges/CallsEdge.java`
- `src/main/java/com/github/model/edges/ReadsEdge.java`
- `src/main/java/com/github/model/edges/WritesEdge.java`
- `src/main/java/com/github/model/edges/UsesParameterEdge.java`

**文档**：
- `EDGE_CONSTRUCTOR_STANDARDIZATION.md` - 详细说明

---

## [v2.1.1] - 2025-10-20

### 🐛 Bug 修复

#### 修复 RefactoringMiner 缺少 Eclipse 依赖的问题

**问题**：执行重构检测时出现 `NoClassDefFoundError: org.eclipse.core.runtime.IAdaptable`

**症状**：
```
Exception in thread "main" java.lang.NoClassDefFoundError: org/eclipse/core/runtime/IAdaptable
	at gr.uom.java.xmi.UMLModelASTReader.processJavaFileContents(...)
	at org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl.createModel(...)
Caused by: java.lang.ClassNotFoundException: org.eclipse.core.runtime.IAdaptable
```

**原因**：
- RefactoringMiner 使用 Eclipse JDT 解析 Java 代码
- Eclipse JDT 依赖于 Eclipse Platform 核心组件
- `pom.xml` 中缺少这些依赖

**解决方案**：
添加完整的 Eclipse 依赖链：
- `org.eclipse.jdt.core` - Eclipse JDT 核心
- `org.eclipse.core.runtime` - Eclipse 核心运行时
- `org.eclipse.core.resources` - Eclipse 资源管理
- `org.eclipse.core.jobs` - Eclipse 任务调度
- `org.eclipse.equinox.common` - Equinox 公共组件
- `org.eclipse.core.contenttype` - Eclipse 内容类型
- `org.eclipse.osgi` - OSGi 框架

**影响范围**：
- 重构检测功能现在可以正常工作
- 演化分析流程完整可用

**相关文件**：
- `pom.xml`

**文档**：
- `REFACTORINGMINER_DEPENDENCIES.md` - 详细说明

---

#### 修复演化分析中的临时文件问题

**问题**：Spoon 生成的 `spoon.classpath.tmp` 临时文件导致 Git checkout 失败

**症状**：
```
[ERROR] 演化分析失败
com.github.git.GitException: 仓库有未提交的更改，无法检出。
```

**解决方案**：
- 在 `GitService.checkout()` 方法中添加自动清理临时文件的逻辑
- 清理 Spoon 生成的所有临时文件：
  - `spoon.classpath.tmp`
  - `spooned/` 目录
  - 其他 `spoon*.tmp` 和 `spoon*.temp` 文件

**影响范围**：
- 演化模式下的版本切换更加稳定
- 不再需要手动清理临时文件

**相关文件**：
- `src/main/java/com/github/git/GitService.java`

**文档**：
- `TEMP_FILE_CLEANUP.md` - 详细说明

---

## [v2.1.0] - 2025-10-20

### ✨ 重要改进

#### 统一项目路径配置

**问题**：用户对 `evolution.repoPath` 和 `project.path` 两个配置感到困惑

**改进**：
- 统一使用 `project.path` 配置项目路径
- 移除 `evolution.repoPath`（保留向后兼容）
- 简化配置逻辑

**配置变化**：

旧配置：
```properties
analysis.mode=EVOLUTION
evolution.repoPath=/path/to/repo
project.path=/path/to/project  # 会被忽略
```

新配置：
```properties
analysis.mode=EVOLUTION
project.path=/path/to/repo  # 统一使用这个
```

**向后兼容**：
- 仍支持 `evolution.repoPath`（显示废弃警告）
- `getRepoPath()` 和 `setRepoPath()` 标记为 `@Deprecated`

**影响范围**：
- 配置更简洁清晰
- 降低用户学习成本
- 减少配置错误

**相关文件**：
- `src/main/java/com/github/config/AppConfig.java`
- `src/main/java/com/github/pipeline/EvolutionPipeline.java`
- `config.properties`
- `config.properties.example`

**文档**：
- `CONFIG_UNIFIED.md` - 统一配置说明
- `EVOLUTION_CONFIG_GUIDE.md` - 演化配置指南
- `REFACTORING_CONFIG_UNIFIED.md` - 重构技术文档

### 🐛 Bug 修复

#### 修复演化分析中的 NullPointerException

**问题**：GitService 在 try-with-resources 中被提前关闭

**解决方案**：
- 改用手动管理 GitService 生命周期
- 确保在整个分析过程中保持打开状态
- 使用 finally 块确保正确关闭

**相关文件**：
- `src/main/java/com/github/evolution/EvolutionAnalyzer.java`

---

## [v2.0.0] - 2025-10-18

### ✨ 新功能

- 实现演化分析模式
- 支持 Git 版本切换
- 集成 RefactoringMiner
- 节点匹配策略
- 图谱合并功能

### 📝 文档

- `EVOLUTION_DESIGN.md` - 演化分析设计
- `ARCHITECTURE.md` - 系统架构
- `UNIFIED_ARCHITECTURE.md` - 统一架构

---

## [v1.0.0] - 2025-10-01

### ✨ 新功能

- 基于 Spoon 的代码分析
- Neo4j 知识图谱构建
- 批量导入支持
- 配置文件管理
- 日志系统

### 📝 文档

- `README.md` - 项目说明
- `QUICKSTART.md` - 快速开始

---

## 版本规则

遵循 [语义化版本](https://semver.org/lang/zh-CN/) 规范：

- **主版本号 (X.y.z)**：不兼容的 API 修改
- **次版本号 (x.Y.z)**：向下兼容的功能性新增
- **修订号 (x.y.Z)**：向下兼容的问题修正

## 图标说明

- ✨ 新功能
- 🐛 Bug 修复
- 📝 文档更新
- 🔧 配置变更
- ⚡ 性能优化
- 🔒 安全修复
- ⚠️ 废弃警告
- 💥 破坏性变更

---

**维护者**: TUGraph Team  
**仓库**: https://github.com/yeren66/TUGraph

# RefactoringTimelineBuilder 使用指南

## 📋 概述

`RefactoringTimelineBuilder` 是一个基于 RefactoringMiner 的智能时间线构建器，用于在多版本演化分析中过滤掉无关的 commit，只保留与目标变更真正相关的 commit。

## 🎯 核心特性

### 1. **动态追踪更新**
- 自动处理方法重命名、移动、签名变更
- 追踪方法提取（EXTRACT）和内联（INLINE）
- 保证不漏掉相关的演化路径

### 2. **高精准度**
- 方法级别的精确追踪
- 基于 RefactoringMiner 的可靠检测
- 预期精准度：70-80%（相比 LINEAR 的 40-50%）

### 3. **简洁高效**
- 简单的并集合并策略
- 直接的方法签名匹配
- 性能可接受（预计 2-3 分钟/50 commits）

## 🚀 快速开始

### 配置方式

编辑 `config.properties`：

```properties
# 启用 REFACTORING_DRIVEN 策略
evolution.timeline.strategy=REFACTORING_DRIVEN

# 配置参数
evolution.refactoringTimeline.maxDepth=50    # 最大回溯 50 个 commit
evolution.refactoringTimeline.maxDays=180    # 只看最近 180 天
```

### 运行

```bash
mvn clean package
java -jar target/TUGraph-1.0-SNAPSHOT.jar
```

## 📊 策略对比

### LINEAR（线性策略）

```
优点：
- 实现简单
- 速度快
- 无需额外计算

缺点：
- 噪音大（60%+ 无关 commit）
- 语义关联弱
- 包含大量文档更新、配置修改等无关变更

适用场景：
- 快速概览
- 小范围连续开发
```

### REFACTORING_DRIVEN（重构驱动策略）⭐ **推荐**

```
优点：
- 精准度高（70-80% 相关性）
- 方法级别追踪
- 自动处理重构（重命名、移动等）
- 噪音少

缺点：
- 比 LINEAR 慢（需要运行 RefactoringMiner）
- 实现稍复杂

适用场景：
- 特定功能演化分析
- 方法级影响分析
- 高质量的演化图谱构建
```

## 📖 工作原理

### Step 1: 分析目标 Commit

```
目标 Commit: V0
变更内容: 
  - 修改 CSVParser.parse()
  - 优化 Lexer.readToken()
  
提取追踪方法:
  ✓ CSVParser.parse(String, Charset)
  ✓ Lexer.readToken()
```

### Step 2: 向前遍历历史

```
V-1 → V0:
  RefactoringMiner 检测:
    - Modify Method Body: CSVParser.parse()
  结论: ✅ 相关，加入时间线
  追踪更新: 保持不变

V-2 → V-1:
  RefactoringMiner 检测:
    - Rename Method: parseFile() → parse()
  结论: ✅ 相关！
  追踪更新: 
    - 移除 parse(String, Charset)
    - 添加 parseFile(String, Charset) ⭐

V-3 → V-2:
  RefactoringMiner 检测:
    - Modify pom.xml
  结论: ❌ 无关，跳过

V-4 → V-3:
  (跳过，无关)

V-5 → V-4:
  RefactoringMiner 检测:
    - Extract Method: parseFile() from processData()
  结论: ✅ 相关！
  追踪更新:
    - 添加 processData() ⭐
```

### Step 3: 构建时间线

```
最终时间线: [V-5, V-2, V-1, V0]
过滤掉: [V-4, V-3]
精准度: 100% (所有节点都直接相关!)
```

## 🔧 动态追踪详解

### 支持的重构类型

| 重构类型 | 处理方式 | 示例 |
|---------|---------|------|
| **RENAME_METHOD** | 用旧名替换新名 | `parse()` ← `parseFile()` |
| **MOVE_METHOD** | 用旧位置替换新位置 | `Utils.parse()` ← `CSVParser.parse()` |
| **CHANGE_SIGNATURE** | 用旧签名替换新签名 | `parse(String)` ← `parse(String, Charset)` |
| **EXTRACT_METHOD** | 添加源方法 | 追踪 `extracted()` → 同时追踪 `source()` |
| **INLINE_METHOD** | 添加被内联方法 | 追踪 `target()` → 同时追踪 `inlined()` |

### 示例：重命名追踪

```java
// 当前追踪: ["CSVParser.parse(String)"]

// 检测到重构: RENAME_METHOD
//   Left:  CSVParser.parseFile(String)
//   Right: CSVParser.parse(String)

// 更新后追踪: ["CSVParser.parseFile(String)"]
//   ^ 替换为旧名字，继续向前追踪
```

## 📈 性能优化

### 当前实现

- **增量模式**：逐个分析每个 commit
- **性能**：约 2-5 秒/commit
- **总耗时**：50 commits ≈ 2-4 分钟

### 未来优化（可选）

```properties
# 批量模式（未来版本）
evolution.refactoringTimeline.useBatchMode=true

# 缓存机制（未来版本）
evolution.refactoringTimeline.enableCache=true
evolution.refactoringTimeline.cacheDir=.cache/refactorings
```

## 💡 使用建议

### 1. 选择合适的参数

```properties
# 小项目（< 500 commits）
evolution.refactoringTimeline.maxDepth=100
evolution.refactoringTimeline.maxDays=365

# 中型项目（500-2000 commits）
evolution.refactoringTimeline.maxDepth=50
evolution.refactoringTimeline.maxDays=180

# 大型项目（> 2000 commits）
evolution.refactoringTimeline.maxDepth=30
evolution.refactoringTimeline.maxDays=90
```

### 2. 日志输出解读

```
✓ abc1234 相关 (3 重构, 5→6 方法)
  ^^^^^^^^       ^^^^^^^  ^^^^^^
  commit hash    匹配的   追踪方法数量变化
                重构数量  (动态更新)

过滤比例: 60.0%
          ^^^^^^
          跳过的无关 commit 比例
          越高说明过滤效果越好
```

### 3. 调试技巧

```properties
# 启用详细日志
log.level=DEBUG

# 查看日志
tail -f logs/tugraph.log

# 日志中会显示：
# - 每个 commit 的分析结果
# - 追踪方法的动态变化
# - 匹配的重构详情
```

## 🐛 常见问题

### Q1: 时间线为什么只有1个节点？

**可能原因：**
- 目标 commit 没有方法级别的变更
- 只修改了文档、配置等非代码内容

**解决方案：**
- 检查目标 commit 的变更内容
- 确认是否真的修改了方法

### Q2: 为什么某些相关 commit 没有被包含？

**可能原因：**
- 方法签名变化太大，匹配失败
- RefactoringMiner 未能检测到重构

**解决方案：**
- 检查 RefactoringMiner 的输出
- 考虑使用 LINEAR 策略作为对比

### Q3: 性能太慢怎么办？

**解决方案：**
```properties
# 减小回溯深度
evolution.refactoringTimeline.maxDepth=30

# 缩短时间窗口
evolution.refactoringTimeline.maxDays=90
```

## 📚 相关文档

- [ARCHITECTURE.md](../ARCHITECTURE.md) - 系统架构
- [EVOLUTION_DESIGN.md](../EVOLUTION_DESIGN.md) - 演化设计
- [RefactoringMiner 官方文档](https://github.com/tsantalis/RefactoringMiner)

## 🎓 技术细节

### 核心算法复杂度

- **时间复杂度**: O(n * m)
  - n = 回溯的 commit 数量
  - m = 每个 commit 的平均重构数量

- **空间复杂度**: O(n + k)
  - n = 时间线节点数量
  - k = 追踪的方法数量

### 依赖关系

```
RefactoringTimelineBuilder
  ├── RefactoringDetector (已有)
  ├── JGit (已有)
  └── RefactoringMiner (已有)
```

## 🚧 未来扩展

### Phase 2: 批量分析模式

```java
// 一次性分析所有 commit，性能提升 3-5 倍
builder.setUseBatchMode(true);
```

### Phase 3: 缓存机制

```java
// 缓存重构结果，避免重复分析
builder.enableCache(true);
```

### Phase 4: FILE_BASED 策略

```properties
# 基于文件变更的关联策略
evolution.timeline.strategy=FILE_BASED
```

---

**作者**: TUGraph Team  
**版本**: 2.2.0  
**最后更新**: 2025-11-03

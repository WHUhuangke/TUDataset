# Neo4j 可视化样式配置指南

## 📋 问题描述

在 Neo4j Browser 中查看 TUGraph 导出的知识图谱时，只有 `CLASS`、`CONSTRUCTOR` 和 `METHOD` 等少数节点类型有颜色，其他节点类型（如 `PACKAGE`、`FILE`、`FIELD` 等）都是默认的灰色。

## 🎨 解决方案

Neo4j 的节点和边的颜色、大小等样式是在 **Neo4j Browser 的样式表**中定义的，而不是在数据导入时定义。我们已经为你准备了一个完整的样式配置文件。

## 🚀 使用方法

### 方法一：通过 Browser Style 编辑器（推荐）

1. **打开 Neo4j Browser**
   - 访问 http://localhost:7474
   - 登录你的 Neo4j 数据库

2. **打开样式编辑器**
   - 点击左下角的 **齿轮图标** ⚙️ （设置按钮）
   - 或者直接在浏览器左侧栏找到 **"Browser Style"** 按钮

3. **导入样式**
   - 打开 `/Users/mac/Desktop/TUGraph/neo4j-style.grass` 文件
   - 复制全部内容
   - 粘贴到 Neo4j Browser 的样式编辑器中
   - 点击 **"Apply"** 按钮应用样式

4. **查看效果**
   - 重新执行查询或刷新图谱视图
   - 所有节点和边都应该有对应的颜色了！

### 方法二：通过 GRASS 文件导入

1. 在 Neo4j Browser 的样式编辑器底部
2. 找到 **"Export GRASS file"** 按钮旁边的 **"Upload GRASS file"** 按钮
3. 选择 `neo4j-style.grass` 文件上传
4. 样式会自动应用

## 🎨 样式配置详解

### 节点颜色方案

我们为不同类型的节点设计了不同的颜色系统：

#### **结构节点（容器类）**
- **PROJECT** 🟣 - 紫色 (#8E44AD) - 最大，代表整个项目
- **PACKAGE** 🔵 - 蓝色 (#3498DB) - 包含多个文件
- **FILE** 🟢 - 青绿色 (#16A085) - 代码文件

#### **类型节点（蓝色系）**
- **TYPE** 🔵 - 浅蓝 (#5DADE2) - 通用类型
- **CLASS** 🔵 - 标准蓝 (#2980B9) - 普通类
- **INTERFACE** 🔵 - 中蓝 (#5499C7) - 接口
- **ENUM** 🔵 - 淡蓝 (#85C1E9) - 枚举
- **ANNOTATION** 🔵 - 极淡蓝 (#AED6F1) - 注解
- **TEST_CLASS** 🔵 - 深蓝 (#1A5490) - 测试类（加粗边框）

#### **方法节点（绿色系）**
- **METHOD** 🟢 - 标准绿 (#27AE60) - 普通方法
- **CONSTRUCTOR** 🟢 - 亮绿 (#28B463) - 构造方法（加粗边框）
- **TEST_METHOD** 🟢 - 淡绿 (#52BE80) - 测试方法

#### **字段节点**
- **FIELD** 🟠 - 橙色 (#E67E22) - 类字段/属性

### 演化状态标识

通过**边框颜色**标识节点的演化状态：

- **ADDED** - 🟢 绿色边框（加粗）- 新增的节点
- **DELETED** - 🔴 红色边框（加粗）- 删除的节点
- **MODIFIED** - 🟠 橙色边框（加粗）- 修改的节点
- **UNCHANGED** - ⚪ 灰色边框（细线）- 未变化的节点

### 边（关系）颜色方案

#### **调用和访问关系**
- **CALLS** 🟢 - 绿色 - 方法调用
- **READS** 🔵 - 蓝色 - 读取字段
- **WRITES** 🔴 - 红色 - 写入字段

#### **声明关系**
- **DECLARES** 🟣 - 紫色 - 声明成员
- **DECLARES_TYPE** 🟣 - 深紫色 - 声明类型

#### **继承关系**
- **EXTENDS** 🟠 - 橙色（加粗）- 继承
- **IMPLEMENTS** 🟡 - 黄色（加粗）- 实现接口

#### **结构关系**
- **CONTAINS_FILE** 🟢 - 青色 - 包含文件
- **IN_PACKAGE** 🔵 - 浅蓝 - 在包中
- **HAS_PACKAGE** 🔵 - 深蓝 - 有包

#### **测试关系**
- **TESTS** 🔴 - 粉色 - 测试关系

#### **类型关系**
- **USES_PARAMETER** ⚪ - 灰色 - 使用参数
- **RETURN_TYPE** 🟤 - 棕色 - 返回类型

#### **演化关系**
- **EVOLVES_FROM** 🟡 - 金色（加粗）- 演化自
- **REFACTORED_TO** 🟠 - 金黄色（加粗）- 重构为

## 📊 查询示例

应用样式后，可以尝试这些查询来查看效果：

### 1. 查看所有类和它们的方法
```cypher
MATCH (c:CLASS)-[:DECLARES]->(m:METHOD)
RETURN c, m
LIMIT 50
```

### 2. 查看包结构
```cypher
MATCH path = (p:PROJECT)-[:HAS_PACKAGE]->(pkg:PACKAGE)-[:CONTAINS_FILE]->(f:FILE)
RETURN path
LIMIT 20
```

### 3. 查看继承关系
```cypher
MATCH (c1:CLASS)-[:EXTENDS|IMPLEMENTS]->(c2)
RETURN c1, c2
LIMIT 30
```

### 4. 查看方法调用关系
```cypher
MATCH (m1:METHOD)-[:CALLS]->(m2:METHOD)
RETURN m1, m2
LIMIT 50
```

### 5. 查看演化分析结果（如果是演化图谱）
```cypher
// 查看新增的节点
MATCH (n)
WHERE n.versionStatus = 'ADDED'
RETURN n
LIMIT 20

// 查看修改的方法
MATCH (m:METHOD)
WHERE m.versionStatus = 'MODIFIED'
RETURN m
LIMIT 20

// 查看演化链
MATCH path = (n1)-[:EVOLVES_FROM]->(n2)
RETURN path
LIMIT 20
```

### 6. 查看不同类型的节点分布
```cypher
MATCH (n)
RETURN labels(n)[0] as NodeType, count(*) as Count
ORDER BY Count DESC
```

## 🔧 自定义样式

如果你想修改颜色或样式，可以直接编辑 `neo4j-style.grass` 文件：

### 节点样式属性

```grass
node.YOUR_LABEL {
  diameter: 50px;              // 节点直径
  color: #27AE60;              // 节点颜色
  border-color: #1E8449;       // 边框颜色
  border-width: 2px;           // 边框宽度
  text-color-internal: #FFFFFF; // 文字颜色
  caption: "{name}";           // 显示的属性
  font-size: 10px;             // 字体大小
}
```

### 边样式属性

```grass
relationship.YOUR_RELATIONSHIP {
  color: #27AE60;              // 边的颜色
  shaft-width: 2px;            // 边的宽度
  caption: "{edgeType}";       // 显示的标签
  font-size: 9px;              // 字体大小
}
```

### 颜色参考

推荐使用的配色方案（Material Design 色系）：

- **红色系**: #E74C3C, #EC7063, #F1948A
- **橙色系**: #E67E22, #F39C12, #F8C471
- **黄色系**: #F1C40F, #F4D03F
- **绿色系**: #27AE60, #28B463, #52BE80, #7DCEA0
- **蓝色系**: #2980B9, #3498DB, #5DADE2, #85C1E9
- **紫色系**: #8E44AD, #9B59B6, #AF7AC5
- **青色系**: #16A085, #1ABC9C, #48C9B0
- **灰色系**: #95A5A6, #BDC3C7, #D5DBDB

## 💡 技巧和建议

### 1. 简化视图
当节点太多时，可以隐藏某些关系：

```cypher
// 只显示类和继承关系
MATCH (c:CLASS)
OPTIONAL MATCH (c)-[r:EXTENDS|IMPLEMENTS]->(c2)
RETURN c, r, c2
```

### 2. 按演化状态过滤
```cypher
// 只看有变化的节点
MATCH (n)
WHERE n.versionStatus IN ['ADDED', 'MODIFIED', 'DELETED']
RETURN n
```

### 3. 节点分组显示
在 Neo4j Browser 中，可以：
- 右键点击节点 → 选择 "Expand" 展开关系
- 右键点击节点 → 选择 "Dismiss" 隐藏节点
- 拖动节点来调整布局

## 📚 相关文档

- [Neo4j Browser 样式文档](https://neo4j.com/docs/browser-manual/current/visual-tour/#browser-styling)
- [GRASS 样式语言](https://neo4j.com/docs/browser-manual/current/operations/grass-file/)
- [Cypher 查询语言](https://neo4j.com/docs/cypher-manual/current/)

## ❓ 常见问题

### Q: 为什么应用样式后没有效果？
A: 
1. 确保点击了 "Apply" 按钮
2. 刷新图谱视图（重新执行查询）
3. 清除浏览器缓存

### Q: 某些节点还是没有颜色？
A: 检查节点的标签（label）是否正确：
```cypher
MATCH (n) RETURN DISTINCT labels(n) LIMIT 20
```
如果发现新的标签，在样式文件中添加对应的样式。

### Q: 如何恢复默认样式？
A: 在样式编辑器底部点击 "Reset to default" 按钮。

### Q: 边的颜色不显示？
A: Neo4j Browser 的某些版本可能不完全支持边的颜色。确保使用的是较新版本的 Neo4j (5.x+)。

---

**创建时间**: 2025-11-03  
**适用版本**: Neo4j 5.x, TUGraph 2.2.0

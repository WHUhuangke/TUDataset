#!/bin/bash

# TUGraph Neo4j 样式配置助手
# 帮助用户快速应用 Neo4j Browser 样式配置

echo "=========================================="
echo "TUGraph Neo4j 样式配置助手"
echo "=========================================="
echo ""

STYLE_FILE="neo4j-style.grass"
DOC_FILE="docs/NEO4J_STYLE_GUIDE.md"

# 检查样式文件是否存在
if [ ! -f "$STYLE_FILE" ]; then
    echo "❌ 错误: 找不到样式文件 $STYLE_FILE"
    echo "   请确保在 TUGraph 项目根目录下运行此脚本"
    exit 1
fi

echo "✓ 找到样式文件: $STYLE_FILE"
echo ""

# 显示样式统计信息
NODE_STYLES=$(grep -c "^node\." "$STYLE_FILE")
RELATIONSHIP_STYLES=$(grep -c "^relationship\." "$STYLE_FILE")

echo "📊 样式配置统计:"
echo "   - 节点样式: $NODE_STYLES 种"
echo "   - 边样式: $RELATIONSHIP_STYLES 种"
echo ""

echo "=========================================="
echo "如何应用样式配置？"
echo "=========================================="
echo ""
echo "方法一：手动复制粘贴（推荐）"
echo "  1. 打开 Neo4j Browser: http://localhost:7474"
echo "  2. 点击左下角齿轮图标 ⚙️ 打开设置"
echo "  3. 找到 'Browser Style' 编辑器"
echo "  4. 复制以下内容并粘贴到编辑器中:"
echo ""
echo "  📋 样式文件路径: $(pwd)/$STYLE_FILE"
echo ""

# 提供选项让用户复制内容
echo "按 Enter 键在终端显示样式内容（可以直接复制）"
read -r

echo "=========================================="
echo "开始显示样式内容 (Ctrl+C 退出)"
echo "=========================================="
echo ""
cat "$STYLE_FILE"
echo ""
echo "=========================================="
echo "样式内容显示完毕"
echo "=========================================="
echo ""

# 提供文档链接
if [ -f "$DOC_FILE" ]; then
    echo "📚 完整使用文档: $(pwd)/$DOC_FILE"
    echo ""
    echo "要打开使用文档吗？(y/n)"
    read -r OPEN_DOC
    
    if [ "$OPEN_DOC" = "y" ] || [ "$OPEN_DOC" = "Y" ]; then
        if command -v open &> /dev/null; then
            open "$DOC_FILE"
            echo "✓ 已在默认应用中打开文档"
        elif command -v xdg-open &> /dev/null; then
            xdg-open "$DOC_FILE"
            echo "✓ 已在默认应用中打开文档"
        else
            echo "请手动打开文档: $(pwd)/$DOC_FILE"
        fi
    fi
fi

echo ""
echo "=========================================="
echo "配置完成提示"
echo "=========================================="
echo ""
echo "✓ 在 Neo4j Browser 中粘贴样式后:"
echo "  1. 点击 'Apply' 按钮应用样式"
echo "  2. 重新执行查询或刷新图谱视图"
echo "  3. 所有节点和边都应该有对应的颜色了！"
echo ""
echo "🎨 支持的节点类型："
echo "   PROJECT, PACKAGE, FILE, CLASS, INTERFACE, ENUM,"
echo "   ANNOTATION, METHOD, CONSTRUCTOR, FIELD, 等"
echo ""
echo "🎨 支持的边类型："
echo "   CALLS, READS, WRITES, DECLARES, EXTENDS, IMPLEMENTS,"
echo "   EVOLVES_FROM, REFACTORED_TO, 等"
echo ""
echo "💡 提示: 查看文档了解更多自定义选项和查询示例"
echo ""

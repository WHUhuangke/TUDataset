#!/bin/bash

# 代码变更分析工具运行脚本
# 用法: ./run.sh <repo_list_file>

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印彩色信息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查参数
if [ $# -eq 0 ]; then
    print_error "用法: $0 <repo_list_file>"
    print_info "repo_list_file: 包含仓库路径列表的txt文件，每行一个路径"
    echo ""
    print_info "示例:"
    print_info "  $0 repos.txt"
    print_info "  $0 /path/to/repository_list.txt"
    exit 1
fi

REPO_LIST_FILE="$1"

# 检查文件是否存在
if [ ! -f "$REPO_LIST_FILE" ]; then
    print_error "文件不存在: $REPO_LIST_FILE"
    exit 1
fi

# 检查文件是否为空
if [ ! -s "$REPO_LIST_FILE" ]; then
    print_error "文件为空: $REPO_LIST_FILE"
    exit 1
fi

# 项目配置
PROJECT_NAME="CodeReviewTool"
MAIN_CLASS="CodeReviewTool"
JAR_NAME="codereviewtool.jar"  # 假设项目打包后的jar文件名
SRC_DIR="src"
LIB_DIR="lib"
BUILD_DIR="build"
OUTPUT_DIR="output"

# 创建必要的目录
mkdir -p "$BUILD_DIR"
mkdir -p "$OUTPUT_DIR"

print_info "开始运行代码变更分析工具..."
print_info "仓库列表文件: $REPO_LIST_FILE"
print_info "当前目录: $(pwd)"
echo ""

# 检查是否已经编译
if [ ! -f "$JAR_NAME" ]; then
    print_warning "未找到已编译的JAR文件，尝试编译项目..."
    
    # 检查源代码目录是否存在
    if [ ! -d "$SRC_DIR" ]; then
        print_error "源代码目录不存在: $SRC_DIR"
        print_info "请确保在项目根目录下运行此脚本"
        exit 1
    fi
    
    # 查找依赖库
    if [ -d "$LIB_DIR" ] && [ "$(ls -A $LIB_DIR/*.jar 2>/dev/null)" ]; then
        print_info "发现依赖库，使用类路径编译..."
        # 构建类路径
        CLASSPATH=$(find $LIB_DIR -name "*.jar" | tr '\n' ':')
        javac -d "$BUILD_DIR" -cp "$CLASSPATH" $(find $SRC_DIR -name "*.java")
    else
        print_info "未发现依赖库，直接编译..."
        javac -d "$BUILD_DIR" $(find $SRC_DIR -name "*.java")
    fi
    
    # 检查编译是否成功
    if [ $? -eq 0 ]; then
        print_success "项目编译成功"
        
        # 创建可执行JAR（如果需要）
        print_info "创建可执行JAR..."
        jar cfe "$JAR_NAME" "$MAIN_CLASS" -C "$BUILD_DIR" .
    else
        print_error "项目编译失败"
        exit 1
    fi
else
    print_success "找到已编译的JAR文件: $JAR_NAME"
fi

# 检查Java环境
if ! command -v java &> /dev/null; then
    print_error "未找到Java运行时环境"
    print_info "请确保已安装Java并配置环境变量"
    exit 1
fi

print_info "Java版本: $(java -version 2>&1 | head -n1)"

# 验证仓库列表文件格式
print_info "验证仓库列表文件..."
VALID_REPOS=0
while IFS= read -r repo_path; do
    repo_path=$(echo "$repo_path" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    if [ -n "$repo_path" ]; then
        if [ -d "$repo_path" ]; then
            if [ -d "$repo_path/.git" ]; then
                VALID_REPOS=$((VALID_REPOS + 1))
                print_info "  ✅ 有效Git仓库: $repo_path"
            else
                print_warning "  ⚠️  目录不是Git仓库: $repo_path"
            fi
        else
            print_warning "  ⚠️  目录不存在: $repo_path"
        fi
    fi
done < "$REPO_LIST_FILE"

if [ $VALID_REPOS -eq 0 ]; then
    print_error "未找到有效的Git仓库"
    exit 1
fi

print_info "找到 $VALID_REPOS 个有效Git仓库"
echo ""

# 运行分析工具
print_info "开始分析代码变更..."
echo "="*60

if [ -f "$JAR_NAME" ]; then
    # 使用JAR文件运行
    java -jar "$JAR_NAME" "$REPO_LIST_FILE"
else
    # 使用编译的类文件运行
    if [ -d "$LIB_DIR" ] && [ "$(ls -A $LIB_DIR/*.jar 2>/dev/null)" ]; then
        CLASSPATH="$BUILD_DIR:$(find $LIB_DIR -name "*.jar" | tr '\n' ':')"
        java -cp "$CLASSPATH" "$MAIN_CLASS" "$REPO_LIST_FILE"
    else
        java -cp "$BUILD_DIR" "$MAIN_CLASS" "$REPO_LIST_FILE"
    fi
fi

# 检查执行结果
if [ $? -eq 0 ]; then
    echo ""
    print_success "代码变更分析完成！"
    print_info "结果文件保存在当前目录的JSON文件中"
    
    # 显示生成的JSON文件
    JSON_FILES=$(ls *.json 2>/dev/null | wc -l)
    if [ $JSON_FILES -gt 0 ]; then
        print_info "生成的JSON文件:"
        ls -la *.json
    fi
else
    print_error "代码变更分析执行失败"
    exit 1
fi

echo ""
print_info "运行完成时间: $(date)"

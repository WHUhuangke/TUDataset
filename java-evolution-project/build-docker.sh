#!/bin/bash
# build-docker.sh

set -e  # 遇到错误立即退出

echo "开始构建Java演化分析Docker环境..."

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo "错误: 未找到Docker，请先安装Docker"
    exit 1
fi

# 检查docker-compose是否安装
if ! command -v docker-compose &> /dev/null; then
    echo "错误: 未找到docker-compose，请先安装Docker Compose"
    exit 1
fi

# 创建必要的目录
mkdir -p {scripts,configs,data,notebooks,docker}

# 修复：使用可用的Maven镜像标签
if [ ! -f docker/Dockerfile.jupyter ]; then
    echo "创建 Jupyter Dockerfile..."
    cat > docker/Dockerfile.jupyter << 'EOF'
# 使用 Jupyter 基础镜像
FROM jupyter/scipy-notebook:latest

# 安装 Java 和 Maven
USER root
RUN apt-get update && \
    apt-get install -y openjdk-17-jdk maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /home/jovyan/work

# 复制分析脚本
COPY scripts /home/jovyan/scripts
RUN chown -R jovyan:users /home/jovyan/scripts

# 切换回非特权用户
USER jovyan
EOF
fi

if [ ! -f docker/Dockerfile.builder ]; then
    echo "创建 Builder Dockerfile..."
    cat > docker/Dockerfile.builder << 'EOF'
# 使用 Maven 基础镜像 - 修复镜像标签问题
FROM maven:3.8.6-jdk-17

# 安装 Python 和 Git
RUN apt-get update && \
    apt-get install -y python3 python3-pip git && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 复制分析脚本
COPY scripts /app/scripts

# 安装 Python 依赖
RUN pip install pandas matplotlib scipy

# 设置入口点
ENTRYPOINT ["/bin/bash"]
EOF
fi

# 创建/更新 docker-compose.yml
echo "创建/更新 docker-compose.yml..."
cat > docker-compose.yml << 'EOF'
version: '3.8'

services:
  jupyter:
    build:
      context: .
      dockerfile: docker/Dockerfile.jupyter
    image: java-evolution-jupyter
    container_name: evolution-jupyter
    ports:
      - "8888:8888"
    volumes:
      - ./notebooks:/home/jovyan/work
      - ./data:/home/jovyan/data
      - ./scripts:/home/jovyan/scripts
    environment:
      JUPYTER_TOKEN: "evolution"
    command: start-notebook.sh --NotebookApp.notebook_dir=/home/jovyan/work

  builder:
    build:
      context: .
      dockerfile: docker/Dockerfile.builder
    image: java-evolution-builder
    container_name: evolution-builder
    volumes:
      - ./scripts:/app/scripts
      - ./data:/app/data
      - ./configs:/app/configs
    working_dir: /app
    stdin_open: true
    tty: true
EOF

# 构建镜像
echo "构建Docker镜像..."
docker-compose build

# 启动服务
echo "启动容器服务..."
docker-compose up -d

# 等待服务启动
echo "等待服务启动..."
sleep 15

# 检查容器状态
echo "容器状态:"
docker-compose ps

# 显示使用说明
echo ""
echo "=== 环境构建完成 ==="
echo "1. 进入构建容器: docker exec -it evolution-builder bash"
echo "2. Jupyter Notebook: http://localhost:8888 (密码: evolution)"
echo "3. 停止服务: docker-compose down"
echo "4. 查看日志: docker-compose logs -f"

# 复制示例配置文件
if [ ! -f configs/commons-csv-config.json ]; then
    echo "复制示例配置文件..."
    cat > configs/commons-csv-config.json << 'EOF'
{
  "project": "commons-csv",
  "repo_url": "https://github.com/apache/commons-csv.git",
  "target_commit": "088672f6f93dc5784e5e01478639706ac7ec41f9",
  "analysis_config": {
    "test_framework": "junit",
    "build_tool": "maven",
    "java_version": 8
  }
}
EOF
fi

echo "环境准备完成！"

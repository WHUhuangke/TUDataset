# 使用 Maven 基础镜像
FROM maven:3.8.6-openjdk-17

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

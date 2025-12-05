# 使用轻量级Alpine Linux基础镜像
FROM alpine:3.18

# 设置环境变量
ENV LANG=C.UTF-8 \
    JAVA_HOME=/usr/lib/jvm/default-jvm \
    PATH=$PATH:/usr/lib/jvm/default-jvm/bin

# 安装基础工具和依赖
RUN apk update && apk add --no-cache \
    bash \
    curl \
    wget \
    git \
    python3 \
    py3-pip \
    openjdk8 \
    openjdk11 \
    openjdk17 \
    openjdk21 \
    && ln -sf python3 /usr/bin/python \
    && rm -rf /var/cache/apk/*

# 设置Java版本管理（默认使用Java 17）
RUN ln -sf /usr/lib/jvm/java-17-openjdk /usr/lib/jvm/default-jvm

# 创建Java版本切换脚本
RUN cat > /usr/local/bin/switch-java << 'EOF'
#!/bin/bash
JAVA_VERSION=$1
case $JAVA_VERSION in
    8)
        ln -sf /usr/lib/jvm/java-8-openjdk /usr/lib/jvm/default-jvm
        ;;
    11)
        ln -sf /usr/lib/jvm/java-11-openjdk /usr/lib/jvm/default-jvm
        ;;
    17)
        ln -sf /usr/lib/jvm/java-17-openjdk /usr/lib/jvm/default-jvm
        ;;
    21)
        ln -sf /usr/lib/jvm/java-21-openjdk /usr/lib/jvm/default-jvm
        ;;
    *)
        echo "可用版本: 8, 11, 17, 21"
        echo "当前版本: $(java -version 2>&1 | head -1)"
        exit 1
        ;;
esac
echo "切换到 Java $JAVA_VERSION"
java -version
EOF

RUN chmod +x /usr/local/bin/switch-java

# 创建工作目录
WORKDIR /workspace

# 复制requirements文件（请确保构建上下文中有此文件）
COPY requirements.txt /tmp/requirements.txt

# 安装Python依赖
RUN pip3 install --no-cache-dir -r /tmp/requirements.txt \
    && rm /tmp/requirements.txt

# 设置挂载点
VOLUME ["/workspace"]

# 验证安装
RUN echo "Java版本:" && java -version && \
    echo "Python版本:" && python --version && \
    echo "Git版本:" && git --version

# 设置默认启动命令
CMD ["/bin/bash"]

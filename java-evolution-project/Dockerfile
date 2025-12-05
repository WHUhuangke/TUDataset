# 使用Ubuntu LTS基础镜像
FROM ubuntu:22.04

# 设置环境变量
ENV LANG=C.UTF-8 \
    DEBIAN_FRONTEND=noninteractive \
    MAVEN_VERSION=3.6.3 \
    MAVEN_HOME=/opt/maven

# 彻底更换APT源为阿里云镜像
RUN echo "deb http://mirrors.aliyun.com/ubuntu/ jammy main restricted universe multiverse" > /etc/apt/sources.list && \
    echo "deb http://mirrors.aliyun.com/ubuntu/ jammy-security main restricted universe multiverse" >> /etc/apt/sources.list && \
    echo "deb http://mirrors.aliyun.com/ubuntu/ jammy-updates main restricted universe multiverse" >> /etc/apt/sources.list && \
    echo "deb http://mirrors.aliyun.com/ubuntu/ jammy-backports main restricted universe multiverse" >> /etc/apt/sources.list

# 安装基础工具
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    apt-transport-https \
    wget \
    curl \
    git \
    python3 \
    python3-pip \
    python3-venv \
    && rm -rf /var/lib/apt/lists/*

# 安装多个Java版本
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-8-jdk \
    openjdk-11-jdk \
    openjdk-17-jdk \
    openjdk-21-jdk \
    && rm -rf /var/lib/apt/lists/*

# 安装 Maven 3.6.3
RUN wget https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz -P /tmp && \
    tar -xzf /tmp/apache-maven-${MAVEN_VERSION}-bin.tar.gz -C /opt && \
    mv /opt/apache-maven-${MAVEN_VERSION} ${MAVEN_HOME} && \
    ln -s ${MAVEN_HOME}/bin/mvn /usr/local/bin/mvn && \
    rm /tmp/apache-maven-${MAVEN_VERSION}-bin.tar.gz

# 设置环境变量
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# 创建Java版本切换脚本
RUN cat > /usr/local/bin/use-java << 'EOF'
#!/bin/bash
case $1 in
    8) JAVA_PATH="/usr/lib/jvm/java-8-openjdk-amd64" ;;
    11) JAVA_PATH="/usr/lib/jvm/java-11-openjdk-amd64" ;;
    17) JAVA_PATH="/usr/lib/jvm/java-17-openjdk-amd64" ;;
    21) JAVA_PATH="/usr/lib/jvm/java-21-openjdk-amd64" ;;
    *) echo "Usage: use-java {8|11|17|21}"; exit 1 ;;
esac
ln -sf $JAVA_PATH/bin/java /usr/bin/java
ln -sf $JAVA_PATH/bin/javac /usr/bin/javac
export JAVA_HOME=$JAVA_PATH
echo "Switched to Java $1"
java -version
EOF

RUN chmod +x /usr/local/bin/use-java

# 设置默认Java版本为17
RUN update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java

WORKDIR /workspace
COPY requirements.txt ./

# 配置pip使用国内源
RUN pip3 config set global.index-url https://mirrors.aliyun.com/pypi/simple/
RUN pip3 config set global.trusted-host mirrors.aliyun.com

# 安装Python依赖
RUN if [ -f requirements.txt ]; then pip3 install -r requirements.txt; fi

VOLUME ["/workspace"]
CMD ["/bin/bash"]

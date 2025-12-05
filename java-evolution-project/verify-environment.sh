#!/bin/bash
# verify-environment.sh

echo "验证Docker环境配置..."

# 检查容器运行状态
if docker ps | grep -q evolution-builder; then
    echo "✓ 主构建容器运行正常"
else
    echo "✗ 主构建容器未运行"
    exit 1
fi

# 检查Java环境
echo "检查Java环境..."
docker exec evolution-builder java -version

# 检查Maven环境
echo "检查Maven环境..."
docker exec evolution-builder mvn --version

# 检查Python环境
echo "检查Python环境..."
docker exec evolution-builder python3 --version
docker exec evolution-builder pip3 list | grep -E "(gitpython|jupyter)"

# 检查Git配置
echo "检查Git配置..."
docker exec evolution-builder git config --global user.email

# 检查目录挂载
echo "检查目录挂载..."
docker exec evolution-builder ls -la /workspace/

echo "环境验证完成！"

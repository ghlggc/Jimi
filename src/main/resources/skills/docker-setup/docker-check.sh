#!/bin/bash

echo "=========================================="
echo "  Docker 环境检查"
echo "=========================================="
echo ""

# 检查 Docker
if command -v docker &> /dev/null; then
    DOCKER_VERSION=$(docker --version)
    echo "✓ $DOCKER_VERSION"
    
    # 检查 Docker 服务状态
    if docker ps &> /dev/null; then
        echo "✓ Docker 服务运行中"
        
        # 显示运行中的容器
        RUNNING=$(docker ps --format '{{.Names}}' | wc -l | tr -d ' ')
        echo "✓ 运行中的容器: $RUNNING"
    else
        echo "⚠️  Docker 服务未运行"
    fi
else
    echo "❌ Docker 未安装"
    echo "   访问 https://www.docker.com/ 下载安装"
fi

echo ""

# 检查 docker-compose
if command -v docker-compose &> /dev/null; then
    COMPOSE_VERSION=$(docker-compose --version)
    echo "✓ $COMPOSE_VERSION"
elif docker compose version &> /dev/null; then
    COMPOSE_VERSION=$(docker compose version)
    echo "✓ $COMPOSE_VERSION"
else
    echo "ℹ️  Docker Compose 未安装"
fi

echo ""

# 检查 Dockerfile
if [ -f "Dockerfile" ]; then
    echo "✓ 发现 Dockerfile"
else
    echo "ℹ️  未发现 Dockerfile"
    echo ""
    echo "建议创建 Dockerfile："
    echo ""
    cat << 'EOF'
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF
fi

echo ""
echo "=========================================="

exit 0

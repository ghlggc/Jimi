#!/bin/bash

SCAN_TARGET="${SCAN_TARGET:-src/}"

echo "=========================================="
echo "  安全扫描"
echo "=========================================="
echo ""
echo "扫描目标: $SCAN_TARGET"
echo ""

# 检查目标目录是否存在
if [ ! -d "$SCAN_TARGET" ]; then
    echo "⚠️  目录不存在: $SCAN_TARGET"
    exit 0
fi

# 1. 扫描敏感信息泄露
echo "=========================================="
echo "  1. 敏感信息扫描"
echo "=========================================="
echo ""

SENSITIVE_PATTERNS=(
    "password\s*=\s*['\"][^'\"]+['\"]"
    "api[_-]?key\s*=\s*['\"][^'\"]+['\"]"
    "secret\s*=\s*['\"][^'\"]+['\"]"
    "token\s*=\s*['\"][^'\"]+['\"]"
    "private[_-]?key"
    "BEGIN RSA PRIVATE KEY"
    "BEGIN PRIVATE KEY"
)

FOUND_ISSUES=0

for pattern in "${SENSITIVE_PATTERNS[@]}"; do
    matches=$(grep -rni -E "$pattern" "$SCAN_TARGET" 2>/dev/null | grep -v ".git" || true)
    if [ -n "$matches" ]; then
        echo "⚠️  发现可能的敏感信息:"
        echo "$matches" | head -5
        echo ""
        FOUND_ISSUES=$((FOUND_ISSUES + 1))
    fi
done

if [ $FOUND_ISSUES -eq 0 ]; then
    echo "✓ 未发现明显的敏感信息泄露"
fi

echo ""

# 2. SQL 注入风险检测
echo "=========================================="
echo "  2. SQL 注入风险检测"
echo "=========================================="
echo ""

SQL_INJECTION_PATTERNS=(
    "String.*sql.*=.*\".*SELECT.*\+.*\""
    "executeQuery.*\+.*\)"
    "Statement.*execute\("
)

SQL_ISSUES=0

for pattern in "${SQL_INJECTION_PATTERNS[@]}"; do
    matches=$(grep -rni -E "$pattern" "$SCAN_TARGET" --include="*.java" 2>/dev/null || true)
    if [ -n "$matches" ]; then
        echo "⚠️  发现可能的 SQL 注入风险:"
        echo "$matches" | head -3
        echo ""
        SQL_ISSUES=$((SQL_ISSUES + 1))
    fi
done

if [ $SQL_ISSUES -eq 0 ]; then
    echo "✓ 未发现明显的 SQL 注入风险"
else
    echo "建议: 使用 PreparedStatement 或参数化查询"
fi

echo ""

# 3. 硬编码配置检查
echo "=========================================="
echo "  3. 硬编码配置检查"
echo "=========================================="
echo ""

HARDCODED_URLS=$(grep -rni -E "http://|https://" "$SCAN_TARGET" --include="*.java" 2>/dev/null | grep -v "^Binary" | head -5 || true)

if [ -n "$HARDCODED_URLS" ]; then
    echo "⚠️  发现硬编码的 URL:"
    echo "$HARDCODED_URLS"
    echo ""
    echo "建议: 将 URL 配置到 application.yml 或环境变量"
else
    echo "✓ 未发现硬编码的 URL"
fi

echo ""

# 4. 日志敏感信息检查
echo "=========================================="
echo "  4. 日志敏感信息检查"
echo "=========================================="
echo ""

LOG_ISSUES=$(grep -rni -E "log\.(info|debug|warn).*password|log\.(info|debug|warn).*token" "$SCAN_TARGET" --include="*.java" 2>/dev/null | head -3 || true)

if [ -n "$LOG_ISSUES" ]; then
    echo "⚠️  日志可能包含敏感信息:"
    echo "$LOG_ISSUES"
    echo ""
    echo "建议: 避免在日志中记录密码、Token 等敏感信息"
else
    echo "✓ 日志使用看起来正常"
fi

echo ""

# 5. 依赖漏洞检查（如果是 Maven 项目）
if [ -f "pom.xml" ]; then
    echo "=========================================="
    echo "  5. Maven 依赖检查"
    echo "=========================================="
    echo ""
    
    if command -v mvn &> /dev/null; then
        echo "ℹ️  检测到 Maven 项目，建议运行:"
        echo "   mvn org.owasp:dependency-check-maven:check"
        echo ""
    else
        echo "⚠️  Maven 未安装，无法检查依赖漏洞"
        echo ""
    fi
fi

# 6. 安全响应头检查
echo "=========================================="
echo "  6. 安全响应头检查"
echo "=========================================="
echo ""

SECURITY_HEADERS=(
    "X-Frame-Options"
    "X-Content-Type-Options"
    "Content-Security-Policy"
    "Strict-Transport-Security"
)

HEADER_ISSUES=0

for header in "${SECURITY_HEADERS[@]}"; do
    if ! grep -rqi "$header" "$SCAN_TARGET" --include="*.java" --include="*.yml" 2>/dev/null; then
        echo "⚠️  未发现 $header 配置"
        HEADER_ISSUES=$((HEADER_ISSUES + 1))
    fi
done

if [ $HEADER_ISSUES -eq 0 ]; then
    echo "✓ 安全响应头配置完善"
else
    echo ""
    echo "建议: 配置安全响应头防止 XSS、点击劫持等攻击"
fi

echo ""

# 7. 总结
echo "=========================================="
echo "  扫描总结"
echo "=========================================="
echo ""

TOTAL_ISSUES=$((FOUND_ISSUES + SQL_ISSUES + HEADER_ISSUES))

if [ $TOTAL_ISSUES -eq 0 ]; then
    echo "✓ 扫描完成，未发现明显的安全问题"
else
    echo "⚠️  发现 $TOTAL_ISSUES 类潜在安全问题"
    echo ""
    echo "建议:"
    echo "1. 审查上述问题并及时修复"
    echo "2. 使用专业工具进行深度扫描"
    echo "3. 定期进行安全审计和渗透测试"
fi

echo ""
echo "=========================================="
echo "  推荐工具"
echo "=========================================="
echo ""
echo "• OWASP Dependency-Check: 依赖漏洞扫描"
echo "• SonarQube: 代码质量和安全"
echo "• SpotBugs: Java 代码缺陷检测"
echo "• git-secrets: 防止密钥提交"
echo "• OWASP ZAP: Web 应用安全测试"
echo ""

exit 0

#!/bin/bash

echo "=========================================="
echo "  Git 环境检查"
echo "=========================================="
echo ""

# 检查 Git 是否安装
if ! command -v git &> /dev/null; then
    echo "❌ Git 未安装"
    echo "   请访问 https://git-scm.com/ 下载安装"
    exit 1
fi

# 显示 Git 版本
GIT_VERSION=$(git --version)
echo "✓ Git 版本: $GIT_VERSION"
echo ""

# 检查是否在 Git 仓库中
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "ℹ️  当前目录不是 Git 仓库"
    echo "   运行 'git init' 初始化仓库"
    exit 0
fi

# 显示仓库信息
REPO_ROOT=$(git rev-parse --show-toplevel)
CURRENT_BRANCH=$(git branch --show-current)
echo "✓ 仓库路径: $REPO_ROOT"
echo "✓ 当前分支: $CURRENT_BRANCH"
echo ""

# 检查 Git 配置
echo "=========================================="
echo "  Git 配置检查"
echo "=========================================="
echo ""

USER_NAME=$(git config user.name)
USER_EMAIL=$(git config user.email)

if [ -z "$USER_NAME" ]; then
    echo "⚠️  未配置用户名"
    echo "   运行: git config --global user.name \"Your Name\""
else
    echo "✓ 用户名: $USER_NAME"
fi

if [ -z "$USER_EMAIL" ]; then
    echo "⚠️  未配置邮箱"
    echo "   运行: git config --global user.email \"your@email.com\""
else
    echo "✓ 邮箱: $USER_EMAIL"
fi
echo ""

# 显示仓库状态
echo "=========================================="
echo "  仓库状态"
echo "=========================================="
echo ""

# 统计信息
TOTAL_COMMITS=$(git rev-list --count HEAD 2>/dev/null || echo "0")
MODIFIED_FILES=$(git status --short | wc -l | tr -d ' ')

echo "✓ 总提交数: $TOTAL_COMMITS"
echo "✓ 未提交变更: $MODIFIED_FILES 个文件"
echo ""

# 显示最近 5 条提交记录
if [ "$TOTAL_COMMITS" -gt 0 ]; then
    echo "=========================================="
    echo "  最近 5 条提交记录"
    echo "=========================================="
    echo ""
    git log --oneline --decorate --graph -5
    echo ""
    
    # 检查提交信息格式
    echo "=========================================="
    echo "  提交规范检查"
    echo "=========================================="
    echo ""
    
    NON_CONVENTIONAL=0
    while IFS= read -r commit_msg; do
        if ! echo "$commit_msg" | grep -qE "^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\(.+\))?: .+"; then
            NON_CONVENTIONAL=$((NON_CONVENTIONAL + 1))
        fi
    done < <(git log --format=%s -5)
    
    if [ $NON_CONVENTIONAL -eq 0 ]; then
        echo "✓ 最近 5 条提交符合 Conventional Commits 规范"
    else
        echo "⚠️  最近 5 条提交中有 $NON_CONVENTIONAL 条不符合规范"
        echo "   建议使用格式: <type>(<scope>): <subject>"
        echo "   示例: feat(api): 添加用户注册接口"
    fi
fi

echo ""
echo "=========================================="
echo "  建议"
echo "=========================================="
echo ""
echo "1. 使用 Conventional Commits 规范编写提交信息"
echo "2. 每次提交前运行: git pull --rebase"
echo "3. 配置 commit message hook 自动验证格式"
echo "4. 安装 commitizen 工具辅助编写规范的提交信息"
echo ""
echo "=========================================="

exit 0

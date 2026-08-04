#!/bin/bash

# Git 提交并推送到所有 remote 的脚本
# 使用方法: ./git-commit-push-all.sh "提交信息"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查是否提供了提交信息
if [ -z "$1" ]; then
    echo -e "${YELLOW}用法: $0 \"提交信息\"${NC}"
    echo -e "${YELLOW}示例: $0 \"feat: 添加知识库管理功能\"${NC}"
    exit 1
fi

COMMIT_MESSAGE="$1"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Git 提交并推送到所有 Remote${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 1. 检查是否有未提交的更改
echo -e "${YELLOW}[1/4] 检查文件状态...${NC}"
git status --short
echo ""

# 2. 添加所有更改并提交
echo -e "${YELLOW}[2/4] 添加文件并提交...${NC}"
git add -A
git commit -m "$COMMIT_MESSAGE"

if [ $? -ne 0 ]; then
    echo -e "${RED}提交失败！${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 提交成功${NC}"
echo ""

# 3. 获取所有 remote
echo -e "${YELLOW}[3/4] 获取所有 Remote...${NC}"
REMOTES=$(git remote)

if [ -z "$REMOTES" ]; then
    echo -e "${RED}错误: 没有找到任何 Remote${NC}"
    exit 1
fi

echo -e "找到以下 Remote:"
echo "$REMOTES" | while read remote; do
    URL=$(git remote get-url "$remote")
    echo -e "  - ${GREEN}$remote${NC}: $URL"
done
echo ""

# 4. 推送到所有 remote
echo -e "${YELLOW}[4/4] 推送到所有 Remote...${NC}"
SUCCESS_COUNT=0
FAIL_COUNT=0

for remote in $REMOTES; do
    echo -e "\n${GREEN}推送到 $remote...${NC}"
    git push "$remote"
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ $remote 推送成功${NC}"
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        echo -e "${RED}✗ $remote 推送失败${NC}"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
done

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  推送完成${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "成功: ${GREEN}$SUCCESS_COUNT${NC} 个 Remote"
echo -e "失败: ${RED}$FAIL_COUNT${NC} 个 Remote"
echo ""

if [ $FAIL_COUNT -gt 0 ]; then
    exit 1
fi
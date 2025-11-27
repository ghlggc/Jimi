# Skill 脚本执行功能使用指南

## 概述

Jimi 的 Skill 系统现在支持在激活时自动执行脚本，可用于环境初始化、依赖安装、配置检查等场景。

## 功能特性

- ✅ **多脚本类型支持**：bash, python, node, ruby 等
- ✅ **自动类型推断**：根据文件扩展名自动识别脚本类型
- ✅ **环境变量注入**：支持为脚本提供自定义环境变量
- ✅ **超时控制**：可配置脚本执行超时时间
- ✅ **错误容错**：脚本执行失败不影响 Skill 内容注入
- ✅ **详细日志**：记录脚本执行过程和结果

## 配置方式

### 1. Skill 文件配置

在 `SKILL.md` 的 YAML Front Matter 中添加脚本相关字段：

```yaml
---
name: my-skill
description: 带脚本的 Skill 示例
version: 1.0.0
category: setup
triggers:
  - environment setup
  - 环境初始化

# 脚本配置（新增字段）
scriptPath: setup.sh              # 脚本文件路径（相对于 Skill 目录）
scriptType: bash                  # 脚本类型（可选，自动推断）
autoExecute: true                 # 是否自动执行（默认 true）
scriptTimeout: 30                 # 超时时间（秒，0 表示使用全局配置）
scriptEnv:                        # 环境变量（可选）
  SKILL_NAME: my-skill
  CUSTOM_VAR: value
---

# Skill 内容
...
```

### 2. 全局配置

在 `application.yml` 中配置脚本执行行为：

```yaml
jimi:
  skill:
    script-execution:
      enabled: true              # 是否启用脚本执行
      timeout: 60                # 默认超时时间（秒）
      require-approval: false    # 是否需要审批（暂未实现）
```

## 使用示例

### 示例 1：环境检查脚本

**目录结构：**
```
skills/env-check/
├── SKILL.md
└── check.sh
```

**SKILL.md:**
```yaml
---
name: env-check
description: 环境检查脚本
triggers:
  - 环境检查
  - check environment
scriptPath: check.sh
scriptType: bash
scriptTimeout: 10
---

# 环境检查技能包

执行环境检查，确保必要的工具已安装。
```

**check.sh:**
```bash
#!/bin/bash
echo "检查 Java 环境..."
java -version || echo "警告：Java 未安装"

echo "检查 Maven 环境..."
mvn -version || echo "警告：Maven 未安装"

echo "环境检查完成"
```

### 示例 2：Python 依赖安装

**SKILL.md:**
```yaml
---
name: python-setup
description: Python 项目依赖安装
triggers:
  - python setup
  - 安装依赖
scriptPath: install.py
scriptType: python
scriptTimeout: 120
scriptEnv:
  PIP_INDEX_URL: https://pypi.tuna.tsinghua.edu.cn/simple
---

# Python 环境配置

自动安装项目依赖。
```

**install.py:**
```python
#!/usr/bin/env python3
import os
import subprocess

print("安装 Python 依赖...")
subprocess.run(["pip", "install", "-r", "requirements.txt"], check=True)
print("依赖安装完成")
```

## 脚本类型支持

| 脚本类型 | 执行器 | 文件扩展名 |
|---------|--------|-----------|
| bash    | /bin/bash | .sh, .bash |
| sh      | /bin/sh   | .sh |
| python  | python3   | .py |
| node    | node      | .js |
| ruby    | ruby      | .rb |
| perl    | perl      | .pl |

## 注意事项

1. **脚本路径**：
   - 相对路径相对于 Skill 目录
   - 支持绝对路径（不推荐）

2. **权限**：
   - 确保脚本文件有执行权限（`chmod +x script.sh`）

3. **超时**：
   - 优先级：Skill 配置 > 全局配置 > 默认值（60秒）
   - 超时会强制终止脚本

4. **错误处理**：
   - 脚本执行失败不会阻止 Skill 内容注入
   - 错误信息会记录到日志

5. **安全性**：
   - 脚本执行使用 Bash 工具，继承其安全机制
   - 建议在生产环境启用审批机制（`require-approval: true`）

## 测试

使用内置的测试 Skill：

```bash
# 触发测试
请执行脚本测试

# 查看日志
tail -f logs/jimi.log
```

## 配置字段说明

### SkillSpec 新增字段

| 字段 | 类型 | 必需 | 默认值 | 说明 |
|-----|------|------|--------|------|
| scriptPath | String | 否 | null | 脚本文件路径 |
| scriptType | String | 否 | 自动推断 | 脚本类型 |
| autoExecute | boolean | 否 | true | 是否自动执行 |
| scriptEnv | Map<String,String> | 否 | null | 环境变量 |
| scriptTimeout | int | 否 | 0 | 超时时间（秒） |

### 配置优先级

1. Skill 级别配置（`SKILL.md` 中的字段）
2. 全局配置（`application.yml`）
3. 代码默认值

## 日志示例

```
INFO  SkillMatcher - Matched 1 skills: script-test
INFO  SkillProvider - Injecting 1 skills into context: script-test
INFO  SkillProvider - Executing scripts for 1 skills
INFO  SkillScriptExecutor - Executing script for skill 'script-test': /path/to/init.sh (timeout: 10s)
INFO  SkillScriptExecutor - Script execution completed successfully for skill 'script-test'
DEBUG SkillScriptExecutor - Script output:
==========================================
  Skill 脚本执行测试
==========================================

✓ 脚本已成功执行
✓ 当前时间: Wed Nov 26 10:00:00 CST 2025
...
```

## 扩展建议

未来可以增加的功能：

1. **审批机制**：脚本执行前用户确认
2. **脚本缓存**：避免重复执行相同脚本
3. **输出过滤**：敏感信息脱敏
4. **并发控制**：限制同时执行的脚本数量
5. **脚本模板**：预定义常用脚本模板

## 故障排查

### 脚本未执行

1. 检查 `script-execution.enabled` 是否为 true
2. 检查 `autoExecute` 是否为 true
3. 查看日志确认 Skill 是否被激活

### 脚本执行失败

1. 检查脚本路径是否正确
2. 检查脚本文件权限
3. 检查脚本类型配置
4. 查看完整日志获取错误详情

### 超时问题

1. 增加 `scriptTimeout` 值
2. 检查脚本是否有死循环
3. 优化脚本执行效率

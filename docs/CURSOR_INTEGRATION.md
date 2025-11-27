# Cursor ChatProvider 集成说明

## 概述

Cursor ChatProvider 是基于 `cursor-agent` CLI 实现的 LLM Provider,将 Cursor AI 作为新的 LLM 引擎集成到 Jimi 系统中。

## 功能特性

- ✅ 支持流式和非流式两种模式
- ✅ 支持多种 Cursor 模型 (auto, gpt-5, sonnet-4.5, opus-4.1 等)
- ✅ 自动解析思考内容 (thinking mode)
- ✅ Token 使用统计
- ✅ 完全集成到 Jimi 的 Agent/Engine/Session 体系
- ⚠️ **不支持工具调用** (cursor-cli 限制)

## 前置要求

需要安装 `cursor-agent` CLI:

```bash
# 访问 https://cursor.com/cli 安装
# 验证安装
cursor-agent --version
```

## 配置方式

### 1. 在 `~/.jimi/config.yml` 中添加配置

```yaml
providers:
  cursor:
    type: cursor
    # 可选配置
    custom_headers:
      # CLI 路径(默认使用 PATH 中的 cursor-agent)
      cursor_cli_path: "cursor-agent"
      # 配置目录(默认使用 ~/.jimi/cursor)
      cursor_config_dir: "~/.jimi/cursor"

models:
  # Cursor 自动模型选择
  cursor-auto:
    provider: cursor
    model: auto
    max_context_size: 128000
  
  # Cursor GPT-5
  cursor-gpt5:
    provider: cursor
    model: gpt-5
    max_context_size: 128000
  
  # Cursor Sonnet 4.5
  cursor-sonnet:
    provider: cursor
    model: sonnet-4.5
    max_context_size: 200000
  
  # Cursor Opus 4.1
  cursor-opus:
    provider: cursor
    model: opus-4.1
    max_context_size: 200000

# 设置默认模型
default_model: cursor-auto
```

### 2. 支持的模型

| 模型名称 | 说明 |
|---------|------|
| `auto` | 自动选择最佳模型 |
| `gpt-5` / `gpt-4` | GPT-5 模型 |
| `gpt-5-codex` | GPT-5 Codex 版本 |
| `sonnet-4.5` / `sonnet` | Claude Sonnet 4.5 |
| `opus-4.1` / `opus` | Claude Opus 4.1 |
| `cheetah` | Cursor Cheetah 模型 |
| `grok` | Grok 模型 |

## 使用示例

### 通过 Jimi CLI

```bash
# 使用默认模型
jimi "帮我写一个快速排序算法"

# 指定使用 Cursor Sonnet
jimi -m cursor-sonnet "分析这段代码的性能问题"
```

### 通过代码调用

```java
// 创建 Provider
LLMProviderConfig config = LLMProviderConfig.builder()
    .type(LLMProviderConfig.ProviderType.CURSOR)
    .build();

CursorChatProvider provider = new CursorChatProvider("auto", config, objectMapper);

// 非流式调用
List<Message> history = new ArrayList<>();
history.add(Message.user("你好"));

Mono<ChatCompletionResult> result = provider.generate(
    "You are a helpful assistant.",
    history,
    null
);

ChatCompletionResult completion = result.block();
System.out.println(completion.getMessage().getContent());

// 流式调用
Flux<ChatCompletionChunk> stream = provider.generateStream(
    "You are a helpful assistant.",
    history,
    null
);

stream.subscribe(chunk -> {
    if (chunk.getType() == ChunkType.CONTENT) {
        System.out.print(chunk.getContentDelta());
    }
});
```

## 实现细节

### 架构设计

```
CursorChatProvider (implements ChatProvider)
    ├── CursorProcessExecutor (进程执行器)
    │   ├── 命令构建
    │   ├── 进程管理
    │   └── 流式输出处理
    └── 格式转换
        ├── Message -> Markdown Prompt
        └── Stream JSON -> ChatCompletionChunk
```

### 消息格式转换

Jimi 的 `Message` 格式会被转换为 Markdown:

```markdown
# System

You are a helpful assistant.

# User

请帮我写一个函数

# Assistant

好的,我来帮你写...
```

### 流式输出解析

Cursor CLI 输出的 stream-json 格式:

```json
{"type": "thinking", "text": "让我思考一下..."}
{"type": "assistant", "content": {"text": "这是回答"}}
{"type": "result", "input_tokens": 100, "output_tokens": 50}
```

会被解析为 `ChatCompletionChunk`:
- `type: thinking` -> `isReasoning=true`
- `type: assistant` -> 正常内容
- `type: result` -> token 统计

## 限制说明

### 1. 不支持工具调用

Cursor CLI 不支持 function calling,如果传入 `tools` 参数会记录警告并忽略:

```
WARN: Cursor does not support tool calls, tools will be ignored
```

### 2. 仅支持文本消息

无法传递图片等多模态内容,只会提取文本部分。

### 3. 依赖本地环境

需要用户本地安装 `cursor-agent` CLI,如果未安装会在创建时记录警告。

## 测试

运行单元测试:

```bash
# 运行所有测试(需要安装 cursor-agent)
mvn test -Dtest=CursorChatProviderTest

# 只运行模型映射测试(不需要 CLI)
mvn test -Dtest=CursorChatProviderTest#testModelMapping
```

## 相关文件

- `LLMProviderConfig.java` - 添加 `CURSOR` 类型
- `CursorProcessExecutor.java` - 进程执行器
- `CursorChatProvider.java` - Provider 实现
- `LLMFactory.java` - 集成到工厂
- `CursorChatProviderTest.java` - 单元测试
- `cursor-test-config.yml` - 配置示例

## 参考

- Cursor CLI: https://cursor.com/cli
- CursorEngine.java: 原始实现参考

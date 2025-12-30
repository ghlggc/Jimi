package io.leavesfly.jimi.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jimi.config.info.LLMProviderConfig;
import io.leavesfly.jimi.llm.ChatCompletionChunk;
import io.leavesfly.jimi.llm.ChatCompletionResult;
import io.leavesfly.jimi.llm.ChatProvider;
import io.leavesfly.jimi.llm.message.Message;
import io.leavesfly.jimi.llm.message.TextPart;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cursor Chat Provider
 * 基于 cursor-agent CLI 实现的 LLM Provider
 */
@Slf4j
public class CursorChatProvider implements ChatProvider {

    private final String modelName;
    private final ObjectMapper objectMapper;
    private final CursorProcessExecutor processExecutor;
    private final String cliCommand;
    private final String configDir;

    private static final long DEFAULT_TIMEOUT = 1800000L; // 30分钟

    /**
     * 模型映射表
     */
    private static final Map<String, String> MODEL_MAPPING = new HashMap<>();

    static {
        MODEL_MAPPING.put("auto", "auto");
        MODEL_MAPPING.put("gpt-5", "gpt-5");
        MODEL_MAPPING.put("gpt-5-codex", "gpt-5-codex");
        MODEL_MAPPING.put("sonnet", "sonnet-4.5");
        MODEL_MAPPING.put("sonnet-4.5", "sonnet-4.5");
        MODEL_MAPPING.put("opus", "opus-4.1");
        MODEL_MAPPING.put("opus-4.1", "opus-4.1");
        MODEL_MAPPING.put("opus-4.5", "opus-4.5");
        MODEL_MAPPING.put("o1-preview", "sonnet-4.5-thinking");
        MODEL_MAPPING.put("composer", "composer-1");
    }

    public CursorChatProvider(
            String modelName,
            LLMProviderConfig providerConfig,
            ObjectMapper objectMapper
    ) {
        this.modelName = resolveModel(modelName);
        this.objectMapper = objectMapper;
        this.processExecutor = new CursorProcessExecutor();

        // 从配置或环境变量获取 CLI 路径
        this.cliCommand = providerConfig.getCustomHeaders() != null
                ? providerConfig.getCustomHeaders().getOrDefault("cursor_cli_path", "cursor-agent")
                : "cursor-agent";

        // 配置目录
        this.configDir = getCursorConfigDir(providerConfig);

        // 检查 CLI 是否已安装
        if (!CursorProcessExecutor.isCliInstalled(cliCommand)) {
            log.warn("Cursor CLI ({}) not found. Please install from https://cursor.com/cli", cliCommand);
        }

        log.info("Created Cursor ChatProvider: model={}, cli={}", this.modelName, cliCommand);
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public Mono<ChatCompletionResult> generate(
            String systemPrompt,
            List<Message> history,
            List<Object> tools
    ) {
        return Mono.deferContextual(ctx -> {
            // 检查工具调用
            if (tools != null && !tools.isEmpty()) {
                log.warn("Cursor does not support tool calls, tools will be ignored");
            }

            // 构建 prompt
            String prompt = buildPrompt(systemPrompt, history);

            // 从 Reactor Context 获取工作目录，如果没有则使用当前目录
            String workingDir = ctx.getOrDefault("workDir", System.getProperty("user.dir")).toString();

            // 构建命令
            List<String> command = buildCommand(modelName, workingDir, prompt);

            // 执行命令
            return Mono.fromCallable(() -> {
                StringBuilder fullOutput = new StringBuilder();
                AtomicReference<ChatCompletionResult.Usage> usageRef = new AtomicReference<>();

                CursorProcessExecutor.ExecutionResult result = processExecutor.execute(
                        command,
                        null,  // prompt 已经在命令行参数中，不需要通过 stdin 传递
                        null,
                        line -> {
                            // 解析每一行
                            ParsedLine parsed = parseStreamJson(line);
                            if (parsed.content != null) {
                                fullOutput.append(parsed.content);
                            }
                            if (parsed.usage != null) {
                                usageRef.set(parsed.usage);
                            }
                        },
                        DEFAULT_TIMEOUT
                );

                if (result.getExitCode() != 0) {
                    throw new RuntimeException("Cursor CLI failed: " + result.getStderr());
                }

                return ChatCompletionResult.builder()
                        .message(Message.assistant(fullOutput.toString()))
                        .usage(usageRef.get())
                        .build();
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }

    @Override
    public Flux<ChatCompletionChunk> generateStream(
            String systemPrompt,
            List<Message> history,
            List<Object> tools
    ) {
        return Flux.deferContextual(ctx -> {
            // 从 Reactor Context 获取工作目录,如果没有则使用当前目录
            Object workDirObj = ctx.getOrDefault("workDir", System.getProperty("user.dir"));
            String workingDir = workDirObj instanceof String ? (String) workDirObj : workDirObj.toString();

            return Flux.<ChatCompletionChunk>create(sink -> {
                try {
                    // 检查工具调用
                    if (tools != null && !tools.isEmpty()) {
                        log.warn("Cursor does not support tool calls, tools will be ignored");
                    }

                    // 构建 prompt
                    String prompt = buildPrompt(systemPrompt, history);

                    // 构建命令
                    List<String> command = buildCommand(modelName, workingDir, prompt);

                    // 执行命令
                    processExecutor.execute(
                            command,
                            null,  // prompt 已经在命令行参数中，不需要通过 stdin 传递
                            null,
                            line -> {
                                // 解析每一行并发送
                                ParsedLine parsed = parseStreamJson(line);
                                if (parsed.chunk != null) {
                                    sink.next(parsed.chunk);
                                }
                            },
                            DEFAULT_TIMEOUT
                    );

                    // 发送完成信号
                    sink.next(ChatCompletionChunk.builder()
                            .type(ChatCompletionChunk.ChunkType.DONE)
                            .build());

                    sink.complete();

                } catch (Exception e) {
                    log.error("Cursor streaming failed", e);
                    sink.error(e);
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }

    /**
     * 构建 Cursor prompt
     * 将 Jimi 消息格式转换为 Markdown 格式
     */
    private String buildPrompt(String systemPrompt, List<Message> history) {
        StringBuilder prompt = new StringBuilder();

        // 添加系统提示词
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            prompt.append("# System\n\n").append(systemPrompt).append("\n\n");
        }

        // 添加历史消息
        for (Message msg : history) {
            String role = msg.getRole().getValue();
            String content = extractTextContent(msg);

            if (content == null || content.isEmpty()) {
                continue;
            }

            // 跳过工具相关消息
            if ("tool".equals(role)) {
                continue;
            }

            if ("user".equals(role)) {
                prompt.append("# User\n\n").append(content).append("\n\n");
            } else if ("assistant".equals(role)) {
                prompt.append("# Assistant\n\n").append(content).append("\n\n");
            }
        }

        String result = prompt.toString();
        log.debug("Built prompt with {} characters", result.length());
//        if (log.isTraceEnabled()) {
//            log.trace("Prompt content:\n{}", result);
//        }
        return result;
    }

    /**
     * 提取消息的文本内容
     */
    private String extractTextContent(Message msg) {
        Object content = msg.getContent();
        if (content instanceof String) {
            return (String) content;
        } else if (content instanceof List) {
            // 只提取文本部分,忽略图片等
            StringBuilder sb = new StringBuilder();
            for (Object part : (List<?>) content) {
                if (part instanceof TextPart) {
                    sb.append(((TextPart) part).getText());
                } else if (part instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) part;
                    if ("text".equals(map.get("type"))) {
                        sb.append(map.get("text"));
                    }
                }
            }
            return sb.toString();
        }
        return null;
    }

    /**
     * 构建命令参数
     */
    private List<String> buildCommand(String model, String workingDir, String prompt) {
        List<String> command = new ArrayList<>();
        command.add(cliCommand);

        // 基础参数
        command.add("-p");  // print mode
        command.add("--force");
        command.add("--output-format");
        command.add("stream-json");

        // 添加模型参数
        if (model != null && !model.isEmpty() && !"auto".equals(model)) {
            command.add("--model");
            command.add(model);
        }

        // 添加工作目录参数
        if (workingDir != null && !workingDir.isEmpty()) {
            command.add("--workspace");
            command.add(workingDir);
        }

        // 添加 prompt 作为命令行参数
        if (prompt != null && !prompt.isEmpty()) {
            command.add(prompt);
        }

        return command;
    }

    /**
     * 解析流式 JSON 输出
     */
    private ParsedLine parseStreamJson(String line) {
        if (line == null || line.trim().isEmpty()) {
            return new ParsedLine();
        }

        try {
            JsonNode json = objectMapper.readTree(line.trim());

            // 检查类型字段
            if (!json.has("type")) {
                return new ParsedLine();
            }

            String type = json.get("type").asText();
            log.debug("Parsing stream JSON type: {}", type);

            // 跳过 system 和 user 类型
            if ("system".equals(type) || "user".equals(type)) {
                return new ParsedLine();
            }

            // 处理助手内容 - cursor-agent 实际格式: {"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"..."}]}}
            if ("assistant".equals(type) && json.has("message")) {
                JsonNode message = json.get("message");
                if (message.has("content") && message.get("content").isArray()) {
                    JsonNode contentArray = message.get("content");
                    for (JsonNode contentItem : contentArray) {
                        if (contentItem.has("type") && "text".equals(contentItem.get("type").asText())) {
                            String text = contentItem.get("text").asText();
                            if (text != null && !text.isEmpty()) {
                                log.debug("Extracted assistant text: {} chars", text.length());
                                ParsedLine result = new ParsedLine();
                                result.content = text;
                                result.chunk = ChatCompletionChunk.builder()
                                        .type(ChatCompletionChunk.ChunkType.CONTENT)
                                        .contentDelta(text)
                                        .isReasoning(false)
                                        .build();
                                return result;
                            }
                        }
                    }
                }
            }

            // 处理思考内容 (如果有的话)
            if ("thinking".equals(type)) {
                String text = extractTextField(json, "text");
                if (text != null && !text.isEmpty()) {
                    ParsedLine result = new ParsedLine();
                    result.content = text;
                    result.chunk = ChatCompletionChunk.builder()
                            .type(ChatCompletionChunk.ChunkType.CONTENT)
                            .contentDelta(text)
                            .isReasoning(true)
                            .build();
                    return result;
                }
            }

            // 处理结果（token 统计）- 格式: {"type":"result","subtype":"success",...}
            if ("result".equals(type)) {
                ParsedLine result = new ParsedLine();
                result.usage = extractTelemetry(json);
                log.debug("Extracted usage: {}", result.usage);
                return result;
            }

        } catch (Exception e) {
            log.debug("Failed to parse stream JSON: {}", line, e);
        }

        return new ParsedLine();
    }

    /**
     * 提取文本字段
     */
    private String extractTextField(JsonNode json, String key) {
        if (json.has(key) && !json.get(key).isNull()) {
            return json.get(key).asText();
        }
        return null;
    }

    /**
     * 提取遥测数据
     */
    private ChatCompletionResult.Usage extractTelemetry(JsonNode json) {
        try {
            int inputTokens = 0;
            int outputTokens = 0;

            if (json.has("input_tokens")) {
                inputTokens = json.get("input_tokens").asInt();
            }

            if (json.has("output_tokens")) {
                outputTokens = json.get("output_tokens").asInt();
            }

            if (inputTokens > 0 || outputTokens > 0) {
                return ChatCompletionResult.Usage.builder()
                        .promptTokens(inputTokens)
                        .completionTokens(outputTokens)
                        .totalTokens(inputTokens + outputTokens)
                        .build();
            }
        } catch (Exception e) {
            log.debug("Failed to extract telemetry", e);
        }

        return null;
    }

    /**
     * 解析模型名称
     */
    private String resolveModel(String model) {
        if (model == null || model.isEmpty()) {
            return "auto";
        }

        // 如果在映射表中，返回映射值
        if (MODEL_MAPPING.containsKey(model)) {
            return MODEL_MAPPING.get(model);
        }

        // 如果是有效的 Cursor 模型名，直接返回
        if (isValidCursorModel(model)) {
            return model;
        }

        // 默认使用 auto
        return "auto";
    }

    /**
     * 检查是否是有效的 Cursor 模型名
     */
    private boolean isValidCursorModel(String model) {
        String[] validModels = {"auto", "cheetah", "sonnet-4.5", "sonnet-4.5-thinking",
                "gpt-5", "gpt-5-codex", "opus-4.1", "grok"};
        for (String validModel : validModels) {
            if (validModel.equals(model)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取 Cursor 配置目录
     */
    private String getCursorConfigDir(LLMProviderConfig providerConfig) {
        // 优先使用配置中的路径
        if (providerConfig.getCustomHeaders() != null) {
            String configPath = providerConfig.getCustomHeaders().get("cursor_config_dir");
            if (configPath != null && !configPath.isEmpty()) {
                return configPath;
            }
        }

        // 使用环境变量
        String envConfigDir = System.getenv("CURSOR_CONFIG_DIR");
        if (envConfigDir != null && !envConfigDir.isEmpty()) {
            return envConfigDir;
        }

        // 默认使用 .jimi/cursor
        String userHome = System.getProperty("user.home");
        Path defaultPath = Paths.get(userHome, ".jimi", "cursor");

        try {
            Files.createDirectories(defaultPath);
            return defaultPath.toString();
        } catch (Exception e) {
            log.warn("Failed to create Cursor config directory: {}", defaultPath, e);
            return null;
        }
    }

    /**
     * 解析结果辅助类
     */
    private static class ParsedLine {
        String content;
        ChatCompletionChunk chunk;
        ChatCompletionResult.Usage usage;
    }
}

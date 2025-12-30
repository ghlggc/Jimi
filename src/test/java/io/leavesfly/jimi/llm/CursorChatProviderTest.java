package io.leavesfly.jimi.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jimi.config.info.LLMProviderConfig;
import io.leavesfly.jimi.llm.message.Message;
import io.leavesfly.jimi.llm.provider.CursorChatProvider;
import io.leavesfly.jimi.llm.provider.CursorProcessExecutor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cursor ChatProvider 测试
 * 注意：需要安装 cursor-agent CLI 才能运行
 */
@Slf4j
public class CursorChatProviderTest {

    private CursorChatProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();

        LLMProviderConfig config = LLMProviderConfig.builder()
                .type(LLMProviderConfig.ProviderType.CURSOR)
                .build();

        provider = new CursorChatProvider("auto", config, objectMapper);
    }

    /**
     * 检查 CLI 是否安装
     */
    static boolean isCursorCliInstalled() {
        return CursorProcessExecutor.isCliInstalled("cursor-agent");
    }

    @Test
    @EnabledIf("isCursorCliInstalled")
    public void testGenerate() {
        log.info("Testing Cursor generate (non-streaming)...");

        List<Message> history = new ArrayList<>();
        history.add(Message.user("What is 2+2? Please answer briefly."));

        Mono<ChatCompletionResult> result = provider.generate(
                "You are a helpful assistant.",
                history,
                null
        );

        ChatCompletionResult completion = result.block();

        log.info("Result: {}", completion.getMessage().getContent());
        log.info("Usage: {}", completion.getUsage());

        assert completion.getMessage() != null;
        assert completion.getMessage().getContent() != null;
    }

    @Test
    @EnabledIf("isCursorCliInstalled")
    public void testGenerateStream() {
        log.info("Testing Cursor generate (streaming)...");

        List<Message> history = new ArrayList<>();
        history.add(Message.user("Count from 1 to 5."));

        Flux<ChatCompletionChunk> stream = provider.generateStream(
                "You are a helpful assistant.",
                history,
                null
        );

        StringBuilder output = new StringBuilder();
        AtomicInteger chunkCount = new AtomicInteger(0);

        stream.doOnNext(chunk -> {
            chunkCount.incrementAndGet();
            if (chunk.getType() == ChatCompletionChunk.ChunkType.CONTENT) {
                output.append(chunk.getContentDelta());
                log.info("Chunk {}: {} (reasoning={})",
                        chunkCount.get(),
                        chunk.getContentDelta(),
                        chunk.isReasoning());
            } else if (chunk.getType() == ChatCompletionChunk.ChunkType.DONE) {
                log.info("Stream completed. Total chunks: {}", chunkCount.get());
                if (chunk.getUsage() != null) {
                    log.info("Token usage: {}", chunk.getUsage());
                }
            }
        }).blockLast();

        log.info("Full output: {}", output);

        assert output.length() > 0;
        assert chunkCount.get() > 0;
    }

    @Test
    @EnabledIf("isCursorCliInstalled")
    public void testWithToolsWarning() {
        log.info("Testing Cursor with tools (should log warning)...");

        List<Message> history = new ArrayList<>();
        history.add(Message.user("Hello"));

        List<Object> tools = new ArrayList<>();
        tools.add(new Object()); // Dummy tool

        // 应该记录警告但不抛异常
        Mono<ChatCompletionResult> result = provider.generate(
                "You are a helpful assistant.",
                history,
                tools
        );

        ChatCompletionResult completion = result.block();
        assert completion != null;
    }

    @Test
    public void testModelMapping() {
        log.info("Testing model name mapping...");

        LLMProviderConfig config = LLMProviderConfig.builder()
                .type(LLMProviderConfig.ProviderType.CURSOR)
                .build();

        // 测试不同模型名称
        CursorChatProvider autoProvider = new CursorChatProvider("auto", config, objectMapper);
        assert "auto".equals(autoProvider.getModelName());

        CursorChatProvider gpt4Provider = new CursorChatProvider("gpt-4", config, objectMapper);
        assert "gpt-5".equals(gpt4Provider.getModelName());

        CursorChatProvider sonnetProvider = new CursorChatProvider("sonnet", config, objectMapper);
        assert "sonnet-4.5".equals(sonnetProvider.getModelName());
    }
}

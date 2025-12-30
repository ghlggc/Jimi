package io.leavesfly.jimi.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jimi.config.JimiConfiguration;
import io.leavesfly.jimi.config.JimiConfig;
import io.leavesfly.jimi.llm.ChatCompletionChunk;
import io.leavesfly.jimi.llm.ChatCompletionResult;
import io.leavesfly.jimi.llm.message.Message;
import io.leavesfly.jimi.llm.provider.OpenAICompatibleChatProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MiniMax ChatProvider 测试
 * 验证MiniMax模型的基础调用功能
 */
@Slf4j
public class TestMiniMaxChatProvider {
    
    @Test
    public void testStreamingResponse() {
        ObjectMapper objectMapper = new ObjectMapper();
        JimiConfig jimiConfig = JimiConfiguration.loadConfig(objectMapper, null);
        
        // 检查MiniMax配置是否存在
        if (!jimiConfig.getProviders().containsKey("minimax")) {
            log.warn("MiniMax provider not configured, skipping test");
            return;
        }
        
        OpenAICompatibleChatProvider provider = new OpenAICompatibleChatProvider(
                "MiniMax-M2.1",
                jimiConfig.getProviders().get("minimax"),
                objectMapper,
                "MiniMax"
        );

        log.info("Testing MiniMax streaming response...");
        
        AtomicBoolean reasoning = new AtomicBoolean(false);
        provider.generateStream(
                        "你是一个专业的AI编程助手",
                        Collections.singletonList(Message.user("你好,请简单介绍一下你的编程能力")),
                        null
                )
                .doOnEach(chunk -> {
                    ChatCompletionChunk rt = chunk.get();
                    if (rt != null && rt.getType() == ChatCompletionChunk.ChunkType.CONTENT) {
                        if (rt.isReasoning() && reasoning.compareAndSet(false, true)) {
                            System.out.println("<think>");
                        }
                        if (!rt.isReasoning() && reasoning.compareAndSet(true, false)) {
                            System.out.printf("%n</think>%n");
                        }
                        System.out.print(Objects.requireNonNull(rt).getContentDelta());
                    }
                })
                .collectList()
                .block();

        System.out.println();
        log.info("MiniMax streaming test completed");
    }
    
    @Test
    public void testNonStreamingResponse() {
        ObjectMapper objectMapper = new ObjectMapper();
        JimiConfig jimiConfig = JimiConfiguration.loadConfig(objectMapper, null);
        
        // 检查MiniMax配置是否存在
        if (!jimiConfig.getProviders().containsKey("minimax")) {
            log.warn("MiniMax provider not configured, skipping test");
            return;
        }
        
        OpenAICompatibleChatProvider provider = new OpenAICompatibleChatProvider(
                "MiniMax-M2.1",
                jimiConfig.getProviders().get("minimax"),
                objectMapper,
                "MiniMax"
        );

        log.info("Testing MiniMax non-streaming response...");
        
        ChatCompletionResult result = provider.generate(
                "你是一个智能AI助手",
                Collections.singletonList(Message.user("2+3等于多少?")),
                null
        ).block();
        
        System.out.println("Response: " + result.getMessage().getTextContent());
        System.out.println("Usage: " + result.getUsage());
        
        log.info("MiniMax non-streaming test completed");
    }
    
    @Test
    public void testCodeGeneration() {
        ObjectMapper objectMapper = new ObjectMapper();
        JimiConfig jimiConfig = JimiConfiguration.loadConfig(objectMapper, null);
        
        // 检查MiniMax配置是否存在
        if (!jimiConfig.getProviders().containsKey("minimax")) {
            log.warn("MiniMax provider not configured, skipping test");
            return;
        }
        
        OpenAICompatibleChatProvider provider = new OpenAICompatibleChatProvider(
                "MiniMax-M2.1",
                jimiConfig.getProviders().get("minimax"),
                objectMapper,
                "MiniMax"
        );

        log.info("Testing MiniMax code generation...");
        
        ChatCompletionResult result = provider.generate(
                "你是一个专业的Java开发工程师",
                Collections.singletonList(Message.user("写一个Java快速排序算法的实现")),
                null
        ).block();
        
        System.out.println("Generated code:");
        System.out.println(result.getMessage().getTextContent());
        
        log.info("MiniMax code generation test completed");
    }
    
    @Test
    public void testLightningModel() {
        ObjectMapper objectMapper = new ObjectMapper();
        JimiConfig jimiConfig = JimiConfiguration.loadConfig(objectMapper, null);
        
        // 检查MiniMax配置是否存在
        if (!jimiConfig.getProviders().containsKey("minimax")) {
            log.warn("MiniMax provider not configured, skipping test");
            return;
        }
        
        OpenAICompatibleChatProvider provider = new OpenAICompatibleChatProvider(
                "MiniMax-M2.1-lightning",
                jimiConfig.getProviders().get("minimax"),
                objectMapper,
                "MiniMax"
        );

        log.info("Testing MiniMax Lightning model...");
        
        long startTime = System.currentTimeMillis();
        
        ChatCompletionResult result = provider.generate(
                "你是一个快速响应的AI助手",
                Collections.singletonList(Message.user("什么是敏捷开发?")),
                null
        ).block();
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("Response: " + result.getMessage().getTextContent());
        System.out.println("Response time: " + duration + "ms");
        
        log.info("MiniMax Lightning test completed in {}ms", duration);
    }
}

package io.leavesfly.jimi.core.engine.executor;

import io.leavesfly.jimi.core.compaction.Compaction;
import io.leavesfly.jimi.core.engine.EngineConstants;
import io.leavesfly.jimi.core.engine.context.Context;
import io.leavesfly.jimi.knowledge.memory.MemoryInjector;
import io.leavesfly.jimi.knowledge.retrieval.RetrievalPipeline;
import io.leavesfly.jimi.llm.LLM;
import io.leavesfly.jimi.llm.message.ContentPart;
import io.leavesfly.jimi.llm.message.Message;
import io.leavesfly.jimi.llm.message.MessageRole;
import io.leavesfly.jimi.tool.skill.SkillMatcher;
import io.leavesfly.jimi.tool.skill.SkillProvider;
import io.leavesfly.jimi.tool.skill.SkillSpec;
import io.leavesfly.jimi.wire.Wire;
import io.leavesfly.jimi.wire.message.CompactionBegin;
import io.leavesfly.jimi.wire.message.CompactionEnd;
import io.leavesfly.jimi.wire.message.SkillsActivated;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 上下文管理器
 * <p>
 * 职责：
 * - 上下文压缩检查和执行
 * - RAG 检索并注入上下文
 * - Skill 匹配和注入
 * - 长期记忆注入
 */
@Slf4j
public class ContextManager {

    private final Wire wire;
    private final RetrievalPipeline retrievalPipeline;
    private final SkillMatcher skillMatcher;
    private final SkillProvider skillProvider;
    private final MemoryInjector memoryInjector;

    /**
     * 基础构造函数
     */
    public ContextManager(Wire wire) {
        this(wire, null, null, null, null);
    }

    /**
     * 完整构造函数
     */
    public ContextManager(
            Wire wire,
            RetrievalPipeline retrievalPipeline,
            SkillMatcher skillMatcher,
            SkillProvider skillProvider,
            MemoryInjector memoryInjector
    ) {
        this.wire = wire;
        this.retrievalPipeline = retrievalPipeline;
        this.skillMatcher = skillMatcher;
        this.skillProvider = skillProvider;
        this.memoryInjector = memoryInjector;
    }

    /**
     * 检查并压缩上下文（如果需要）
     *
     * @param context    上下文
     * @param llm        LLM 实例
     * @param compaction 压缩器
     * @return 完成的 Mono
     */
    public Mono<Void> checkAndCompact(Context context, LLM llm, Compaction compaction) {
        return Mono.defer(() -> {
            if (llm == null || compaction == null) {
                return Mono.empty();
            }

            int currentTokens = context.getTokenCount();
            int maxContextSize = llm.getMaxContextSize();

            // 检查是否需要压缩（Token 数超过限制 - 预留 Token）
            if (currentTokens > maxContextSize - EngineConstants.RESERVED_TOKENS) {
                log.info("Context size ({} tokens) approaching limit ({} tokens), triggering compaction",
                        currentTokens, maxContextSize);

                // 发送压缩开始事件
                wire.send(new CompactionBegin());

                return compaction.compact(context.getHistory(), llm)
                        .flatMap(compactedMessages -> {
                            // 回退到检查点 0（保留系统提示词和初始检查点）
                            return context.revertTo(0)
                                    .then(Mono.defer(() -> {
                                        // 添加压缩后的消息
                                        return context.appendMessage(compactedMessages);
                                    }))
                                    .doOnSuccess(v -> {
                                        log.info("Context compacted successfully");
                                        wire.send(new CompactionEnd());
                                    })
                                    .doOnError(e -> {
                                        log.error("Context compaction failed", e);
                                        wire.send(new CompactionEnd());
                                    });
                        });
            }

            return Mono.empty();
        });
    }

    /**
     * 检索并注入上下文（RAG）
     *
     * @param context 上下文
     * @param runtime 运行时（用于获取工作目录等）
     * @param stepNo  当前步骤号
     * @return 完成的 Mono
     */
    public Mono<Void> retrieveAndInject(Context context, io.leavesfly.jimi.core.engine.runtime.Runtime runtime, int stepNo) {
        // 如果没有配置 RetrievalPipeline，直接跳过
        if (retrievalPipeline == null) {
            return Mono.empty();
        }

        // 只在第一步执行检索（基于用户输入）
        if (stepNo != 1) {
            return Mono.empty();
        }

        return retrievalPipeline.retrieveAndInject(context, runtime)
                .doOnNext(count -> {
                    if (count > 0) {
                        log.info("Retrieved and injected {} code chunks into context", count);
                    }
                })
                .doOnError(e -> {
                    log.warn("Retrieval failed, continuing without RAG: {}", e.getMessage());
                })
                .onErrorResume(e -> Mono.empty()) // 检索失败不影响主流程
                .then();
    }

    /**
     * 匹配和注入 Skills（如果启用）
     *
     * @param context 上下文
     * @param stepNo  当前步骤号
     * @return 完成的 Mono
     */
    public Mono<Void> matchAndInjectSkills(Context context, int stepNo) {
        // 如果没有配置 Skill 组件，直接跳过
        if (skillMatcher == null || skillProvider == null) {
            return Mono.empty();
        }

        // 只在第一步执行 Skill 匹配（基于用户输入）
        if (stepNo == 1) {
            return matchSkillsFromUserInput(context);
        }

        return Mono.empty();
    }

    /**
     * 从用户输入匹配 Skills
     */
    private Mono<Void> matchSkillsFromUserInput(Context context) {
        return Mono.defer(() -> {
            // 获取最近的用户消息
            List<Message> history = context.getHistory();
            if (history.isEmpty()) {
                return Mono.empty();
            }

            // 从最后一条用户消息中提取内容
            Message lastUserMessage = findLastUserMessage(history);
            if (lastUserMessage == null) {
                return Mono.empty();
            }

            // 提取内容部分
            List<ContentPart> contentParts = lastUserMessage.getContentParts();
            if (contentParts.isEmpty()) {
                return Mono.empty();
            }

            // 匹配 Skills
            List<SkillSpec> matchedSkills = skillMatcher.matchFromInput(contentParts);

            if (matchedSkills.isEmpty()) {
                log.debug("No skills matched from user input");
                return Mono.empty();
            }

            // 发送 Wire 消息
            wire.send(SkillsActivated.from(matchedSkills));

            // 注入 Skills
            return skillProvider.injectSkills(context, matchedSkills);
        });
    }

    /**
     * 查找最后一条用户消息
     */
    private Message findLastUserMessage(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message msg = history.get(i);
            if (msg.getRole() == MessageRole.USER) {
                return msg;
            }
        }
        return null;
    }

    /**
     * 注入长期记忆（如果启用）
     *
     * @param context   上下文
     * @param userQuery 用户查询
     * @return 完成的 Mono
     */
    public Mono<Void> injectLongTermMemories(Context context, String userQuery) {
        if (memoryInjector == null) {
            return Mono.empty();
        }

        return memoryInjector.injectMemories(context, userQuery)
                .onErrorResume(e -> {
                    log.warn("记忆注入失败，继续执行: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}

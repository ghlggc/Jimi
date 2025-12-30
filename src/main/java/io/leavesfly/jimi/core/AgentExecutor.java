package io.leavesfly.jimi.core;

import io.leavesfly.jimi.config.info.MemoryConfig;
import io.leavesfly.jimi.core.agent.Agent;
import io.leavesfly.jimi.core.compaction.Compaction;
import io.leavesfly.jimi.core.engine.MemoryComponents;
import io.leavesfly.jimi.core.engine.SkillComponents;
import io.leavesfly.jimi.core.engine.context.ActivePromptBuilder;
import io.leavesfly.jimi.core.engine.context.Context;
import io.leavesfly.jimi.core.engine.executor.*;
import io.leavesfly.jimi.core.engine.runtime.ParentContext;
import io.leavesfly.jimi.core.engine.runtime.Runtime;
import io.leavesfly.jimi.exception.MaxStepsReachedException;
import io.leavesfly.jimi.knowledge.memory.MemoryExtractor;
import io.leavesfly.jimi.knowledge.memory.MemoryInjector;
import io.leavesfly.jimi.knowledge.retrieval.RetrievalPipeline;
import io.leavesfly.jimi.llm.LLM;
import io.leavesfly.jimi.llm.message.ContentPart;
import io.leavesfly.jimi.llm.message.Message;
import io.leavesfly.jimi.llm.message.TextPart;
import io.leavesfly.jimi.tool.ToolRegistry;
import io.leavesfly.jimi.tool.skill.SkillMatcher;
import io.leavesfly.jimi.tool.skill.SkillProvider;
import io.leavesfly.jimi.wire.Wire;
import io.leavesfly.jimi.wire.message.*;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Agent 执行器
 * <p>
 * 职责：
 * - Agent 主循环调度
 * - 协调各组件完成执行流程
 * <p>
 * 设计原则：
 * - 单一职责：仅负责执行流程协调
 * - 组合优于继承：通过组合组件完成功能
 * - Builder 模式：简化构造过程
 */
@Slf4j
public class AgentExecutor {

    private static final int MAX_THINKING_STEPS = 5;

    // ==================== 核心依赖（必需） ====================
    private final Agent agent;
    private final Runtime runtime;
    private final Context context;
    private final Wire wire;
    private final ToolRegistry toolRegistry;
    private final Compaction compaction;

    // ==================== 拆分组件 ====================
    private final ExecutionState executionState;
    private final MemoryRecorder memoryRecorder;
    private final ResponseProcessor responseProcessor;
    private final ToolDispatcher toolDispatcher;
    private final ContextManager contextManager;

    // ==================== 可选配置 ====================
    private final boolean isSubagent;
    private final String agentName;
    private final ActivePromptBuilder promptBuilder;
    private final MemoryConfig memoryConfig;

    /**
     * 私有构造函数，通过 Builder 创建实例
     */
    private AgentExecutor(Builder builder) {
        // 必需参数
        this.agent = Objects.requireNonNull(builder.agent, "agent is required");
        this.runtime = Objects.requireNonNull(builder.runtime, "runtime is required");
        this.context = Objects.requireNonNull(builder.context, "context is required");
        this.wire = Objects.requireNonNull(builder.wire, "wire is required");
        this.toolRegistry = Objects.requireNonNull(builder.toolRegistry, "toolRegistry is required");
        this.compaction = Objects.requireNonNull(builder.compaction, "compaction is required");

        // 可选参数
        this.isSubagent = builder.isSubagent;
        this.agentName = agent.getName();
        this.promptBuilder = builder.promptBuilder;

        // 从组件中提取配置
        MemoryComponents memoryComponents = builder.memoryComponents;
        SkillComponents skillComponents = builder.skillComponents;

        this.memoryConfig = memoryComponents != null ? memoryComponents.getConfig() : null;
        MemoryExtractor memoryExtractor = memoryComponents != null ? memoryComponents.getExtractor() : null;
        MemoryInjector memoryInjector = memoryComponents != null ? memoryComponents.getInjector() : null;
        SkillMatcher skillMatcher = skillComponents != null ? skillComponents.getMatcher() : null;
        SkillProvider skillProvider = skillComponents != null ? skillComponents.getProvider() : null;

        // 初始化拆分组件
        this.executionState = new ExecutionState();
        this.memoryRecorder = new MemoryRecorder(memoryExtractor);
        this.responseProcessor = new ResponseProcessor(wire);
        this.toolDispatcher = new ToolDispatcher(
                toolRegistry, wire, memoryConfig, memoryExtractor, memoryRecorder, executionState);
        this.contextManager = new ContextManager(
                wire, builder.retrievalPipeline, skillMatcher, skillProvider, memoryInjector);

        // 订阅 Subagent 事件（ReCAP 记忆优化）
        if (memoryConfig != null && memoryConfig.isEnableRecap()) {
            subscribeToSubagentEvents();
        }
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * AgentExecutor Builder
     * <p>
     * 使用示例：
     * <pre>
     * AgentExecutor executor = AgentExecutor.builder()
     *     .agent(agent)
     *     .runtime(runtime)
     *     .context(context)
     *     .wire(wire)
     *     .toolRegistry(toolRegistry)
     *     .compaction(compaction)
     *     // 可选配置
     *     .skillComponents(SkillComponents.of(matcher, provider))
     *     .memoryComponents(MemoryComponents.of(config, injector, extractor))
     *     .build();
     * </pre>
     */
    public static class Builder {
        // 必需参数
        private Agent agent;
        private Runtime runtime;
        private Context context;
        private Wire wire;
        private ToolRegistry toolRegistry;
        private Compaction compaction;

        // 可选参数
        private boolean isSubagent = false;
        private SkillComponents skillComponents;
        private MemoryComponents memoryComponents;
        private RetrievalPipeline retrievalPipeline;
        private ActivePromptBuilder promptBuilder;

        private Builder() {
        }

        // ==================== 必需参数 ====================

        public Builder agent(Agent agent) {
            this.agent = agent;
            return this;
        }

        public Builder runtime(Runtime runtime) {
            this.runtime = runtime;
            return this;
        }

        public Builder context(Context context) {
            this.context = context;
            return this;
        }

        public Builder wire(Wire wire) {
            this.wire = wire;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        public Builder compaction(Compaction compaction) {
            this.compaction = compaction;
            return this;
        }

        // ==================== 可选参数 ====================

        public Builder isSubagent(boolean isSubagent) {
            this.isSubagent = isSubagent;
            return this;
        }

        /**
         * 设置 Skill 组件（合并了 SkillMatcher 和 SkillProvider）
         */
        public Builder skillComponents(SkillComponents skillComponents) {
            this.skillComponents = skillComponents;
            return this;
        }

        /**
         * 设置 Memory 组件（合并了 MemoryConfig、MemoryInjector、MemoryExtractor）
         */
        public Builder memoryComponents(MemoryComponents memoryComponents) {
            this.memoryComponents = memoryComponents;
            return this;
        }

        /**
         * 设置 RAG 检索管线
         */
        public Builder retrievalPipeline(RetrievalPipeline retrievalPipeline) {
            this.retrievalPipeline = retrievalPipeline;
            return this;
        }

        /**
         * 设置 ReCAP 提示构建器
         */
        public Builder promptBuilder(ActivePromptBuilder promptBuilder) {
            this.promptBuilder = promptBuilder;
            return this;
        }

        /**
         * 构建 AgentExecutor 实例
         */
        public AgentExecutor build() {
            return new AgentExecutor(this);
        }
    }

    // 复制构造函数（从另一个 AgentExecutor 复制）
    private AgentExecutor(AgentExecutor other) {
        this.agent = other.agent;
        this.runtime = other.runtime;
        this.context = other.context;
        this.wire = other.wire;
        this.toolRegistry = other.toolRegistry;
        this.compaction = other.compaction;
        this.isSubagent = other.isSubagent;
        this.agentName = other.agentName;
        this.promptBuilder = other.promptBuilder;
        this.memoryConfig = other.memoryConfig;
        this.executionState = other.executionState;
        this.memoryRecorder = other.memoryRecorder;
        this.responseProcessor = other.responseProcessor;
        this.toolDispatcher = other.toolDispatcher;
        this.contextManager = other.contextManager;
    }

    // ==================== 执行方法 ====================

    /**
     * 执行 Agent 任务
     *
     * @param userInput 用户输入
     * @return 执行完成的 Mono
     */
    public Mono<Void> execute(List<ContentPart> userInput) {
        return Mono.defer(() -> {
            // 初始化任务跟踪
            executionState.initializeTask();

            // 创建用户消息
            Message userMessage = Message.user(userInput);

            // 提取高层意图（首条用户消息，ReCAP 优化）
            if (memoryConfig != null && memoryConfig.isEnableRecap() && context.getHistory().isEmpty()) {
                String intent = memoryRecorder.extractHighLevelIntent(userInput);
                context.setHighLevelIntent(intent);
                log.info("提取高层意图: {}", intent);
            }

            // 估算用户输入的 Token 数
            int userInputTokens = responseProcessor.estimateTokensFromMessage(userMessage);
            int newTokenCount = context.getTokenCount() + userInputTokens;
            executionState.addTokens(userInputTokens);

            // 提取用户查询文本
            String userQuery = memoryRecorder.extractHighLevelIntent(userInput);
            executionState.setCurrentUserQuery(userQuery);

            // 创建检查点 0，添加用户消息，并更新 Token 计数
            return context.checkpoint(false)
                    .then(context.appendMessage(userMessage))
                    .then(context.updateTokenCount(newTokenCount))
                    .doOnSuccess(v -> log.debug("Added user input: {} tokens (total: {})",
                            userInputTokens, newTokenCount))
                    // 注入长期记忆
                    .then(contextManager.injectLongTermMemories(context, userQuery))
                    .then(agentLoop())
                    .doOnSuccess(v -> {
                        log.info("Agent execution completed");
                        memoryRecorder.recordTaskHistory(executionState, context, "success").subscribe();
                        executionState.incrementTasksCompleted();
                    })
                    .doOnError(e -> {
                        log.error("Agent execution failed", e);
                        memoryRecorder.recordTaskHistory(executionState, context, "failed").subscribe();
                        memoryRecorder.recordErrorPattern(e, "agent_execution",
                                executionState.getCurrentUserQuery()).subscribe();
                    });
        });
    }

    /**
     * Agent 主循环
     */
    private Mono<Void> agentLoop() {
        return Mono.defer(() -> agentLoopStep(1));
    }

    /**
     * Agent 循环步骤
     */
    private Mono<Void> agentLoopStep(int stepNo) {
        // 记录步数
        executionState.setStepsInTask(stepNo);

        // 获取并递增全局步数
        int globalStepNo = runtime.getSession().incrementAndGetGlobalStep();

        // 检查全局最大步数
        int maxSteps = runtime.getConfig().getLoopControl().getMaxStepsPerRun();
        if (globalStepNo > maxSteps) {
            return Mono.error(new MaxStepsReachedException(maxSteps));
        }

        // 发送步骤开始消息
        wire.send(new StepBegin(globalStepNo, isSubagent, agentName));

        log.debug("Agent '{}' local step {}, global step {}/{}",
                agentName != null ? agentName : "main", stepNo, globalStepNo, maxSteps);

        return Mono.defer(() -> {
            // 检查上下文是否超限，触发压缩
            return contextManager.checkAndCompact(context, runtime.getLlm(), compaction)
                    .then(context.checkpoint(false))
                    // RAG：检索并注入相关代码片段
                    .then(contextManager.retrieveAndInject(context, runtime, stepNo))
                    // 匹配和注入 Skills
                    .then(contextManager.matchAndInjectSkills(context, stepNo))
                    .then(step())
                    .flatMap(finished -> {
                        if (finished) {
                            log.info("Agent '{}' loop finished at local step {}, global step {}",
                                    agentName != null ? agentName : "main", stepNo, globalStepNo);
                            return Mono.empty();
                        } else {
                            return agentLoopStep(stepNo + 1);
                        }
                    })
                    .then()
                    .onErrorResume(e -> {
                        log.error("Error in Agent '{}' at local step {}, global step {}",
                                agentName != null ? agentName : "main", stepNo, globalStepNo, e);
                        wire.send(new StepInterrupted());
                        return Mono.error(e);
                    });
        });
    }

    /**
     * 执行单步
     */
    private Mono<Boolean> step() {
        return Mono.defer(() -> {
            LLM llm = runtime.getLlm();
            List<Object> toolSchemas = new ArrayList<>(toolRegistry.getToolSchemas(agent.getTools()));

            // 构建增强的系统提示（如果启用 ReCAP）
            String systemPrompt = agent.getSystemPrompt();
            if (memoryConfig != null && memoryConfig.isEnableRecap() && promptBuilder != null) {
                systemPrompt = promptBuilder.buildEnhancedPrompt(
                        agent.getSystemPrompt(),
                        context.getHighLevelIntent(),
                        context.getRecentInsights(memoryConfig.getInsightsWindowSize()),
                        0
                );
                int estimatedTokens = systemPrompt.length() / 4;
                log.debug("使用 ReCAP 增强提示 (Token 估算: {} tokens)", estimatedTokens);
            }

            return llm.getChatProvider()
                    .generateStream(systemPrompt, context.getHistory(), toolSchemas)
                    .contextWrite(ctx -> ctx.put("workDir", runtime.getWorkDir()))
                    .reduce(new ResponseProcessor.StreamAccumulator(), responseProcessor::processStreamChunk)
                    .flatMap(acc -> responseProcessor.handleStreamCompletion(acc, context))
                    .flatMap(this::processAssistantMessage)
                    .onErrorResume(responseProcessor::handleLLMError);
        });
    }

    /**
     * 处理 assistant 消息
     */
    private Mono<Boolean> processAssistantMessage(Message assistantMessage) {
        if (assistantMessage.getToolCalls() == null || assistantMessage.getToolCalls().isEmpty()) {
            if (executionState.shouldForceComplete(MAX_THINKING_STEPS)) {
                log.warn("Agent has been thinking for {} consecutive steps without taking action, forcing completion",
                        MAX_THINKING_STEPS);
                return Mono.just(true);
            }

            log.info("No tool calls, finishing step (consecutive thinking steps: {})",
                    executionState.getConsecutiveNoToolCallSteps());
            return Mono.just(true);
        }

        executionState.resetNoToolCallCounter();

        log.info("准备执行 {} 个工具调用", assistantMessage.getToolCalls().size());

        return toolDispatcher.executeToolCalls(assistantMessage.getToolCalls(), context)
                .then(Mono.defer(() -> {
                    if (toolDispatcher.shouldTerminateLoop()) {
                        log.warn("检测到工具调用连续重复错误，强制终止当前 Agent 循环");
                        return Mono.just(true);
                    }
                    return Mono.just(false);
                }));
    }

    // ==================== ReCAP 相关方法 ====================

    private void subscribeToSubagentEvents() {
        wire.asFlux()
                .ofType(SubagentStarting.class)
                .subscribe(event -> {
                    log.debug("收到 SubagentStarting 事件: {}", event.getSubagentName());
                    pushCurrentContext(event.getPrompt()).subscribe();
                });

        wire.asFlux()
                .ofType(SubagentCompleted.class)
                .subscribe(event -> {
                    log.debug("收到 SubagentCompleted 事件");
                    restoreParentContext(event.getSummary()).subscribe();
                });
    }

    public Mono<Void> pushCurrentContext(String subGoalDesc) {
        if (memoryConfig == null || !memoryConfig.isEnableRecap()) {
            return Mono.empty();
        }

        return Mono.defer(() -> {
            if (executionState.getCurrentDepth() >= memoryConfig.getMaxRecursionDepth()) {
                log.warn("达到最大递归深度 {}, 不再入栈", memoryConfig.getMaxRecursionDepth());
                return Mono.empty();
            }

            return context.checkpoint(false)
                    .map(checkpointId -> {
                        String latestThought = memoryRecorder.extractLatestThought(context);

                        ParentContext parent = new ParentContext(
                                checkpointId,
                                latestThought,
                                executionState.getCurrentDepth(),
                                subGoalDesc
                        );

                        executionState.pushParentContext(parent);

                        log.info("Push 父级上下文 (depth: {} -> {}, checkpoint: {})",
                                parent.getDepth(), executionState.getCurrentDepth(), checkpointId);

                        return parent;
                    })
                    .then();
        });
    }

    public Mono<Void> restoreParentContext(String childSummary) {
        if (memoryConfig == null || !memoryConfig.isEnableRecap() || executionState.isParentStackEmpty()) {
            return Mono.empty();
        }

        return Mono.defer(() -> {
            ParentContext parent = executionState.popParentContext();
            if (parent == null) {
                return Mono.empty();
            }

            log.info("Restore 父级上下文 (depth: {} <- {}, checkpoint: {})",
                    executionState.getCurrentDepth(), executionState.getCurrentDepth() + 1,
                    parent.getCheckpointId());

            return context.revertTo(parent.getCheckpointId())
                    .then(Mono.defer(() -> {
                        String injectionMsg = parent.formatForInjection()
                                + "\n## 子目标完成摘要\n"
                                + (childSummary != null ? childSummary : "(无)");

                        return context.appendMessage(Message.user(List.of(TextPart.of(injectionMsg))));
                    }))
                    .doOnSuccess(v -> log.debug("结构化注入完成"));
        });
    }

    // ==================== 公共辅助方法 ====================

    public Mono<Void> recordSessionSummary(String status) {
        return memoryRecorder.recordSessionSummary(executionState, context, status);
    }

    public void recordFileModified(String filePath) {
        executionState.recordFileModified(filePath);
    }

    public void recordKeyDecision(String decision) {
        executionState.recordKeyDecision(decision);
    }
}

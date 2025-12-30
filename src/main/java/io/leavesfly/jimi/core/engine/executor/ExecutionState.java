package io.leavesfly.jimi.core.engine.executor;

import io.leavesfly.jimi.core.engine.runtime.ParentContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * 执行状态管理器
 * <p>
 * 职责：
 * - 跟踪任务执行状态（开始时间、工具使用、步数、Token数）
 * - 跟踪会话状态（修改的文件、关键决策、经验教训）
 * - 跟踪连续思考步数（无工具调用检测）
 * - 管理 ReCAP 父级上下文栈和递归深度
 */
@Slf4j
@Getter
@Setter
public class ExecutionState {

    // ==================== 任务执行跟踪 ====================
    
    /** 任务开始时间 */
    private Instant taskStartTime;
    
    /** 当前用户查询 */
    private String currentUserQuery;
    
    /** 任务中使用的工具列表 */
    private final List<String> toolsUsedInTask = new ArrayList<>();
    
    /** 任务中的步数 */
    private int stepsInTask = 0;
    
    /** 任务中使用的Token数 */
    private int tokensInTask = 0;

    // ==================== 会话跟踪 ====================
    
    /** 会话开始时间 */
    private Instant sessionStartTime;
    
    /** 修改的文件列表 */
    private final List<String> filesModified = new ArrayList<>();
    
    /** 关键决策列表 */
    private final List<String> keyDecisions = new ArrayList<>();
    
    /** 经验教训列表 */
    private final List<String> lessonsLearned = new ArrayList<>();
    
    /** 会话中完成的任务数 */
    private int tasksCompletedInSession = 0;

    // ==================== 思考步数跟踪 ====================
    
    /** 连续无工具调用步数 */
    private int consecutiveNoToolCallSteps = 0;

    // ==================== ReCAP 相关 ====================
    
    /** 父级上下文栈（用于 Subagent 恢复） */
    private final Deque<ParentContext> parentStack = new LinkedList<>();
    
    /** 当前递归深度 */
    private int currentDepth = 0;

    /**
     * 初始化任务状态（在每个新任务开始时调用）
     */
    public void initializeTask() {
        taskStartTime = Instant.now();
        toolsUsedInTask.clear();
        stepsInTask = 0;
        tokensInTask = 0;
        consecutiveNoToolCallSteps = 0;
        
        log.debug("任务状态已初始化: startTime={}", taskStartTime);
    }

    /**
     * 初始化会话状态（在会话开始时调用）
     */
    public void initializeSession() {
        sessionStartTime = Instant.now();
        filesModified.clear();
        keyDecisions.clear();
        lessonsLearned.clear();
        tasksCompletedInSession = 0;
        
        log.debug("会话状态已初始化: startTime={}", sessionStartTime);
    }

    /**
     * 记录工具使用
     *
     * @param toolName 工具名称
     */
    public void recordToolUsed(String toolName) {
        if (toolName != null && !toolName.isEmpty() && !toolsUsedInTask.contains(toolName)) {
            toolsUsedInTask.add(toolName);
            log.debug("记录工具使用: {}", toolName);
        }
    }

    /**
     * 增加步数
     */
    public void incrementStep() {
        stepsInTask++;
        log.debug("步数递增: {}", stepsInTask);
    }

    /**
     * 累加Token数
     *
     * @param tokens 要添加的Token数
     */
    public void addTokens(int tokens) {
        tokensInTask += tokens;
        log.debug("Token累加: {} (总计: {})", tokens, tokensInTask);
    }

    /**
     * 检查是否应该强制完成（连续思考步数超限）
     *
     * @param maxThinkingSteps 最大连续思考步数
     * @return true 如果应该强制完成
     */
    public boolean shouldForceComplete(int maxThinkingSteps) {
        consecutiveNoToolCallSteps++;
        
        if (consecutiveNoToolCallSteps >= maxThinkingSteps) {
            log.warn("连续思考 {} 步未调用工具，强制完成", consecutiveNoToolCallSteps);
            return true;
        }
        
        log.debug("连续思考步数: {}/{}", consecutiveNoToolCallSteps, maxThinkingSteps);
        return false;
    }

    /**
     * 重置无工具调用计数器（当有工具调用时）
     */
    public void resetNoToolCallCounter() {
        if (consecutiveNoToolCallSteps > 0) {
            log.debug("重置连续思考计数器 (之前: {})", consecutiveNoToolCallSteps);
        }
        consecutiveNoToolCallSteps = 0;
    }

    /**
     * 记录修改的文件
     *
     * @param filePath 文件路径
     */
    public void recordFileModified(String filePath) {
        if (filePath != null && !filePath.isEmpty() && !filesModified.contains(filePath)) {
            filesModified.add(filePath);
            log.debug("记录文件修改: {}", filePath);
        }
    }

    /**
     * 记录关键决策
     *
     * @param decision 决策描述
     */
    public void recordKeyDecision(String decision) {
        if (decision != null && !decision.isEmpty() && !keyDecisions.contains(decision)) {
            keyDecisions.add(decision);
            log.debug("记录关键决策: {}", decision);
        }
    }

    /**
     * 记录经验教训
     *
     * @param lesson 经验描述
     */
    public void recordLessonLearned(String lesson) {
        if (lesson != null && !lesson.isEmpty() && !lessonsLearned.contains(lesson)) {
            lessonsLearned.add(lesson);
            log.debug("记录经验教训: {}", lesson);
        }
    }

    /**
     * 增加完成的任务数
     */
    public void incrementTasksCompleted() {
        tasksCompletedInSession++;
        log.debug("任务完成数递增: {}", tasksCompletedInSession);
    }

    /**
     * 计算任务执行时长（毫秒）
     *
     * @return 执行时长，如果任务未开始返回 0
     */
    public long getTaskDurationMs() {
        if (taskStartTime == null) {
            return 0;
        }
        return Instant.now().toEpochMilli() - taskStartTime.toEpochMilli();
    }

    /**
     * 计算会话时长（毫秒）
     *
     * @return 会话时长，如果会话未开始返回 0
     */
    public long getSessionDurationMs() {
        if (sessionStartTime == null) {
            return 0;
        }
        return Instant.now().toEpochMilli() - sessionStartTime.toEpochMilli();
    }

    /**
     * Push 父级上下文到栈
     *
     * @param parent 父级上下文
     */
    public void pushParentContext(ParentContext parent) {
        parentStack.push(parent);
        currentDepth++;
        log.debug("Push 父级上下文: depth {} -> {}", parent.getDepth(), currentDepth);
    }

    /**
     * Pop 父级上下文
     *
     * @return 弹出的父级上下文，如果栈为空返回 null
     */
    public ParentContext popParentContext() {
        if (parentStack.isEmpty()) {
            log.warn("父级上下文栈为空，无法 pop");
            return null;
        }
        
        ParentContext parent = parentStack.pop();
        currentDepth = parent.getDepth();
        log.debug("Pop 父级上下文: depth -> {}", currentDepth);
        return parent;
    }

    /**
     * 检查父级上下文栈是否为空
     *
     * @return true 如果栈为空
     */
    public boolean isParentStackEmpty() {
        return parentStack.isEmpty();
    }

    /**
     * 重置所有状态（用于测试或完全重置）
     */
    public void reset() {
        initializeTask();
        initializeSession();
        parentStack.clear();
        currentDepth = 0;
        
        log.debug("执行状态已完全重置");
    }
}

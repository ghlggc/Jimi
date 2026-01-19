package io.leavesfly.jwork.model;

import lombok.Data;
import lombok.AllArgsConstructor;

/**
 * 流式输出块
 * 将 Wire 消息转换为 UI 可展示的单元
 */
@Data
@AllArgsConstructor
public class StreamChunk {
    
    /**
     * 块类型
     */
    public enum Type {
        TEXT,           // 文本内容
        TOOL_CALL,      // 工具调用
        TOOL_RESULT,    // 工具结果
        APPROVAL,       // 审批请求
        STEP_BEGIN,     // 步骤开始
        STEP_END,       // 步骤结束
        TODO_UPDATE,    // 任务计划更新
        ERROR,          // 错误
        DONE            // 完成
    }
    
    private final Type type;
    private final String content;
    private final ApprovalInfo approval;
    private final TodoInfo.TodoList todoList;
    
    // 工厂方法
    
    public static StreamChunk text(String content) {
        return new StreamChunk(Type.TEXT, content, null, null);
    }
    
    public static StreamChunk toolCall(String toolName) {
        return new StreamChunk(Type.TOOL_CALL, toolName, null, null);
    }
    
    public static StreamChunk toolResult(String result) {
        return new StreamChunk(Type.TOOL_RESULT, result, null, null);
    }
    
    public static StreamChunk approval(ApprovalInfo info) {
        return new StreamChunk(Type.APPROVAL, null, info, null);
    }
    
    public static StreamChunk stepBegin() {
        return new StreamChunk(Type.STEP_BEGIN, null, null, null);
    }
    
    public static StreamChunk stepEnd() {
        return new StreamChunk(Type.STEP_END, null, null, null);
    }
    
    public static StreamChunk todoUpdate(TodoInfo.TodoList todoList) {
        return new StreamChunk(Type.TODO_UPDATE, null, null, todoList);
    }
    
    public static StreamChunk error(String message) {
        return new StreamChunk(Type.ERROR, message, null, null);
    }
    
    public static StreamChunk done() {
        return new StreamChunk(Type.DONE, null, null, null);
    }
    
    public static StreamChunk empty() {
        return new StreamChunk(Type.TEXT, "", null, null);
    }
}

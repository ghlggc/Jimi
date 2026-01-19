package io.leavesfly.jwork.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务计划项信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TodoInfo {
    
    private String id;
    private String content;
    private Status status;
    private String parentId;
    
    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETE,
        ERROR,
        CANCELLED
    }
    
    /**
     * 获取状态图标
     */
    public String getStatusIcon() {
        return switch (status) {
            case PENDING -> "⏳";
            case IN_PROGRESS -> "🔄";
            case COMPLETE -> "✅";
            case ERROR -> "❌";
            case CANCELLED -> "⛔";
        };
    }
    
    /**
     * 从状态字符串转换
     */
    public static Status parseStatus(String statusStr) {
        if (statusStr == null) return Status.PENDING;
        return switch (statusStr.toLowerCase()) {
            case "pending" -> Status.PENDING;
            case "in progress", "inprogress", "in_progress" -> Status.IN_PROGRESS;
            case "done", "complete", "completed" -> Status.COMPLETE;
            case "error", "failed" -> Status.ERROR;
            case "cancelled", "canceled" -> Status.CANCELLED;
            default -> Status.PENDING;
        };
    }
    
    /**
     * Todo 列表包装（包含统计信息）
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TodoList {
        private List<TodoInfo> todos;
        private int totalCount;
        private int pendingCount;
        private int inProgressCount;
        private int doneCount;
        private int cancelledCount;
        private int errorCount;
    }
}

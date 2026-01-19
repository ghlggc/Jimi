package io.leavesfly.jimi.wire.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Todo 更新消息
 * 当 SetTodoList 工具执行后，通过 Wire 通知 UI 更新待办事项列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoUpdateMessage implements WireMessage {
    
    /**
     * 待办事项列表
     */
    private List<TodoItem> todos;
    
    /**
     * 统计信息
     */
    private int totalCount;
    private int pendingCount;
    private int inProgressCount;
    private int doneCount;
    private int cancelledCount;
    private int errorCount;
    
    @Override
    public String getMessageType() {
        return "todo_update";
    }
    
    /**
     * 待办事项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodoItem {
        private String id;
        private String title;
        private String status;
        private String parentId;
    }
}

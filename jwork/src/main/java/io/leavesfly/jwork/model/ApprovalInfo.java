package io.leavesfly.jwork.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 审批信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApprovalInfo {
    
    private String toolCallId;
    private String action;
    private String description;
    
    /**
     * 审批响应
     */
    public enum Response {
        APPROVE,            // 批准
        APPROVE_SESSION,    // 本次会话批准
        REJECT              // 拒绝
    }
}

package io.leavesfly.jwork.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话元数据
 * 用于持久化会话信息（不包含 Engine 实例）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionMetadata {
    
    private String id;
    private String workDir;
    private String agentName;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessAt;
    private String historyFile;  // 历史文件路径
    
    /**
     * 从 WorkSession 创建元数据
     */
    public static SessionMetadata fromWorkSession(WorkSession session) {
        String historyFilePath = "";
        if (session.getEngine() != null && 
            session.getEngine().getRuntime() != null && 
            session.getEngine().getRuntime().getSession() != null &&
            session.getEngine().getRuntime().getSession().getHistoryFile() != null) {
            historyFilePath = session.getEngine().getRuntime().getSession().getHistoryFile().toString();
        }
        
        return SessionMetadata.builder()
            .id(session.getId())
            .workDir(session.getWorkDir().toString())
            .agentName(session.getAgentName())
            .createdAt(session.getCreatedAt())
            .lastAccessAt(LocalDateTime.now())
            .historyFile(historyFilePath)
            .build();
    }
}

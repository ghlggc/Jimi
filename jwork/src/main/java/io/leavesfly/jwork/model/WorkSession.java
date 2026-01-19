package io.leavesfly.jwork.model;

import io.leavesfly.jimi.core.JimiEngine;
import lombok.Data;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 工作会话
 * 封装 JimiEngine，管理单个会话的生命周期
 */
@Data
public class WorkSession {
    
    private final String id;
    private final JimiEngine engine;
    private final Path workDir;
    private final String agentName;
    private final LocalDateTime createdAt;
    
    // 当前任务状态
    private volatile String currentJobId;
    private volatile boolean running;
    
    public WorkSession(JimiEngine engine, Path workDir, String agentName) {
        this(UUID.randomUUID().toString(), engine, workDir, agentName, LocalDateTime.now());
    }

    public WorkSession(String id, JimiEngine engine, Path workDir, String agentName, LocalDateTime createdAt) {
        this.id = id;
        this.engine = engine;
        this.workDir = workDir;
        this.agentName = agentName;
        this.createdAt = createdAt;
        this.running = false;
    }
    
    /**
     * 标记任务开始
     */
    public void startJob(String jobId) {
        this.currentJobId = jobId;
        this.running = true;
    }
    
    /**
     * 标记任务结束
     */
    public void endJob() {
        this.currentJobId = null;
        this.running = false;
    }
    
    /**
     * 获取会话显示名称
     */
    public String getDisplayName() {
        String dirName = workDir.getFileName().toString();
        return dirName + " (" + agentName + ")";
    }
}

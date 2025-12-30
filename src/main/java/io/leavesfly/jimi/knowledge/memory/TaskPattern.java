package io.leavesfly.jimi.knowledge.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 任务模式
 * 记录常见任务的执行步骤和经验
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskPattern {
    
    /**
     * 唯一标识
     */
    @JsonProperty("id")
    private String id;
    
    /**
     * 触发词（如"添加新工具"、"修复bug"等）
     */
    @JsonProperty("trigger")
    private String trigger;
    
    /**
     * 执行步骤
     */
    @JsonProperty("steps")
    private List<String> steps;
    
    /**
     * 使用次数
     */
    @JsonProperty("usageCount")
    @Builder.Default
    private int usageCount = 0;
    
    /**
     * 成功率（0.0-1.0）
     */
    @JsonProperty("successRate")
    @Builder.Default
    private double successRate = 1.0;
    
    /**
     * 最后使用时间
     */
    @JsonProperty("lastUsed")
    private Instant lastUsed;
    
    /**
     * 增加使用次数
     */
    public void incrementUsage() {
        this.usageCount++;
        this.lastUsed = Instant.now();
    }
}

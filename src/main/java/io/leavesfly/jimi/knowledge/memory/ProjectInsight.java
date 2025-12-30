package io.leavesfly.jimi.knowledge.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 项目知识条目
 * 从工具执行结果中提取的关键发现
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectInsight {
    
    /**
     * 唯一标识
     */
    @JsonProperty("id")
    private String id;
    
    /**
     * 分类（architecture/bug_fix/code_structure/execution等）
     */
    @JsonProperty("category")
    private String category;
    
    /**
     * 创建时间
     */
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    /**
     * 知识内容
     */
    @JsonProperty("content")
    private String content;
    
    /**
     * 来源（tool_execution/code_analysis/task_completion等）
     */
    @JsonProperty("source")
    private String source;
    
    /**
     * 置信度（0.0-1.0）
     */
    @JsonProperty("confidence")
    @Builder.Default
    private double confidence = 0.8;
    
    /**
     * 访问次数
     */
    @JsonProperty("accessCount")
    @Builder.Default
    private int accessCount = 0;
    
    /**
     * 最后访问时间
     */
    @JsonProperty("lastAccessed")
    private Instant lastAccessed;
    
    /**
     * 增加访问次数
     */
    public void incrementAccess() {
        this.accessCount++;
        this.lastAccessed = Instant.now();
    }
}

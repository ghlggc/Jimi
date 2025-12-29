package io.leavesfly.jimi.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务历史记录
 * 记录每次执行的完整任务信息，用于回答"最近做了什么"等查询
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskHistory {
    
    /**
     * 唯一标识
     */
    @JsonProperty("id")
    private String id;
    
    /**
     * 任务开始时间
     */
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    /**
     * 用户原始查询
     */
    @JsonProperty("userQuery")
    private String userQuery;
    
    /**
     * 任务摘要（自动生成或从最后的assistant回复提取）
     */
    @JsonProperty("summary")
    private String summary;
    
    /**
     * 使用的工具列表
     */
    @JsonProperty("toolsUsed")
    @Builder.Default
    private List<String> toolsUsed = new ArrayList<>();
    
    /**
     * 执行结果状态（success/partial/failed）
     */
    @JsonProperty("resultStatus")
    @Builder.Default
    private String resultStatus = "success";
    
    /**
     * 执行步数
     */
    @JsonProperty("stepsCount")
    @Builder.Default
    private int stepsCount = 0;
    
    /**
     * 消耗的Token数
     */
    @JsonProperty("tokensUsed")
    @Builder.Default
    private int tokensUsed = 0;
    
    /**
     * 任务持续时间（毫秒）
     */
    @JsonProperty("durationMs")
    @Builder.Default
    private long durationMs = 0;
    
    /**
     * 任务标签（用于分类：bug_fix, feature_add, refactor, query等）
     */
    @JsonProperty("tags")
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    
    /**
     * 添加工具使用记录
     */
    public void addToolUsed(String toolName) {
        if (!toolsUsed.contains(toolName)) {
            toolsUsed.add(toolName);
        }
    }
    
    /**
     * 添加标签
     */
    public void addTag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
    }
    
    /**
     * 格式化为用户友好的描述
     */
    public String formatForDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【%s】", 
                timestamp.toString().substring(0, 19).replace('T', ' ')));
        sb.append(" ").append(userQuery);
        
        if (summary != null && !summary.isEmpty()) {
            sb.append("\n  摘要: ").append(summary);
        }
        
        if (!toolsUsed.isEmpty()) {
            sb.append("\n  使用工具: ").append(String.join(", ", toolsUsed));
        }
        
        sb.append(String.format("\n  统计: %d步, %d tokens, 耗时%dms", 
                stepsCount, tokensUsed, durationMs));
        
        return sb.toString();
    }
}

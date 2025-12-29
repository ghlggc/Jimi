package io.leavesfly.jimi.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务模式存储
 * 管理所有任务模式
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskPatternStore {
    
    /**
     * 版本号
     */
    @JsonProperty("version")
    private String version = "1.0";
    
    /**
     * 任务模式列表
     */
    @JsonProperty("patterns")
    private List<TaskPattern> patterns = new ArrayList<>();
    
    /**
     * 添加模式
     */
    public void add(TaskPattern pattern) {
        patterns.add(pattern);
    }
    
    /**
     * 根据触发词查找模式
     */
    public TaskPattern findByTrigger(String trigger) {
        if (trigger == null || trigger.isEmpty()) {
            return null;
        }
        
        String lowerTrigger = trigger.toLowerCase();
        
        return patterns.stream()
                .filter(p -> lowerTrigger.contains(p.getTrigger().toLowerCase()))
                .findFirst()
                .map(pattern -> {
                    pattern.incrementUsage();
                    return pattern;
                })
                .orElse(null);
    }
}

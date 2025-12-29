package io.leavesfly.jimi.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务历史存储
 * 管理所有任务执行历史
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskHistoryStore {
    
    /**
     * 版本号
     */
    @JsonProperty("version")
    private String version = "1.0";
    
    /**
     * 任务历史列表
     */
    @JsonProperty("tasks")
    private List<TaskHistory> tasks = new ArrayList<>();
    
    /**
     * 添加任务历史
     */
    public void add(TaskHistory task) {
        tasks.add(task);
    }
    
    /**
     * 获取最近的任务（按时间倒序）
     * 
     * @param limit 返回数量
     * @return 任务列表
     */
    public List<TaskHistory> getRecent(int limit) {
        return tasks.stream()
                .sorted(Comparator.comparing(TaskHistory::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * 按时间范围查询任务
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 任务列表
     */
    public List<TaskHistory> getByTimeRange(Instant startTime, Instant endTime) {
        return tasks.stream()
                .filter(task -> {
                    Instant timestamp = task.getTimestamp();
                    return !timestamp.isBefore(startTime) && !timestamp.isAfter(endTime);
                })
                .sorted(Comparator.comparing(TaskHistory::getTimestamp).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * 按关键词搜索任务
     * 
     * @param keyword 关键词
     * @param limit 返回数量
     * @return 任务列表
     */
    public List<TaskHistory> searchByKeyword(String keyword, int limit) {
        if (keyword == null || keyword.isEmpty()) {
            return List.of();
        }
        
        String lowerKeyword = keyword.toLowerCase();
        
        return tasks.stream()
                .filter(task -> 
                    task.getUserQuery().toLowerCase().contains(lowerKeyword) ||
                    (task.getSummary() != null && task.getSummary().toLowerCase().contains(lowerKeyword)) ||
                    task.getToolsUsed().stream().anyMatch(tool -> tool.toLowerCase().contains(lowerKeyword))
                )
                .sorted(Comparator.comparing(TaskHistory::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * 按标签查询任务
     * 
     * @param tag 标签
     * @param limit 返回数量
     * @return 任务列表
     */
    public List<TaskHistory> getByTag(String tag, int limit) {
        if (tag == null || tag.isEmpty()) {
            return List.of();
        }
        
        return tasks.stream()
                .filter(task -> task.getTags().contains(tag))
                .sorted(Comparator.comparing(TaskHistory::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * 清理过期的任务历史
     * 
     * @param maxSize 最大保留数量
     * @param expiryDays 过期天数
     */
    public void prune(int maxSize, int expiryDays) {
        // 1. 移除过期任务
        Instant expiry = Instant.now().minus(expiryDays, ChronoUnit.DAYS);
        tasks.removeIf(task -> task.getTimestamp().isBefore(expiry));
        
        // 2. 如果仍然超限，只保留最近的任务
        if (tasks.size() > maxSize) {
            tasks = tasks.stream()
                    .sorted(Comparator.comparing(TaskHistory::getTimestamp).reversed())
                    .limit(maxSize)
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * 获取统计信息
     */
    public TaskHistoryStats getStats() {
        if (tasks.isEmpty()) {
            return new TaskHistoryStats(0, 0, 0, 0);
        }
        
        int totalTasks = tasks.size();
        int totalSteps = tasks.stream().mapToInt(TaskHistory::getStepsCount).sum();
        int totalTokens = tasks.stream().mapToInt(TaskHistory::getTokensUsed).sum();
        long totalDuration = tasks.stream().mapToLong(TaskHistory::getDurationMs).sum();
        
        return new TaskHistoryStats(totalTasks, totalSteps, totalTokens, totalDuration);
    }
    
    /**
     * 统计信息数据类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskHistoryStats {
        private int totalTasks;
        private int totalSteps;
        private int totalTokens;
        private long totalDurationMs;
    }
}

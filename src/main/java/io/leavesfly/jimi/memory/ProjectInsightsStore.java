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
 * 项目知识存储
 * 管理所有项目相关的知识条目
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectInsightsStore {
    
    /**
     * 版本号
     */
    @JsonProperty("version")
    private String version = "1.0";
    
    /**
     * 工作目录
     */
    @JsonProperty("workspaceRoot")
    private String workspaceRoot;
    
    /**
     * 知识列表
     */
    @JsonProperty("insights")
    private List<ProjectInsight> insights = new ArrayList<>();
    
    /**
     * 添加知识
     */
    public void add(ProjectInsight insight) {
        insights.add(insight);
    }
    
    /**
     * 搜索知识（基于关键词）
     */
    public List<ProjectInsight> search(String keyword, int limit) {
        if (keyword == null || keyword.isEmpty()) {
            return List.of();
        }
        
        String lowerKeyword = keyword.toLowerCase();
        
        return insights.stream()
                .filter(insight -> 
                    insight.getContent().toLowerCase().contains(lowerKeyword) ||
                    insight.getCategory().toLowerCase().contains(lowerKeyword)
                )
                .sorted(Comparator
                        .comparingInt(ProjectInsight::getAccessCount).reversed()
                        .thenComparing(ProjectInsight::getTimestamp).reversed()
                )
                .limit(limit)
                .peek(ProjectInsight::incrementAccess)
                .collect(Collectors.toList());
    }
    
    /**
     * 清理过期和低频知识
     * 
     * @param maxSize 最大保留数量
     */
    public void prune(int maxSize) {
        if (insights.size() <= maxSize) {
            return;
        }
        
        // 1. 移除过期知识（超过90天未访问）
        Instant expiry = Instant.now().minus(90, ChronoUnit.DAYS);
        insights.removeIf(insight -> 
            insight.getLastAccessed() != null && 
            insight.getLastAccessed().isBefore(expiry)
        );
        
        // 2. 如果仍然超限，按访问频率排序后删除低频项
        if (insights.size() > maxSize) {
            insights.sort(Comparator.comparingInt(ProjectInsight::getAccessCount).reversed());
            insights.subList(maxSize, insights.size()).clear();
        }
    }
}

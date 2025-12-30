package io.leavesfly.jimi.knowledge.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 统一的记忆存储
 * 替代原有的4个独立Store类,使用类型区分不同的记忆
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryStore {
    
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
     * 所有记忆条目(按类型分组存储)
     */
    @JsonProperty("entries")
    private Map<MemoryType, List<MemoryEntry>> entries = new ConcurrentHashMap<>();
    
    /**
     * 添加记忆条目
     */
    public void add(MemoryEntry entry) {
        entries.computeIfAbsent(entry.getType(), k -> new ArrayList<>()).add(entry);
    }
    
    /**
     * 添加或更新错误模式(如果已存在相似的,则更新)
     */
    public void addOrUpdateErrorPattern(MemoryEntry entry) {
        if (entry.getType() != MemoryType.ERROR_PATTERN) {
            throw new IllegalArgumentException("Entry type must be ERROR_PATTERN");
        }
        
        List<MemoryEntry> patterns = entries.computeIfAbsent(MemoryType.ERROR_PATTERN, k -> new ArrayList<>());
        
        String errorMessage = entry.getMetadataString("errorMessage");
        String context = entry.getMetadataString("context");
        
        // 查找是否存在相似模式
        Optional<MemoryEntry> existing = patterns.stream()
                .filter(p -> matchesError(p, errorMessage, context))
                .findFirst();
        
        if (existing.isPresent()) {
            // 更新现有模式
            MemoryEntry existingEntry = existing.get();
            Integer count = existingEntry.getMetadataInt("occurrenceCount");
            existingEntry.setMetadata("occurrenceCount", count != null ? count + 1 : 2);
            existingEntry.setMetadata("lastSeen", Instant.now());
            existingEntry.touch();
            
            // 如果新模式有更好的解决方案,更新它
            String newSolution = entry.getMetadataString("solution");
            if (newSolution != null && !newSolution.isEmpty()) {
                existingEntry.setMetadata("solution", newSolution);
            }
        } else {
            // 添加新模式
            entry.setMetadata("occurrenceCount", 1);
            entry.setMetadata("resolvedCount", 0);
            entry.setMetadata("firstSeen", Instant.now());
            entry.setMetadata("lastSeen", Instant.now());
            patterns.add(entry);
        }
    }
    
    /**
     * 检查错误是否匹配
     */
    private boolean matchesError(MemoryEntry entry, String errorMsg, String ctx) {
        if (errorMsg == null) {
            return false;
        }
        
        String storedErrorMsg = entry.getMetadataString("errorMessage");
        if (storedErrorMsg == null) {
            return false;
        }
        
        boolean msgMatch = errorMsg.toLowerCase().contains(storedErrorMsg.toLowerCase());
        
        if (ctx != null) {
            String storedCtx = entry.getMetadataString("context");
            if (storedCtx != null) {
                boolean ctxMatch = ctx.toLowerCase().contains(storedCtx.toLowerCase());
                return msgMatch && ctxMatch;
            }
        }
        
        return msgMatch;
    }
    
    /**
     * 获取指定类型的所有记忆
     */
    public List<MemoryEntry> getByType(MemoryType type) {
        return entries.getOrDefault(type, new ArrayList<>());
    }
    
    /**
     * 获取最近的记忆(按时间倒序)
     */
    public List<MemoryEntry> getRecent(MemoryType type, int limit) {
        return getByType(type).stream()
                .sorted(Comparator.comparing(MemoryEntry::getCreatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * 按关键词搜索记忆
     */
    public List<MemoryEntry> searchByKeyword(MemoryType type, String keyword, int limit) {
        if (keyword == null || keyword.isEmpty()) {
            return List.of();
        }
        
        String lowerKeyword = keyword.toLowerCase();
        
        return getByType(type).stream()
                .filter(entry -> matchesKeyword(entry, lowerKeyword))
                .sorted(Comparator.comparing(MemoryEntry::getCreatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * 检查记忆是否匹配关键词
     */
    private boolean matchesKeyword(MemoryEntry entry, String keyword) {
        if (entry.getContent() != null && entry.getContent().toLowerCase().contains(keyword)) {
            return true;
        }
        
        // 搜索元数据
        for (Object value : entry.getMetadata().values()) {
            if (value != null && value.toString().toLowerCase().contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 按时间范围查询
     */
    public List<MemoryEntry> getByTimeRange(MemoryType type, Instant startTime, Instant endTime) {
        return getByType(type).stream()
                .filter(entry -> {
                    Instant timestamp = entry.getCreatedAt();
                    return !timestamp.isBefore(startTime) && !timestamp.isAfter(endTime);
                })
                .sorted(Comparator.comparing(MemoryEntry::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * 查找匹配的错误模式
     */
    public Optional<MemoryEntry> findErrorPattern(String errorMessage, String context) {
        return getByType(MemoryType.ERROR_PATTERN).stream()
                .filter(e -> matchesError(e, errorMessage, context))
                .findFirst();
    }
    
    /**
     * 记录错误解决成功
     */
    public void recordErrorResolution(String errorMessage, String context) {
        findErrorPattern(errorMessage, context).ifPresent(entry -> {
            Integer resolvedCount = entry.getMetadataInt("resolvedCount");
            entry.setMetadata("resolvedCount", resolvedCount != null ? resolvedCount + 1 : 1);
            entry.touch();
        });
    }
    
    /**
     * 获取最常见的错误模式
     */
    public List<MemoryEntry> getMostFrequentErrors(int limit) {
        return getByType(MemoryType.ERROR_PATTERN).stream()
                .sorted((a, b) -> {
                    Integer countA = a.getMetadataInt("occurrenceCount");
                    Integer countB = b.getMetadataInt("occurrenceCount");
                    return Integer.compare(countB != null ? countB : 0, countA != null ? countA : 0);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取最后一个会话摘要
     */
    public MemoryEntry getLastSession() {
        List<MemoryEntry> sessions = getByType(MemoryType.SESSION_SUMMARY);
        if (sessions.isEmpty()) {
            return null;
        }
        return sessions.stream()
                .max(Comparator.comparing(MemoryEntry::getCreatedAt))
                .orElse(null);
    }
    
    /**
     * 清理过期的记忆
     */
    public void prune(MemoryType type, int maxSize, int expiryDays) {
        List<MemoryEntry> memoryList = getByType(type);
        if (memoryList.isEmpty()) {
            return;
        }
        
        // 1. 移除过期记忆
        Instant expiry = Instant.now().minus(expiryDays, ChronoUnit.DAYS);
        memoryList.removeIf(entry -> {
            Instant timestamp = entry.getUpdatedAt() != null ? entry.getUpdatedAt() : entry.getCreatedAt();
            return timestamp != null && timestamp.isBefore(expiry) && entry.getAccessCount() < 3;
        });
        
        // 2. 如果仍然超限,按重要性排序后保留
        if (memoryList.size() > maxSize) {
            List<MemoryEntry> sorted = memoryList.stream()
                    .sorted((a, b) -> {
                        // 优先按访问次数,其次按创建时间
                        int cmp = Integer.compare(b.getAccessCount(), a.getAccessCount());
                        if (cmp == 0) {
                            return b.getCreatedAt().compareTo(a.getCreatedAt());
                        }
                        return cmp;
                    })
                    .limit(maxSize)
                    .collect(Collectors.toList());
            
            memoryList.clear();
            memoryList.addAll(sorted);
        }
    }
    
    /**
     * 清理所有类型的过期记忆
     */
    public void pruneAll(int maxSizePerType, int expiryDays) {
        for (MemoryType type : MemoryType.values()) {
            prune(type, maxSizePerType, expiryDays);
        }
    }
    
    /**
     * 获取统计信息
     */
    public Map<MemoryType, Integer> getStats() {
        Map<MemoryType, Integer> stats = new HashMap<>();
        for (Map.Entry<MemoryType, List<MemoryEntry>> entry : entries.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }
    
    /**
     * 获取总记忆数
     */
    public int getTotalCount() {
        return entries.values().stream().mapToInt(List::size).sum();
    }
}

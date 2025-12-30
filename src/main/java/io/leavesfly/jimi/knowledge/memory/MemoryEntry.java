package io.leavesfly.jimi.knowledge.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一的记忆条目数据模型
 * 用于存储所有类型的长期记忆
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEntry {
    
    /**
     * 唯一标识
     */
    @JsonProperty("id")
    private String id;
    
    /**
     * 记忆类型
     */
    @JsonProperty("type")
    private MemoryType type;
    
    /**
     * 主要内容
     */
    @JsonProperty("content")
    private String content;
    
    /**
     * 元数据（用于存储不同类型记忆的特定字段）
     */
    @JsonProperty("metadata")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * 创建时间
     */
    @JsonProperty("createdAt")
    private Instant createdAt;
    
    /**
     * 更新时间
     */
    @JsonProperty("updatedAt")
    private Instant updatedAt;
    
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
     * 置信度/重要性 (0.0-1.0)
     */
    @JsonProperty("confidence")
    @Builder.Default
    private double confidence = 0.8;
    
    /**
     * 增加访问次数
     */
    public void incrementAccess() {
        this.accessCount++;
        this.lastAccessed = Instant.now();
    }
    
    /**
     * 更新时间戳
     */
    public void touch() {
        this.updatedAt = Instant.now();
    }
    
    /**
     * 获取元数据值
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    /**
     * 设置元数据值
     */
    public void setMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }
    
    /**
     * 获取字符串类型的元数据
     */
    public String getMetadataString(String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * 获取整数类型的元数据
     */
    public Integer getMetadataInt(String key) {
        Object value = metadata.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
    
    /**
     * 获取布尔类型的元数据
     */
    public Boolean getMetadataBoolean(String key) {
        Object value = metadata.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }
}

package io.leavesfly.jimi.llm.message;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息角色枚举
 */
@Slf4j
public enum MessageRole {
    /**
     * 用户消息
     */
    USER("user"),
    
    /**
     * 助手消息
     */
    ASSISTANT("assistant"),
    
    /**
     * 系统消息
     */
    SYSTEM("system"),
    
    /**
     * 工具消息
     */
    TOOL("tool");
    
    private final String value;
    
    MessageRole(String value) {
        this.value = value;
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    @com.fasterxml.jackson.annotation.JsonCreator
    public static MessageRole fromValue(String value) {
        for (MessageRole role : values()) {
            if (role.value.equalsIgnoreCase(value) || role.name().equalsIgnoreCase(value)) {
                return role;
            }
        }
        log.warn("Unknown message role: {}, defaulting to USER", value);
        return USER;
    }
}

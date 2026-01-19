package io.leavesfly.jwork.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 远程连接配置
 * 用于连接远程 Jimi 服务器
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RemoteConnection {
    
    /**
     * 连接模式
     */
    public enum Mode {
        EMBEDDED,  // 本地嵌入式（默认）
        REMOTE     // 远程服务器
    }
    
    @Builder.Default
    private Mode mode = Mode.EMBEDDED;
    
    private String serverUrl;     // 远程服务器 URL，如 http://localhost:9527
    private String apiKey;        // 可选的 API Key
    private int timeout = 30;     // 连接超时（秒）
    
    /**
     * 获取完整的 RPC URL
     */
    public String getRpcUrl() {
        if (serverUrl == null) return null;
        String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        return baseUrl + "/api/v1/rpc";
    }
    
    /**
     * 获取事件流 URL
     */
    public String getEventsUrl(String sessionId) {
        if (serverUrl == null) return null;
        String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        return baseUrl + "/api/v1/events/" + sessionId;
    }
    
    /**
     * 检查是否为远程模式
     */
    public boolean isRemote() {
        return mode == Mode.REMOTE && serverUrl != null && !serverUrl.isEmpty();
    }
}

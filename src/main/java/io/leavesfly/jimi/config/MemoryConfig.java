package io.leavesfly.jimi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 记忆模块配置
 * 基于 ReCAP 理念的记忆优化配置 + 长期记忆管理
 * 
 * @see <a href="https://github.com/ReCAP-Stanford/ReCAP">ReCAP: Recursive Context-Aware Reasoning and Planning</a>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jimi.memory")
public class MemoryConfig {
    
    /**
     * 有界提示最大 Token 数
     * 默认 4000，确保提示大小保持 O(1)
     */
    private int activePromptMaxTokens = 4000;
    
    /**
     * 关键发现窗口大小
     * 默认保留最近 5 条关键发现进入活动提示
     */
    private int insightsWindowSize = 5;
    
    /**
     * 是否启用 ReCAP 优化
     * 默认关闭，通过配置开关逐步启用
     */
    private boolean enableRecap = false;
    
    /**
     * 最大递归深度
     * 默认 5 层，防止无限递归
     */
    private int maxRecursionDepth = 5;
    
    // ==================== 长期记忆相关配置 ====================
    
    /**
     * 是否启用长期记忆
     * 默认关闭，通过配置开关启用
     */
    private boolean longTermEnabled = false;
    
    /**
     * 是否自动从工具结果提取知识
     * 默认开启（启用长期记忆时生效）
     */
    private boolean autoExtract = true;
    
    /**
     * 是否自动注入相关知识到上下文
     * 默认开启（启用长期记忆时生效）
     */
    private boolean autoInject = true;
    
    /**
     * 最多保留的知识条目数量
     * 默认 100 条，超出后按访问频率清理
     */
    private int maxInsights = 100;
    
    /**
     * 知识过期天数
     * 默认 90 天，超过此期限未访问的知识将被清理
     */
    private int insightExpiryDays = 90;
    
    /**
     * 最多保留的任务历史数量
     * 默认 50 条，超出后按时间清理
     */
    private int maxTaskHistory = 50;
}

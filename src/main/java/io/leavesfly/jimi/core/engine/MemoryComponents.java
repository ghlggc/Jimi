package io.leavesfly.jimi.core.engine;

import io.leavesfly.jimi.config.info.MemoryConfig;
import io.leavesfly.jimi.knowledge.memory.MemoryExtractor;
import io.leavesfly.jimi.knowledge.memory.MemoryInjector;
import lombok.Getter;

/**
 * Memory 组件封装
 * <p>
 * 将 MemoryConfig、MemoryInjector、MemoryExtractor 合并为一个组件，
 * 简化 AgentExecutor 的构造参数。
 */
@Getter
public class MemoryComponents {

    private final MemoryConfig config;
    private final MemoryInjector injector;
    private final MemoryExtractor extractor;

    public MemoryComponents(MemoryConfig config, MemoryInjector injector, MemoryExtractor extractor) {
        this.config = config;
        this.injector = injector;
        this.extractor = extractor;
    }

    /**
     * 检查 ReCAP 功能是否启用
     */
    public boolean isRecapEnabled() {
        return config != null && config.isEnableRecap();
    }

    /**
     * 检查记忆注入是否可用
     */
    public boolean isInjectorAvailable() {
        return injector != null;
    }

    /**
     * 检查记忆提取是否可用
     */
    public boolean isExtractorAvailable() {
        return extractor != null;
    }

    /**
     * 静态工厂方法
     */
    public static MemoryComponents of(MemoryConfig config, MemoryInjector injector, MemoryExtractor extractor) {
        return new MemoryComponents(config, injector, extractor);
    }

    /**
     * 创建仅包含配置的组件（用于简单场景）
     */
    public static MemoryComponents withConfig(MemoryConfig config) {
        return new MemoryComponents(config, null, null);
    }
}

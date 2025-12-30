package io.leavesfly.jimi.core.engine.provider;

import io.leavesfly.jimi.config.info.MemoryConfig;
import io.leavesfly.jimi.core.engine.MemoryComponents;
import io.leavesfly.jimi.core.engine.runtime.Runtime;
import io.leavesfly.jimi.knowledge.memory.MemoryExtractor;
import io.leavesfly.jimi.knowledge.memory.MemoryInjector;
import io.leavesfly.jimi.knowledge.memory.MemoryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 记忆组件提供者
 * <p>
 * 封装所有记忆相关的依赖：
 * - MemoryConfig: 记忆配置（ReCAP）
 * - MemoryManager: 长期记忆管理器
 * - MemoryInjector: 长期记忆注入器
 * - MemoryExtractor: 长期记忆提取器
 */
@Slf4j
@Component
public class MemoryComponentsProvider {

    @Autowired(required = false)
    private MemoryConfig memoryConfig;

    @Autowired(required = false)
    private MemoryManager memoryManager;

    @Autowired(required = false)
    private MemoryInjector memoryInjector;

    @Autowired(required = false)
    private MemoryExtractor memoryExtractor;

    /**
     * 获取记忆组件封装
     */
    public MemoryComponents getComponents() {
        return MemoryComponents.of(memoryConfig, memoryInjector, memoryExtractor);
    }

    /**
     * 获取记忆配置
     */
    public MemoryConfig getConfig() {
        return memoryConfig;
    }

    /**
     * 获取记忆管理器
     */
    public MemoryManager getManager() {
        return memoryManager;
    }

    /**
     * 检查长期记忆是否启用
     */
    public boolean isLongTermEnabled() {
        return memoryManager != null && memoryConfig != null && memoryConfig.isLongTermEnabled();
    }

    /**
     * 初始化记忆管理器
     */
    public void initializeIfEnabled(Runtime runtime) {
        if (isLongTermEnabled()) {
            log.info("初始化长期记忆管理器...");
            memoryManager.initialize(runtime);
        }
    }
}

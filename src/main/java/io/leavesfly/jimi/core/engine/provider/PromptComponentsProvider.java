package io.leavesfly.jimi.core.engine.provider;

import io.leavesfly.jimi.core.engine.context.ActivePromptBuilder;
import io.leavesfly.jimi.knowledge.retrieval.RetrievalPipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 提示词组件提供者
 * <p>
 * 封装所有提示词增强相关的依赖：
 * - RetrievalPipeline: RAG 检索管线
 * - ActivePromptBuilder: ReCAP 提示构建器
 */
@Component
public class PromptComponentsProvider {

    @Autowired(required = false)
    private RetrievalPipeline retrievalPipeline;

    @Autowired(required = false)
    private ActivePromptBuilder promptBuilder;

    /**
     * 获取 RAG 检索管线
     */
    public RetrievalPipeline getRetrievalPipeline() {
        return retrievalPipeline;
    }

    /**
     * 获取 ReCAP 提示构建器
     */
    public ActivePromptBuilder getPromptBuilder() {
        return promptBuilder;
    }

    /**
     * 检查 RAG 是否可用
     */
    public boolean isRagEnabled() {
        return retrievalPipeline != null;
    }

    /**
     * 检查 ReCAP 是否可用
     */
    public boolean isRecapEnabled() {
        return promptBuilder != null;
    }
}

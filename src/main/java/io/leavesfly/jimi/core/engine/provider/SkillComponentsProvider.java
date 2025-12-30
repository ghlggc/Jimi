package io.leavesfly.jimi.core.engine.provider;

import io.leavesfly.jimi.core.engine.SkillComponents;
import io.leavesfly.jimi.tool.skill.SkillMatcher;
import io.leavesfly.jimi.tool.skill.SkillProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 技能组件提供者
 * <p>
 * 封装所有技能相关的依赖：
 * - SkillMatcher: 技能匹配器
 * - SkillProvider: 技能提供者
 */
@Component
public class SkillComponentsProvider {

    @Autowired(required = false)
    private SkillMatcher skillMatcher;

    @Autowired(required = false)
    private SkillProvider skillProvider;

    /**
     * 获取技能组件封装
     */
    public SkillComponents getComponents() {
        return SkillComponents.of(skillMatcher, skillProvider);
    }

    /**
     * 获取技能匹配器
     */
    public SkillMatcher getMatcher() {
        return skillMatcher;
    }

    /**
     * 获取技能提供者
     */
    public SkillProvider getProvider() {
        return skillProvider;
    }

    /**
     * 检查技能功能是否可用
     */
    public boolean isAvailable() {
        return skillMatcher != null && skillProvider != null;
    }
}

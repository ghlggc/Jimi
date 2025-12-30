package io.leavesfly.jimi.core.engine;

import io.leavesfly.jimi.tool.skill.SkillMatcher;
import io.leavesfly.jimi.tool.skill.SkillProvider;
import lombok.Getter;

/**
 * Skill 组件封装
 * <p>
 * 将 SkillMatcher 和 SkillProvider 合并为一个组件，
 * 简化 AgentExecutor 的构造参数。
 */
@Getter
public class SkillComponents {

    private final SkillMatcher matcher;
    private final SkillProvider provider;

    public SkillComponents(SkillMatcher matcher, SkillProvider provider) {
        this.matcher = matcher;
        this.provider = provider;
    }

    /**
     * 检查组件是否可用（两者都非空）
     */
    public boolean isAvailable() {
        return matcher != null && provider != null;
    }

    /**
     * 静态工厂方法
     */
    public static SkillComponents of(SkillMatcher matcher, SkillProvider provider) {
        return new SkillComponents(matcher, provider);
    }
}

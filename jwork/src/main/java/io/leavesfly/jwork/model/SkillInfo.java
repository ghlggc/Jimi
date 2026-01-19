package io.leavesfly.jwork.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Skill 信息（用于 UI 展示）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillInfo {
    
    private String name;
    private String description;
    private String version;
    private String category;
    private Scope scope;
    private boolean installed;
    
    public enum Scope {
        GLOBAL,     // 全局 Skill
        PROJECT     // 项目级 Skill
    }
}

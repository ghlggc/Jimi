package io.leavesfly.jimi.command.custom;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义命令规范
 * 
 * 用于从 YAML 配置文件加载用户自定义命令
 * 配置文件位置: ~/.jimi/commands/*.yaml 或 <project>/.jimi/commands/*.yaml
 * 
 * 示例配置:
 * ```yaml
 * name: "quick-build"
 * description: "快速构建并运行测试"
 * category: "build"
 * priority: 10
 * aliases:
 *   - "qb"
 * usage: "/quick-build [--skip-tests]"
 * parameters:
 *   - name: "skip-tests"
 *     type: "boolean"
 *     default: "false"
 * execution:
 *   type: "script"
 *   script: "mvn clean install"
 * require_approval: false
 * ```
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomCommandSpec {
    
    /**
     * 命令名称 (必需)
     */
    private String name;
    
    /**
     * 命令描述 (必需)
     */
    private String description;
    
    /**
     * 命令分类 (可选, 默认 "custom")
     */
    @Builder.Default
    private String category = "custom";
    
    /**
     * 命令优先级 (可选, 默认 0)
     */
    @Builder.Default
    private int priority = 0;
    
    /**
     * 命令别名列表 (可选)
     */
    @Builder.Default
    private List<String> aliases = new ArrayList<>();
    
    /**
     * 命令用法说明 (可选)
     */
    private String usage;
    
    /**
     * 参数定义列表 (可选)
     */
    @Builder.Default
    private List<ParameterSpec> parameters = new ArrayList<>();
    
    /**
     * 执行配置 (必需)
     */
    private ExecutionSpec execution;
    
    /**
     * 前置条件列表 (可选)
     */
    @Builder.Default
    private List<PreconditionSpec> preconditions = new ArrayList<>();
    
    /**
     * 是否需要审批 (可选, 默认 false)
     */
    @Builder.Default
    private boolean requireApproval = false;
    
    /**
     * 是否启用 (可选, 默认 true)
     */
    @Builder.Default
    private boolean enabled = true;
    
    /**
     * 配置文件路径 (运行时设置)
     */
    private String configFilePath;
    
    /**
     * 验证配置有效性
     */
    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Command name is required");
        }
        
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Command description is required for: " + name);
        }
        
        if (execution == null) {
            throw new IllegalArgumentException("Execution config is required for: " + name);
        }
        
        execution.validate();
        
        // 验证参数
        if (parameters != null) {
            parameters.forEach(ParameterSpec::validate);
        }
        
        // 验证前置条件
        if (preconditions != null) {
            preconditions.forEach(PreconditionSpec::validate);
        }
    }
}

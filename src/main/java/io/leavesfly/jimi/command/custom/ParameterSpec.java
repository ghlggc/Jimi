package io.leavesfly.jimi.command.custom;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 命令参数规范
 * 
 * 定义自定义命令的参数信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParameterSpec {
    
    /**
     * 参数名称 (必需)
     */
    private String name;
    
    /**
     * 参数类型 (可选, 默认 "string")
     * 支持: string, boolean, integer, path
     */
    @Builder.Default
    private String type = "string";
    
    /**
     * 参数描述 (可选)
     */
    private String description;
    
    /**
     * 默认值 (可选)
     */
    private String defaultValue;
    
    /**
     * 是否必需 (可选, 默认 false)
     */
    @Builder.Default
    private boolean required = false;
    
    /**
     * 验证配置有效性
     */
    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter name is required");
        }
        
        // 验证参数类型
        if (!isValidType(type)) {
            throw new IllegalArgumentException(
                "Invalid parameter type '" + type + "' for parameter: " + name + 
                ". Supported types: string, boolean, integer, path"
            );
        }
    }
    
    /**
     * 检查参数类型是否有效
     */
    private boolean isValidType(String type) {
        return "string".equals(type) || 
               "boolean".equals(type) || 
               "integer".equals(type) || 
               "path".equals(type);
    }
}

package io.leavesfly.jimi.config.info;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 沙箱配置
 * 
 * 职责：
 * - 控制工具执行的安全边界
 * - 文件系统访问限制
 * - Shell 命令执行限制
 * - 网络访问限制
 * 
 * 设计参考 Claude Code 的沙箱机制
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxConfig {
    
    /**
     * 是否启用沙箱
     */
    @JsonProperty("enabled")
    @Builder.Default
    private boolean enabled = false;
    
    /**
     * 沙箱模式
     */
    @JsonProperty("mode")
    @NotNull
    @Builder.Default
    private SandboxMode mode = SandboxMode.RESTRICTED;
    
    /**
     * 文件系统配置
     */
    @JsonProperty("filesystem")
    @Valid
    @Builder.Default
    private FilesystemConfig filesystem = new FilesystemConfig();
    
    /**
     * Shell 执行配置
     */
    @JsonProperty("shell")
    @Valid
    @Builder.Default
    private ShellConfig shell = new ShellConfig();
    
    /**
     * 网络访问配置
     */
    @JsonProperty("network")
    @Valid
    @Builder.Default
    private NetworkConfig network = new NetworkConfig();
    
    /**
     * 需要审批的操作类型
     */
    @JsonProperty("require_approval_for")
    @Builder.Default
    private Set<String> requireApprovalFor = new HashSet<>();
    
    /**
     * 沙箱模式枚举
     */
    public enum SandboxMode {
        /**
         * 严格模式：所有潜在危险操作都需要审批
         */
        STRICT,
        
        /**
         * 受限模式：明确的危险操作需要审批（默认）
         */
        RESTRICTED,
        
        /**
         * 宽松模式：仅记录警告，不拦截
         */
        PERMISSIVE
    }
    
    /**
     * 文件系统配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilesystemConfig {
        
        /**
         * 是否允许在工作区外写入文件
         */
        @JsonProperty("allow_write_outside_workspace")
        @Builder.Default
        private boolean allowWriteOutsideWorkspace = false;
        
        /**
         * 额外允许的写入路径模式（支持 glob）
         */
        @JsonProperty("allowed_write_paths")
        @Builder.Default
        private List<String> allowedWritePaths = new ArrayList<>();
        
        /**
         * 禁止访问的路径模式（支持 glob）
         */
        @JsonProperty("denied_paths")
        @Builder.Default
        private List<String> deniedPaths = new ArrayList<>();
        
        /**
         * 最大文件大小（字节）
         */
        @JsonProperty("max_file_size")
        @Builder.Default
        private long maxFileSize = 10 * 1024 * 1024; // 10MB
    }
    
    /**
     * Shell 执行配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShellConfig {
        
        /**
         * 是否允许危险命令（不推荐）
         */
        @JsonProperty("allow_dangerous_commands")
        @Builder.Default
        private boolean allowDangerousCommands = false;
        
        /**
         * 危险命令模式列表
         */
        @JsonProperty("dangerous_patterns")
        @Builder.Default
        private List<String> dangerousPatterns = new ArrayList<>();
        
        /**
         * 允许的命令白名单（为空表示不启用白名单）
         */
        @JsonProperty("allowed_commands")
        @Builder.Default
        private List<String> allowedCommands = new ArrayList<>();
        
        /**
         * 最大执行时间（秒）
         */
        @JsonProperty("max_execution_time")
        @Builder.Default
        private int maxExecutionTime = 300; // 5分钟
    }
    
    /**
     * 网络访问配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkConfig {
        
        /**
         * 是否允许外部网络访问
         */
        @JsonProperty("allow_external_access")
        @Builder.Default
        private boolean allowExternalAccess = true;
        
        /**
         * 禁止访问的域名列表
         */
        @JsonProperty("denied_domains")
        @Builder.Default
        private List<String> deniedDomains = new ArrayList<>();
    }
}

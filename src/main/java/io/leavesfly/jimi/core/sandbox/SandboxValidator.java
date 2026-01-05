package io.leavesfly.jimi.core.sandbox;

import io.leavesfly.jimi.config.info.SandboxConfig;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.regex.Pattern;

/**
 * 沙箱验证器
 * 
 * 职责：
 * - 验证文件路径访问权限
 * - 验证 Shell 命令安全性
 * - 验证网络访问权限
 * - 根据沙箱配置返回验证结果
 * 
 * 设计参考 Claude Code 沙箱机制
 */
@Slf4j
@Component
public class SandboxValidator {
    
    private final SandboxConfig config;
    private final Path workspaceRoot;
    private final FileSystem fileSystem;
    
    public SandboxValidator(SandboxConfig config, Path workspaceRoot) {
        this.config = config;
        this.workspaceRoot = workspaceRoot != null ? workspaceRoot.toAbsolutePath().normalize() : null;
        this.fileSystem = FileSystems.getDefault();
    }
    
    /**
     * 验证文件路径是否允许操作
     * 
     * @param targetPath 目标路径
     * @param operation 操作类型
     * @return 验证结果
     */
    public ValidationResult validateFilePath(Path targetPath, FileOperation operation) {
        // 沙箱未启用，直接允许
        if (!config.isEnabled()) {
            return ValidationResult.allowed();
        }
        
        try {
            Path normalizedPath = targetPath.toAbsolutePath().normalize();
            
            // 1. 检查是否在拒绝列表中
            for (String deniedPattern : config.getFilesystem().getDeniedPaths()) {
                if (matchesPattern(normalizedPath, deniedPattern)) {
                    log.warn("Sandbox: Path {} matches denied pattern: {}", normalizedPath, deniedPattern);
                    return ValidationResult.denied(
                        "Path is in denied list: " + deniedPattern,
                        SandboxViolationType.DENIED_PATH
                    );
                }
            }
            
            // 2. 只对写操作检查工作区限制
            if (operation == FileOperation.WRITE || operation == FileOperation.DELETE) {
                // 检查是否在工作区内
                if (workspaceRoot != null && !isWithinWorkspace(normalizedPath)) {
                    // 如果不允许工作区外写入
                    if (!config.getFilesystem().isAllowWriteOutsideWorkspace()) {
                        // 检查是否在额外允许路径中
                        boolean inAllowedPath = false;
                        for (String allowedPattern : config.getFilesystem().getAllowedWritePaths()) {
                            if (matchesPattern(normalizedPath, allowedPattern)) {
                                inAllowedPath = true;
                                break;
                            }
                        }
                        
                        if (!inAllowedPath) {
                            log.warn("Sandbox: Write outside workspace detected: {}", normalizedPath);
                            return ValidationResult.requiresApproval(
                                "Write outside workspace: " + normalizedPath,
                                SandboxViolationType.OUTSIDE_WORKSPACE
                            );
                        }
                    }
                }
            }
            
            return ValidationResult.allowed();
            
        } catch (Exception e) {
            log.error("Sandbox: Error validating file path: {}", targetPath, e);
            return ValidationResult.denied(
                "Invalid path: " + e.getMessage(),
                SandboxViolationType.INVALID_PATH
            );
        }
    }
    
    /**
     * 验证 Shell 命令是否安全
     * 
     * @param command 命令字符串
     * @return 验证结果
     */
    public ValidationResult validateShellCommand(String command) {
        // 沙箱未启用，直接允许
        if (!config.isEnabled()) {
            return ValidationResult.allowed();
        }
        
        String trimmedCommand = command.trim();
        
        // 1. 检查危险模式
        if (!config.getShell().isAllowDangerousCommands()) {
            for (String pattern : config.getShell().getDangerousPatterns()) {
                if (containsPattern(trimmedCommand, pattern)) {
                    log.warn("Sandbox: Dangerous command pattern detected: {}", pattern);
                    return ValidationResult.denied(
                        "Dangerous command pattern detected: " + pattern,
                        SandboxViolationType.DANGEROUS_COMMAND
                    );
                }
            }
        }
        
        // 2. 白名单模式检查
        if (!config.getShell().getAllowedCommands().isEmpty()) {
            String commandName = extractCommandName(trimmedCommand);
            if (!config.getShell().getAllowedCommands().contains(commandName)) {
                log.warn("Sandbox: Command not in whitelist: {}", commandName);
                return ValidationResult.requiresApproval(
                    "Command not in whitelist: " + commandName,
                    SandboxViolationType.NOT_IN_WHITELIST
                );
            }
        }
        
        // 3. 检查危险的重定向
        if (Pattern.matches(".*>\\s*/dev/.*", trimmedCommand) ||
            Pattern.matches(".*>\\s*/etc/.*", trimmedCommand) ||
            Pattern.matches(".*>\\s*/usr/.*", trimmedCommand) ||
            Pattern.matches(".*>\\s*/System/.*", trimmedCommand)) {
            log.warn("Sandbox: Dangerous redirect detected in command");
            return ValidationResult.denied(
                "Redirect to system directory not allowed",
                SandboxViolationType.DANGEROUS_REDIRECT
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * 验证网络访问
     * 
     * @param url URL地址
     * @return 验证结果
     */
    public ValidationResult validateNetworkAccess(String url) {
        // 沙箱未启用，直接允许
        if (!config.isEnabled()) {
            return ValidationResult.allowed();
        }
        
        // 检查是否允许外部访问
        if (!config.getNetwork().isAllowExternalAccess()) {
            log.warn("Sandbox: External network access not allowed");
            return ValidationResult.requiresApproval(
                "External network access: " + url,
                SandboxViolationType.NETWORK_ACCESS
            );
        }
        
        // 检查被拒绝的域名
        for (String deniedDomain : config.getNetwork().getDeniedDomains()) {
            if (url.contains(deniedDomain)) {
                log.warn("Sandbox: Access to denied domain: {}", deniedDomain);
                return ValidationResult.denied(
                    "Access to denied domain: " + deniedDomain,
                    SandboxViolationType.DENIED_DOMAIN
                );
            }
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * 验证文件大小
     * 
     * @param contentSize 内容大小（字节）
     * @return 验证结果
     */
    public ValidationResult validateFileSize(long contentSize) {
        if (!config.isEnabled()) {
            return ValidationResult.allowed();
        }
        
        long maxSize = config.getFilesystem().getMaxFileSize();
        if (contentSize > maxSize) {
            log.warn("Sandbox: File size {} exceeds limit {}", contentSize, maxSize);
            return ValidationResult.denied(
                String.format("File size (%d bytes) exceeds limit (%d bytes)", contentSize, maxSize),
                SandboxViolationType.FILE_SIZE_EXCEEDED
            );
        }
        
        return ValidationResult.allowed();
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 检查路径是否在工作区内
     */
    private boolean isWithinWorkspace(Path path) {
        if (workspaceRoot == null) {
            return true; // 没有工作区概念，视为允许
        }
        
        try {
            Path normalized = path.toAbsolutePath().normalize();
            return normalized.startsWith(workspaceRoot);
        } catch (Exception e) {
            log.error("Error checking workspace containment", e);
            return false;
        }
    }
    
    /**
     * 路径模式匹配（支持 glob）
     */
    private boolean matchesPattern(Path path, String pattern) {
        String pathStr = path.toString();
        
        // 直接字符串包含匹配
        if (pathStr.contains(pattern)) {
            return true;
        }
        
        // Glob 模式匹配
        try {
            PathMatcher matcher = fileSystem.getPathMatcher("glob:" + pattern);
            return matcher.matches(path);
        } catch (Exception e) {
            log.debug("Invalid glob pattern: {}", pattern);
            return false;
        }
    }
    
    /**
     * 检查命令是否包含特定模式
     */
    private boolean containsPattern(String command, String pattern) {
        // 简单的字符串包含检查
        if (command.contains(pattern)) {
            return true;
        }
        
        // 正则表达式匹配（如果模式看起来像正则）
        try {
            if (pattern.contains(".*") || pattern.contains("\\")) {
                return Pattern.compile(pattern).matcher(command).find();
            }
        } catch (Exception e) {
            log.debug("Invalid regex pattern: {}", pattern);
        }
        
        return false;
    }
    
    /**
     * 提取命令名称
     */
    private String extractCommandName(String command) {
        String trimmed = command.trim();
        
        // 跳过环境变量定义
        if (trimmed.contains("=")) {
            int equalIndex = trimmed.indexOf('=');
            int spaceAfterEqual = trimmed.indexOf(' ', equalIndex);
            if (spaceAfterEqual > 0) {
                trimmed = trimmed.substring(spaceAfterEqual + 1).trim();
            }
        }
        
        // 提取第一个单词
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex);
        }
        
        return trimmed;
    }
    
    // ==================== 结果类 ====================
    
    /**
     * 验证结果
     */
    @Data
    @Builder
    public static class ValidationResult {
        private final boolean allowed;
        private final boolean requiresApproval;
        private final String reason;
        private final SandboxViolationType violationType;
        
        public static ValidationResult allowed() {
            return ValidationResult.builder()
                    .allowed(true)
                    .requiresApproval(false)
                    .build();
        }
        
        public static ValidationResult denied(String reason, SandboxViolationType type) {
            return ValidationResult.builder()
                    .allowed(false)
                    .requiresApproval(false)
                    .reason(reason)
                    .violationType(type)
                    .build();
        }
        
        public static ValidationResult requiresApproval(String reason, SandboxViolationType type) {
            return ValidationResult.builder()
                    .allowed(false)
                    .requiresApproval(true)
                    .reason(reason)
                    .violationType(type)
                    .build();
        }
    }
    
    /**
     * 沙箱违规类型
     */
    public enum SandboxViolationType {
        DENIED_PATH,
        OUTSIDE_WORKSPACE,
        DANGEROUS_COMMAND,
        NOT_IN_WHITELIST,
        DANGEROUS_REDIRECT,
        NETWORK_ACCESS,
        DENIED_DOMAIN,
        FILE_SIZE_EXCEEDED,
        INVALID_PATH
    }
    
    /**
     * 文件操作类型
     */
    public enum FileOperation {
        READ,
        WRITE,
        DELETE
    }
}

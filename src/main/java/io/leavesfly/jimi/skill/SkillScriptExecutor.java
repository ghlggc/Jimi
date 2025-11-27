package io.leavesfly.jimi.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jimi.engine.approval.Approval;
import io.leavesfly.jimi.tool.ToolResult;
import io.leavesfly.jimi.tool.bash.Bash;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Skill 脚本执行器
 * 
 * 职责：
 * - 执行 Skill 关联的脚本文件
 * - 支持多种脚本类型（bash, python, node 等）
 * - 提供环境变量注入和超时控制
 * - 处理脚本执行结果和错误
 * 
 * 设计特性：
 * - 复用 Bash 工具的命令执行能力
 * - 自动推断脚本类型
 * - 安全的路径验证
 * - 详细的日志记录
 * 
 * 注意:
 * - ToolRegistry 不是 Spring Bean，因此不能直接注入
 * - 使用 ApplicationContext 获取 Bash 工具原型实例
 * - 每次执行时创建新的 Bash 工具实例以确保隔离
 */
@Slf4j
@Service
public class SkillScriptExecutor {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired(required = false)
    private SkillConfig skillConfig;
    
    /**
     * 默认超时时间（秒）
     */
    private static final int DEFAULT_TIMEOUT = 60;
    
    /**
     * 脚本类型到执行器的映射
     */
    private static final Map<String, String> SCRIPT_EXECUTORS = Map.of(
        "bash", "/bin/bash",
        "sh", "/bin/sh",
        "python", "python3",
        "python3", "python3",
        "python2", "python2",
        "node", "node",
        "ruby", "ruby",
        "perl", "perl"
    );
    
    /**
     * 文件扩展名到脚本类型的映射
     */
    private static final Map<String, String> EXTENSION_TO_TYPE = Map.of(
        ".sh", "bash",
        ".bash", "bash",
        ".py", "python",
        ".js", "node",
        ".rb", "ruby",
        ".pl", "perl"
    );
    
    /**
     * 执行 Skill 脚本
     * 
     * @param skill Skill 规格
     * @param workDir 工作目录（用于解析相对路径）
     * @return 执行结果的 Mono
     */
    public Mono<ToolResult> executeScript(SkillSpec skill, Path workDir) {
        return Mono.defer(() -> {
            // 检查是否需要执行脚本
            if (!shouldExecuteScript(skill)) {
                log.debug("Skill '{}' does not require script execution", skill.getName());
                return Mono.just(ToolResult.ok("", "No script to execute"));
            }
            
            try {
                // 解析脚本路径
                Path scriptPath = resolveScriptPath(skill, workDir);
                
                // 验证脚本文件
                if (!Files.exists(scriptPath)) {
                    String error = String.format("Script file not found: %s", scriptPath);
                    log.error(error);
                    return Mono.just(ToolResult.error("", error, "Script not found"));
                }
                
                if (!Files.isReadable(scriptPath)) {
                    String error = String.format("Script file not readable: %s", scriptPath);
                    log.error(error);
                    return Mono.just(ToolResult.error("", error, "Script not readable"));
                }
                
                // 确定脚本类型
                String scriptType = determineScriptType(skill, scriptPath);
                
                // 构建执行命令
                String command = buildExecutionCommand(scriptType, scriptPath, skill.getScriptEnv());
                
                // 确定超时时间
                int timeout = determineTimeout(skill);
                
                log.info("Executing script for skill '{}': {} (timeout: {}s)", 
                        skill.getName(), scriptPath, timeout);
                
                // 使用 Bash 工具执行命令
                return executeCommand(command, timeout)
                        .doOnSuccess(result -> {
                            if (result.isOk()) {
                                log.info("Script execution completed successfully for skill '{}'", 
                                        skill.getName());
                            } else if (result.isError()) {
                                log.warn("Script execution failed for skill '{}': {}", 
                                        skill.getName(), result.getMessage());
                            }
                        })
                        .doOnError(e -> {
                            log.error("Error executing script for skill '{}'", skill.getName(), e);
                        });
                
            } catch (Exception e) {
                log.error("Failed to prepare script execution for skill '{}'", skill.getName(), e);
                return Mono.just(ToolResult.error(
                        "",
                        "Failed to prepare script: " + e.getMessage(), 
                        "Preparation failed"
                ));
            }
        });
    }
    
    /**
     * 判断是否应该执行脚本
     */
    private boolean shouldExecuteScript(SkillSpec skill) {
        // 检查全局配置
        if (!isScriptExecutionEnabled()) {
            log.debug("Script execution is disabled globally");
            return false;
        }
        
        // 检查 Skill 是否配置了脚本
        if (skill.getScriptPath() == null || skill.getScriptPath().isEmpty()) {
            return false;
        }
        
        // 检查是否自动执行
        return skill.isAutoExecute();
    }
    
    /**
     * 解析脚本路径
     */
    private Path resolveScriptPath(SkillSpec skill, Path workDir) {
        String scriptPath = skill.getScriptPath();
        
        // 如果是绝对路径，直接使用
        Path path = Path.of(scriptPath);
        if (path.isAbsolute()) {
            return path;
        }
        
        // 相对路径：相对于 Skill 文件所在目录
        Path skillDir = skill.getSkillFilePath() != null 
                ? skill.getSkillFilePath().getParent() 
                : workDir;
        
        if (skillDir == null) {
            skillDir = workDir;
        }
        
        return skillDir.resolve(scriptPath).normalize();
    }
    
    /**
     * 确定脚本类型
     */
    private String determineScriptType(SkillSpec skill, Path scriptPath) {
        // 优先使用显式配置的类型
        if (skill.getScriptType() != null && !skill.getScriptType().isEmpty()) {
            return skill.getScriptType().toLowerCase();
        }
        
        // 根据文件扩展名推断
        String fileName = scriptPath.getFileName().toString();
        for (Map.Entry<String, String> entry : EXTENSION_TO_TYPE.entrySet()) {
            if (fileName.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 默认使用 bash
        log.debug("Unable to determine script type for '{}', defaulting to bash", fileName);
        return "bash";
    }
    
    /**
     * 构建执行命令
     */
    private String buildExecutionCommand(String scriptType, Path scriptPath, Map<String, String> env) {
        String executor = SCRIPT_EXECUTORS.getOrDefault(scriptType, "/bin/bash");
        String absolutePath = scriptPath.toAbsolutePath().toString();
        
        // 如果有环境变量，构建带环境变量的命令
        if (env != null && !env.isEmpty()) {
            StringBuilder cmd = new StringBuilder();
            for (Map.Entry<String, String> entry : env.entrySet()) {
                cmd.append(entry.getKey())
                   .append("=")
                   .append(escapeShellValue(entry.getValue()))
                   .append(" ");
            }
            cmd.append(executor).append(" ").append(absolutePath);
            return cmd.toString();
        }
        
        return executor + " " + absolutePath;
    }
    
    /**
     * 转义 Shell 值
     */
    private String escapeShellValue(String value) {
        if (value.contains(" ") || value.contains("\"") || value.contains("'")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }
    
    /**
     * 确定超时时间
     */
    private int determineTimeout(SkillSpec skill) {
        // 优先使用 Skill 配置
        if (skill.getScriptTimeout() > 0) {
            return skill.getScriptTimeout();
        }
        
        // 使用全局配置
        if (skillConfig != null && skillConfig.getScriptExecution() != null) {
            int globalTimeout = skillConfig.getScriptExecution().getTimeout();
            if (globalTimeout > 0) {
                return globalTimeout;
            }
        }
        
        // 使用默认值
        return DEFAULT_TIMEOUT;
    }
    
    /**
     * 执行命令(通过 Bash 工具)
     * 
     * 注意:
     * - 从 Spring 容器获取 Bash 工具原型实例
     * - 每次执行都创建新实例以确保线程安全
     * - 使用 YOLO 模式的 Approval 自动批准
     */
    private Mono<ToolResult> executeCommand(String command, int timeout) {
        try {
            // 从 Spring 容器获取 Bash 工具原型实例
            Bash bash = applicationContext.getBean(Bash.class);
            
            // 设置自动批准(Skill 脚本执行使用 YOLO 模式自动批准)
            bash.setApproval(new Approval(true)); // true = YOLO mode
            
            // 构建 Bash 工具参数
            Bash.Params params = Bash.Params.builder()
                .command(command)
                .timeout(timeout)
                .build();
            
            // 直接调用 Bash 工具
            return bash.execute(params);
            
        } catch (Exception e) {
            log.error("Failed to execute command via Bash tool", e);
            return Mono.just(ToolResult.error(
                    "Command execution failed: " + e.getMessage(),
                    "Execution error"
            ));
        }
    }
    
    /**
     * 判断脚本执行是否全局启用
     */
    private boolean isScriptExecutionEnabled() {
        if (skillConfig != null && skillConfig.getScriptExecution() != null) {
            return skillConfig.getScriptExecution().isEnabled();
        }
        return true; // 默认启用
    }
}

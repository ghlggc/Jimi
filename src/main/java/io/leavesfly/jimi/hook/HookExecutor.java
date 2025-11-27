package io.leavesfly.jimi.hook;

import io.leavesfly.jimi.command.custom.ExecutionSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Hook 执行器
 * 
 * 职责:
 * - 执行 Hook 的脚本或命令
 * - 检查执行条件
 * - 处理异步执行
 * - 变量替换和环境准备
 */
@Slf4j
@Service
public class HookExecutor {
    
    /**
     * 执行 Hook
     * 
     * @param hook Hook 规范
     * @param context Hook 上下文
     * @return 异步执行结果
     */
    public Mono<Void> execute(HookSpec hook, HookContext context) {
        log.debug("Executing hook: {} (type={})", hook.getName(), hook.getTrigger().getType());
        
        // 检查执行条件
        if (!checkConditions(hook.getConditions(), context)) {
            log.debug("Hook conditions not met: {}", hook.getName());
            return Mono.empty();
        }
        
        ExecutionSpec execution = hook.getExecution();
        
        // 根据执行类型执行
        Mono<Void> executionMono = switch (execution.getType()) {
            case "script" -> executeScript(hook, context);
            case "agent" -> executeAgent(hook, context);
            case "composite" -> executeComposite(hook, context);
            default -> {
                log.error("Unknown execution type: {}", execution.getType());
                yield Mono.empty();
            }
        };
        
        // 如果配置为异步执行,在后台线程运行
        // 注意: ExecutionSpec 需要添加 async 字段
        return executionMono.subscribeOn(Schedulers.parallel());
    }
    
    /**
     * 检查执行条件
     */
    private boolean checkConditions(List<HookCondition> conditions, HookContext context) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        
        for (HookCondition condition : conditions) {
            if (!checkCondition(condition, context)) {
                log.debug("Condition not met: {}", condition.getDescription());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查单个条件
     */
    private boolean checkCondition(HookCondition condition, HookContext context) {
        return switch (condition.getType()) {
            case "env_var" -> checkEnvVar(condition);
            case "file_exists" -> checkFileExists(condition, context);
            case "script" -> checkScript(condition, context);
            case "tool_result_contains" -> checkToolResultContains(condition, context);
            default -> {
                log.warn("Unknown condition type: {}", condition.getType());
                yield false;
            }
        };
    }
    
    /**
     * 检查环境变量条件
     */
    private boolean checkEnvVar(HookCondition condition) {
        String value = System.getenv(condition.getVar());
        if (value == null) {
            return false;
        }
        if (condition.getValue() != null) {
            return value.equals(condition.getValue());
        }
        return true;
    }
    
    /**
     * 检查文件存在条件
     */
    private boolean checkFileExists(HookCondition condition, HookContext context) {
        Path path = resolvePath(condition.getPath(), context);
        return Files.exists(path);
    }
    
    /**
     * 检查脚本条件
     */
    private boolean checkScript(HookCondition condition, HookContext context) {
        try {
            String script = replaceVariables(condition.getScript(), context);
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", script);
            pb.directory(context.getWorkDir().toFile());
            
            Process process = pb.start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                return false;
            }
            
            return process.exitValue() == 0;
            
        } catch (Exception e) {
            log.error("Failed to check script condition", e);
            return false;
        }
    }
    
    /**
     * 检查工具结果包含条件
     */
    private boolean checkToolResultContains(HookCondition condition, HookContext context) {
        if (context.getToolResult() == null) {
            return false;
        }
        return context.getToolResult().matches(condition.getPattern());
    }
    
    /**
     * 执行脚本类型 Hook
     */
    private Mono<Void> executeScript(HookSpec hook, HookContext context) {
        return Mono.fromRunnable(() -> {
            try {
                ExecutionSpec execution = hook.getExecution();
                
                // 获取脚本内容
                String script = getScriptContent(execution);
                script = replaceVariables(script, context);
                
                // 构建环境变量
                Map<String, String> env = buildEnvironment(execution, context);
                
                // 执行脚本
                ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", script);
                pb.directory(context.getWorkDir().toFile());
                pb.redirectErrorStream(true);
                
                if (env != null && !env.isEmpty()) {
                    pb.environment().putAll(env);
                }
                
                Process process = pb.start();
                
                // 读取输出
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("[Hook:{}] {}", hook.getName(), line);
                    }
                }
                
                // 等待完成
                int timeout = execution.getTimeout();
                boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
                
                if (!completed) {
                    process.destroyForcibly();
                    log.error("Hook script timeout: {}", hook.getName());
                    return;
                }
                
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    log.error("Hook script failed with exit code {}: {}", 
                            exitCode, hook.getName());
                } else {
                    log.info("Hook executed successfully: {}", hook.getName());
                }
                
            } catch (Exception e) {
                log.error("Failed to execute hook script: {}", hook.getName(), e);
            }
        });
    }
    
    /**
     * 执行 Agent 委托类型 Hook
     */
    private Mono<Void> executeAgent(HookSpec hook, HookContext context) {
        return Mono.fromRunnable(() -> {
            // TODO: 实现 Agent 委托
            log.warn("Agent execution not yet implemented for hook: {}", hook.getName());
        });
    }
    
    /**
     * 执行组合类型 Hook
     */
    private Mono<Void> executeComposite(HookSpec hook, HookContext context) {
        return Mono.fromRunnable(() -> {
            // TODO: 实现组合执行
            log.warn("Composite execution not yet implemented for hook: {}", hook.getName());
        });
    }
    
    /**
     * 获取脚本内容
     */
    private String getScriptContent(ExecutionSpec execution) throws Exception {
        if (execution.getScriptFile() != null && !execution.getScriptFile().trim().isEmpty()) {
            Path scriptPath = Path.of(execution.getScriptFile());
            if (!Files.exists(scriptPath)) {
                throw new IllegalStateException("Script file not found: " + scriptPath);
            }
            return Files.readString(scriptPath);
        }
        return execution.getScript();
    }
    
    /**
     * 替换变量
     */
    private String replaceVariables(String text, HookContext context) {
        if (text == null) {
            return null;
        }
        
        String result = text;
        
        // 替换工作目录
        if (context.getWorkDir() != null) {
            result = result.replace("${JIMI_WORK_DIR}", context.getWorkDir().toString());
        }
        
        // 替换 HOME
        result = result.replace("${HOME}", System.getProperty("user.home"));
        
        // 替换工具相关变量
        if (context.getToolName() != null) {
            result = result.replace("${TOOL_NAME}", context.getToolName());
        }
        
        if (context.getToolResult() != null) {
            result = result.replace("${TOOL_RESULT}", context.getToolResult());
        }
        
        // 替换文件相关变量
        if (!context.getAffectedFiles().isEmpty()) {
            String files = String.join(" ", context.getAffectedFilePaths());
            result = result.replace("${MODIFIED_FILES}", files);
            result = result.replace("${MODIFIED_FILE}", 
                    context.getAffectedFiles().get(0).toString());
        }
        
        // 替换 Agent 相关变量
        if (context.getAgentName() != null) {
            result = result.replace("${AGENT_NAME}", context.getAgentName());
            result = result.replace("${CURRENT_AGENT}", context.getAgentName());
        }
        
        if (context.getPreviousAgentName() != null) {
            result = result.replace("${PREVIOUS_AGENT}", context.getPreviousAgentName());
        }
        
        // 替换错误相关变量
        if (context.getErrorMessage() != null) {
            result = result.replace("${ERROR_MESSAGE}", context.getErrorMessage());
        }
        
        return result;
    }
    
    /**
     * 构建环境变量
     */
    private Map<String, String> buildEnvironment(ExecutionSpec execution, HookContext context) {
        Map<String, String> env = new HashMap<>();
        
        // 复制执行配置中的环境变量
        if (execution.getEnvironment() != null) {
            execution.getEnvironment().forEach((key, value) -> {
                env.put(key, replaceVariables(value, context));
            });
        }
        
        // 添加上下文变量
        if (context.getToolName() != null) {
            env.put("HOOK_TOOL_NAME", context.getToolName());
        }
        
        if (context.getAgentName() != null) {
            env.put("HOOK_AGENT_NAME", context.getAgentName());
        }
        
        return env;
    }
    
    /**
     * 解析路径
     */
    private Path resolvePath(String pathStr, HookContext context) {
        String resolved = replaceVariables(pathStr, context);
        Path path = Path.of(resolved);
        
        if (!path.isAbsolute() && context.getWorkDir() != null) {
            path = context.getWorkDir().resolve(path);
        }
        
        return path;
    }
}

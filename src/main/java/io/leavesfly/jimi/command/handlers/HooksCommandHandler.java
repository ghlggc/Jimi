package io.leavesfly.jimi.command.handlers;

import io.leavesfly.jimi.command.CommandContext;
import io.leavesfly.jimi.command.CommandHandler;
import io.leavesfly.jimi.hook.HookRegistry;
import io.leavesfly.jimi.hook.HookSpec;
import io.leavesfly.jimi.hook.HookType;
import io.leavesfly.jimi.ui.shell.output.OutputFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * /hooks 命令处理器
 * 
 * 管理 Hooks:
 * - /hooks: 列出所有 Hook
 * - /hooks <name>: 查看指定 Hook 的详细信息
 * - /hooks reload: 重新加载所有 Hooks
 * - /hooks enable <name>: 启用 Hook
 * - /hooks disable <name>: 禁用 Hook
 */
@Slf4j
@Component
public class HooksCommandHandler implements CommandHandler {
    
    @Autowired
    private HookRegistry hookRegistry;
    
    @Override
    public String getName() {
        return "hooks";
    }
    
    @Override
    public String getDescription() {
        return "管理 Hooks";
    }
    
    @Override
    public String getUsage() {
        return "/hooks [list|<name>|reload|enable <name>|disable <name>]";
    }
    
    @Override
    public String getCategory() {
        return "system";
    }
    
    @Override
    public void execute(CommandContext context) throws Exception {
        OutputFormatter out = context.getOutputFormatter();
        
        // 无参数 - 列出所有 Hooks
        if (context.getArgCount() == 0) {
            listAllHooks(out);
            return;
        }
        
        String subCommand = context.getArg(0);
        
        switch (subCommand) {
            case "list":
                listAllHooks(out);
                break;
                
            case "reload":
                reloadHooks(out);
                break;
                
            case "enable":
                if (context.getArgCount() < 2) {
                    out.printError("用法: /hooks enable <hook-name>");
                    return;
                }
                enableHook(context.getArg(1), out);
                break;
                
            case "disable":
                if (context.getArgCount() < 2) {
                    out.printError("用法: /hooks disable <hook-name>");
                    return;
                }
                disableHook(context.getArg(1), out);
                break;
                
            default:
                // 查看指定 Hook 详情
                showHookDetails(subCommand, out);
                break;
        }
    }
    
    /**
     * 列出所有 Hooks
     */
    private void listAllHooks(OutputFormatter out) {
        Map<HookType, Integer> stats = hookRegistry.getHookStatistics();
        int total = hookRegistry.getHookCount();
        
        out.println();
        out.printSuccess("Hooks 列表 (" + total + " 个):");
        out.println();
        
        if (total == 0) {
            out.println("  暂无 Hook");
            out.println();
            out.printInfo("提示: 在 ~/.jimi/hooks/ 或 <project>/.jimi/hooks/ 目录下");
            out.printInfo("      创建 YAML 配置文件来添加 Hooks");
            out.println();
            return;
        }
        
        // 按类型分组显示
        for (HookType type : HookType.values()) {
            List<HookSpec> hooks = hookRegistry.getHooks(type);
            if (!hooks.isEmpty()) {
                out.println("📍 " + type.name());
                hooks.forEach(hook -> {
                    String status = hook.isEnabled() ? "✅" : "❌";
                    out.println(String.format("  %s %-25s - %s (优先级: %d)", 
                            status, hook.getName(), hook.getDescription(), hook.getPriority()));
                });
                out.println();
            }
        }
        
        out.printInfo("使用 '/hooks <name>' 查看 Hook 详情");
        out.println();
    }
    
    /**
     * 显示 Hook 详情
     */
    private void showHookDetails(String hookName, OutputFormatter out) {
        HookSpec hook = hookRegistry.getHook(hookName);
        
        if (hook == null) {
            out.printError("未找到 Hook: " + hookName);
            out.printInfo("使用 '/hooks' 查看所有 Hooks");
            return;
        }
        
        out.println();
        out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        out.printSuccess("Hook 详情: " + hook.getName());
        out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        out.println();
        
        // 基本信息
        out.println("📝 基本信息:");
        out.println("  名称:     " + hook.getName());
        out.println("  描述:     " + hook.getDescription());
        out.println("  状态:     " + (hook.isEnabled() ? "✅ 启用" : "❌ 禁用"));
        out.println("  优先级:   " + hook.getPriority());
        out.println();
        
        // 触发配置
        out.println("🎯 触发配置:");
        out.println("  类型:     " + hook.getTrigger().getType());
        
        if (!hook.getTrigger().getTools().isEmpty()) {
            out.println("  工具:     " + String.join(", ", hook.getTrigger().getTools()));
        }
        
        if (!hook.getTrigger().getFilePatterns().isEmpty()) {
            out.println("  文件模式: " + String.join(", ", hook.getTrigger().getFilePatterns()));
        }
        
        if (hook.getTrigger().getAgentName() != null) {
            out.println("  Agent:    " + hook.getTrigger().getAgentName());
        }
        
        if (hook.getTrigger().getErrorPattern() != null) {
            out.println("  错误模式: " + hook.getTrigger().getErrorPattern());
        }
        out.println();
        
        // 执行配置
        out.println("⚙️  执行配置:");
        out.println("  类型:     " + hook.getExecution().getType());
        
        switch (hook.getExecution().getType()) {
            case "script":
                if (hook.getExecution().getScriptFile() != null) {
                    out.println("  脚本文件: " + hook.getExecution().getScriptFile());
                } else {
                    String script = hook.getExecution().getScript();
                    String preview = script.length() > 50 ? 
                            script.substring(0, 47) + "..." : script;
                    out.println("  脚本:     " + preview);
                }
                out.println("  超时:     " + hook.getExecution().getTimeout() + "秒");
                break;
                
            case "agent":
                out.println("  Agent:    " + hook.getExecution().getAgent());
                out.println("  任务:     " + hook.getExecution().getTask());
                break;
                
            case "composite":
                out.println("  步骤数:   " + hook.getExecution().getSteps().size());
                break;
        }
        out.println();
        
        // 条件
        if (!hook.getConditions().isEmpty()) {
            out.println("⚠️  执行条件:");
            hook.getConditions().forEach(cond -> {
                out.println("  • " + cond.getType() + 
                        (cond.getDescription() != null ? ": " + cond.getDescription() : ""));
            });
            out.println();
        }
        
        // 其他信息
        out.println("ℹ️  其他信息:");
        out.println("  配置文件: " + hook.getConfigFilePath());
        out.println();
    }
    
    /**
     * 重新加载 Hooks
     */
    private void reloadHooks(OutputFormatter out) {
        out.println();
        out.println("正在重新加载 Hooks...");
        
        try {
            int before = hookRegistry.getHookCount();
            hookRegistry.reloadHooks();
            int after = hookRegistry.getHookCount();
            
            out.printSuccess("重新加载完成!");
            out.println("  加载前: " + before + " 个 Hook");
            out.println("  加载后: " + after + " 个 Hook");
            
            if (after > before) {
                out.printSuccess("新增 " + (after - before) + " 个 Hook");
            } else if (after < before) {
                out.printWarning("减少 " + (before - after) + " 个 Hook");
            }
            
        } catch (Exception e) {
            out.printError("重新加载失败: " + e.getMessage());
            log.error("Failed to reload hooks", e);
        }
        
        out.println();
    }
    
    /**
     * 启用 Hook
     */
    private void enableHook(String hookName, OutputFormatter out) {
        if (!hookRegistry.hasHook(hookName)) {
            out.printError("未找到 Hook: " + hookName);
            return;
        }
        
        hookRegistry.enableHook(hookName);
        out.printSuccess("已启用 Hook: " + hookName);
    }
    
    /**
     * 禁用 Hook
     */
    private void disableHook(String hookName, OutputFormatter out) {
        if (!hookRegistry.hasHook(hookName)) {
            out.printError("未找到 Hook: " + hookName);
            return;
        }
        
        hookRegistry.disableHook(hookName);
        out.printWarning("已禁用 Hook: " + hookName);
    }
}

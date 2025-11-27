package io.leavesfly.jimi.command.handlers;

import io.leavesfly.jimi.command.CommandContext;
import io.leavesfly.jimi.command.CommandHandler;
import io.leavesfly.jimi.command.custom.CustomCommandRegistry;
import io.leavesfly.jimi.command.custom.CustomCommandSpec;
import io.leavesfly.jimi.ui.shell.output.OutputFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * /commands 命令处理器
 * 
 * 管理自定义命令:
 * - /commands: 列出所有自定义命令
 * - /commands <name>: 查看指定命令的详细信息
 * - /commands reload: 重新加载所有自定义命令
 * - /commands enable <name>: 启用命令
 * - /commands disable <name>: 禁用命令
 */
@Slf4j
@Component
public class CommandsCommandHandler implements CommandHandler {
    
    @Autowired
    private CustomCommandRegistry customCommandRegistry;
    
    @Override
    public String getName() {
        return "commands";
    }
    
    @Override
    public String getDescription() {
        return "管理自定义命令";
    }
    
    @Override
    public List<String> getAliases() {
        return List.of("cmds");
    }
    
    @Override
    public String getUsage() {
        return "/commands [list|<name>|reload|enable <name>|disable <name>]";
    }
    
    @Override
    public String getCategory() {
        return "system";
    }
    
    @Override
    public void execute(CommandContext context) throws Exception {
        OutputFormatter out = context.getOutputFormatter();
        
        // 无参数 - 列出所有自定义命令
        if (context.getArgCount() == 0) {
            listAllCommands(out);
            return;
        }
        
        String subCommand = context.getArg(0);
        
        switch (subCommand) {
            case "list":
                listAllCommands(out);
                break;
                
            case "reload":
                reloadCommands(out);
                break;
                
            case "enable":
                if (context.getArgCount() < 2) {
                    out.printError("用法: /commands enable <command-name>");
                    return;
                }
                enableCommand(context.getArg(1), out);
                break;
                
            case "disable":
                if (context.getArgCount() < 2) {
                    out.printError("用法: /commands disable <command-name>");
                    return;
                }
                disableCommand(context.getArg(1), out);
                break;
                
            default:
                // 查看指定命令详情
                showCommandDetails(subCommand, out);
                break;
        }
    }
    
    /**
     * 列出所有自定义命令
     */
    private void listAllCommands(OutputFormatter out) {
        List<CustomCommandSpec> commands = customCommandRegistry.getAllCustomCommands();
        
        out.println();
        out.printSuccess("自定义命令列表 (" + commands.size() + " 个):");
        out.println();
        
        if (commands.isEmpty()) {
            out.println("  暂无自定义命令");
            out.println();
            out.printInfo("提示: 在 ~/.jimi/commands/ 或 <project>/.jimi/commands/ 目录下");
            out.printInfo("      创建 YAML 配置文件来添加自定义命令");
            out.println();
            return;
        }
        
        // 按分类组织
        commands.stream()
                .collect(java.util.stream.Collectors.groupingBy(CustomCommandSpec::getCategory))
                .forEach((category, categoryCommands) -> {
                    out.println("📦 " + category.toUpperCase());
                    categoryCommands.forEach(cmd -> {
                        String status = cmd.isEnabled() ? "✅" : "❌";
                        String aliases = cmd.getAliases().isEmpty() ? "" : 
                                " [" + String.join(", ", cmd.getAliases()) + "]";
                        out.println(String.format("  %s %-20s - %s%s", 
                                status, cmd.getName(), cmd.getDescription(), aliases));
                    });
                    out.println();
                });
        
        out.printInfo("使用 '/commands <name>' 查看命令详情");
        out.println();
    }
    
    /**
     * 显示命令详情
     */
    private void showCommandDetails(String commandName, OutputFormatter out) {
        CustomCommandSpec spec = customCommandRegistry.getCommandSpec(commandName);
        
        if (spec == null) {
            out.printError("未找到自定义命令: " + commandName);
            out.printInfo("使用 '/commands' 查看所有自定义命令");
            return;
        }
        
        out.println();
        out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        out.printSuccess("命令详情: " + spec.getName());
        out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        out.println();
        
        // 基本信息
        out.println("📝 基本信息:");
        out.println("  名称:     " + spec.getName());
        out.println("  描述:     " + spec.getDescription());
        out.println("  分类:     " + spec.getCategory());
        out.println("  状态:     " + (spec.isEnabled() ? "✅ 启用" : "❌ 禁用"));
        out.println("  优先级:   " + spec.getPriority());
        out.println("  用法:     " + spec.getUsage());
        
        if (!spec.getAliases().isEmpty()) {
            out.println("  别名:     " + String.join(", ", spec.getAliases()));
        }
        out.println();
        
        // 执行配置
        out.println("⚙️  执行配置:");
        out.println("  类型:     " + spec.getExecution().getType());
        
        switch (spec.getExecution().getType()) {
            case "script":
                if (spec.getExecution().getScriptFile() != null) {
                    out.println("  脚本文件: " + spec.getExecution().getScriptFile());
                } else {
                    out.println("  脚本:     " + (spec.getExecution().getScript().length() > 50 ? 
                            spec.getExecution().getScript().substring(0, 47) + "..." : 
                            spec.getExecution().getScript()));
                }
                out.println("  超时:     " + spec.getExecution().getTimeout() + "秒");
                break;
                
            case "agent":
                out.println("  Agent:    " + spec.getExecution().getAgent());
                out.println("  任务:     " + spec.getExecution().getTask());
                break;
                
            case "composite":
                out.println("  步骤数:   " + spec.getExecution().getSteps().size());
                break;
        }
        out.println();
        
        // 参数
        if (!spec.getParameters().isEmpty()) {
            out.println("📋 参数:");
            spec.getParameters().forEach(param -> {
                String required = param.isRequired() ? " (必需)" : " (可选)";
                String defaultValue = param.getDefaultValue() != null ? 
                        ", 默认: " + param.getDefaultValue() : "";
                out.println(String.format("  • %s [%s]%s%s", 
                        param.getName(), param.getType(), required, defaultValue));
                if (param.getDescription() != null) {
                    out.println("    " + param.getDescription());
                }
            });
            out.println();
        }
        
        // 前置条件
        if (!spec.getPreconditions().isEmpty()) {
            out.println("⚠️  前置条件:");
            spec.getPreconditions().forEach(pre -> {
                out.println("  • " + pre.getType() + ": " + 
                        (pre.getPath() != null ? pre.getPath() : 
                         pre.getVar() != null ? pre.getVar() : pre.getCommand()));
            });
            out.println();
        }
        
        // 其他信息
        out.println("ℹ️  其他信息:");
        out.println("  需要审批: " + (spec.isRequireApproval() ? "是" : "否"));
        out.println("  配置文件: " + spec.getConfigFilePath());
        out.println();
    }
    
    /**
     * 重新加载自定义命令
     */
    private void reloadCommands(OutputFormatter out) {
        out.println();
        out.println("正在重新加载自定义命令...");
        
        try {
            int before = customCommandRegistry.getCommandCount();
            customCommandRegistry.reloadCommands();
            int after = customCommandRegistry.getCommandCount();
            
            out.printSuccess("重新加载完成!");
            out.println("  加载前: " + before + " 个命令");
            out.println("  加载后: " + after + " 个命令");
            
            if (after > before) {
                out.printSuccess("新增 " + (after - before) + " 个命令");
            } else if (after < before) {
                out.printWarning("减少 " + (before - after) + " 个命令");
            }
            
        } catch (Exception e) {
            out.printError("重新加载失败: " + e.getMessage());
            log.error("Failed to reload custom commands", e);
        }
        
        out.println();
    }
    
    /**
     * 启用命令
     */
    private void enableCommand(String commandName, OutputFormatter out) {
        if (!customCommandRegistry.isCustomCommand(commandName)) {
            out.printError("未找到自定义命令: " + commandName);
            return;
        }
        
        customCommandRegistry.enableCommand(commandName);
        out.printSuccess("已启用命令: " + commandName);
    }
    
    /**
     * 禁用命令
     */
    private void disableCommand(String commandName, OutputFormatter out) {
        if (!customCommandRegistry.isCustomCommand(commandName)) {
            out.printError("未找到自定义命令: " + commandName);
            return;
        }
        
        customCommandRegistry.disableCommand(commandName);
        out.printWarning("已禁用命令: " + commandName);
    }
}

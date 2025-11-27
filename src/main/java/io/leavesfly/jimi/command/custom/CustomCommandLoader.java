package io.leavesfly.jimi.command.custom;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jimi.exception.ConfigException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * 自定义命令加载器
 * 
 * 职责:
 * - 从文件系统扫描和加载自定义命令配置
 * - 支持从多个位置加载: 类路径、用户主目录、项目目录
 * - 解析 YAML 配置文件
 * 
 * 加载策略:
 * - 类路径: resources/commands/ (内置示例命令)
 * - 用户主目录: ~/.jimi/commands/
 * - 项目目录: <project>/.jimi/commands/
 * - 优先级: 项目目录 > 用户主目录 > 类路径
 */
@Slf4j
@Service
public class CustomCommandLoader {
    
    @Autowired
    @Qualifier("yamlObjectMapper")
    private ObjectMapper yamlObjectMapper;
    
    /**
     * 从所有位置加载自定义命令
     * 
     * @param projectDir 项目目录 (可选)
     * @return 自定义命令列表
     */
    public List<CustomCommandSpec> loadAllCommands(Path projectDir) {
        List<CustomCommandSpec> allCommands = new ArrayList<>();
        
        // 1. 加载类路径中的命令 (内置示例)
        List<CustomCommandSpec> classpathCommands = loadCommandsFromClasspath();
        allCommands.addAll(classpathCommands);
        log.debug("Loaded {} commands from classpath", classpathCommands.size());
        
        // 2. 加载用户主目录的命令
        List<CustomCommandSpec> userCommands = loadCommandsFromUserHome();
        allCommands.addAll(userCommands);
        log.debug("Loaded {} commands from user home", userCommands.size());
        
        // 3. 加载项目目录的命令 (如果提供)
        if (projectDir != null) {
            List<CustomCommandSpec> projectCommands = loadCommandsFromProject(projectDir);
            allCommands.addAll(projectCommands);
            log.debug("Loaded {} commands from project directory", projectCommands.size());
        }
        
        log.info("Total {} custom commands loaded", allCommands.size());
        return allCommands;
    }
    
    /**
     * 从类路径加载命令
     */
    private List<CustomCommandSpec> loadCommandsFromClasspath() {
        try {
            URL resource = getClass().getClassLoader().getResource("commands");
            if (resource == null) {
                log.debug("No commands directory found in classpath");
                return Collections.emptyList();
            }
            
            // JAR 包中运行时不支持直接遍历
            if (resource.getProtocol().equals("jar")) {
                log.debug("Running from JAR, skipping classpath commands");
                return Collections.emptyList();
            }
            
            Path commandsDir = Paths.get(resource.toURI());
            return loadCommandsFromDirectory(commandsDir, "classpath");
            
        } catch (Exception e) {
            log.warn("Failed to load commands from classpath", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 从用户主目录加载命令
     */
    private List<CustomCommandSpec> loadCommandsFromUserHome() {
        String userHome = System.getProperty("user.home");
        Path commandsDir = Paths.get(userHome, ".jimi", "commands");
        
        if (!Files.exists(commandsDir)) {
            log.debug("User commands directory not found: {}", commandsDir);
            return Collections.emptyList();
        }
        
        return loadCommandsFromDirectory(commandsDir, "user");
    }
    
    /**
     * 从项目目录加载命令
     */
    private List<CustomCommandSpec> loadCommandsFromProject(Path projectDir) {
        Path commandsDir = projectDir.resolve(".jimi").resolve("commands");
        
        if (!Files.exists(commandsDir)) {
            log.debug("Project commands directory not found: {}", commandsDir);
            return Collections.emptyList();
        }
        
        return loadCommandsFromDirectory(commandsDir, "project");
    }
    
    /**
     * 从指定目录加载所有命令配置文件
     * 
     * @param directory 命令配置目录
     * @param source 来源标识 (用于日志)
     * @return 命令列表
     */
    private List<CustomCommandSpec> loadCommandsFromDirectory(Path directory, String source) {
        List<CustomCommandSpec> commands = new ArrayList<>();
        
        if (!Files.isDirectory(directory)) {
            return commands;
        }
        
        try (Stream<Path> paths = Files.walk(directory, 1)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().endsWith(".yaml") || 
                             p.getFileName().toString().endsWith(".yml"))
                 .forEach(file -> {
                     try {
                         CustomCommandSpec spec = parseCommandFile(file);
                         if (spec != null) {
                             spec.setConfigFilePath(file.toString());
                             commands.add(spec);
                             log.debug("Loaded command '{}' from {} ({})", 
                                     spec.getName(), source, file.getFileName());
                         }
                     } catch (Exception e) {
                         log.error("Failed to load command from {}: {}", 
                                 file, e.getMessage());
                     }
                 });
        } catch (IOException e) {
            log.error("Failed to scan commands directory: {}", directory, e);
        }
        
        return commands;
    }
    
    /**
     * 解析单个命令配置文件
     * 
     * @param file 配置文件路径
     * @return 命令规范
     */
    public CustomCommandSpec parseCommandFile(Path file) {
        try {
            log.debug("Parsing command file: {}", file);
            
            // 读取并解析 YAML
            CustomCommandSpec spec = yamlObjectMapper.readValue(
                file.toFile(), 
                CustomCommandSpec.class
            );
            
            // 验证配置
            spec.validate();
            
            return spec;
            
        } catch (IOException e) {
            throw new ConfigException("Failed to parse command file: " + file, e);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Invalid command configuration in " + file + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取用户命令目录路径
     */
    public Path getUserCommandsDirectory() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".jimi", "commands");
    }
    
    /**
     * 获取项目命令目录路径
     */
    public Path getProjectCommandsDirectory(Path projectDir) {
        return projectDir.resolve(".jimi").resolve("commands");
    }
    
    /**
     * 确保用户命令目录存在
     */
    public void ensureUserCommandsDirectory() {
        Path dir = getUserCommandsDirectory();
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("Created user commands directory: {}", dir);
            }
        } catch (IOException e) {
            log.error("Failed to create user commands directory: {}", dir, e);
        }
    }
}

package io.leavesfly.jimi.hook;

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
 * Hook 加载器
 * 
 * 职责:
 * - 从文件系统扫描和加载 Hook 配置
 * - 支持从多个位置加载: 类路径、用户主目录、项目目录
 * - 解析 YAML 配置文件
 * 
 * 加载策略:
 * - 类路径: resources/hooks/ (内置示例)
 * - 用户主目录: ~/.jimi/hooks/
 * - 项目目录: <project>/.jimi/hooks/
 * - 优先级: 项目目录 > 用户主目录 > 类路径
 */
@Slf4j
@Service
public class HookLoader {
    
    @Autowired
    @Qualifier("yamlObjectMapper")
    private ObjectMapper yamlObjectMapper;
    
    /**
     * 从所有位置加载 Hooks
     * 
     * @param projectDir 项目目录 (可选)
     * @return Hook 列表
     */
    public List<HookSpec> loadAllHooks(Path projectDir) {
        List<HookSpec> allHooks = new ArrayList<>();
        
        // 1. 加载类路径中的 Hooks (内置示例)
        List<HookSpec> classpathHooks = loadHooksFromClasspath();
        allHooks.addAll(classpathHooks);
        log.debug("Loaded {} hooks from classpath", classpathHooks.size());
        
        // 2. 加载用户主目录的 Hooks
        List<HookSpec> userHooks = loadHooksFromUserHome();
        allHooks.addAll(userHooks);
        log.debug("Loaded {} hooks from user home", userHooks.size());
        
        // 3. 加载项目目录的 Hooks (如果提供)
        if (projectDir != null) {
            List<HookSpec> projectHooks = loadHooksFromProject(projectDir);
            allHooks.addAll(projectHooks);
            log.debug("Loaded {} hooks from project directory", projectHooks.size());
        }
        
        log.info("Total {} hooks loaded", allHooks.size());
        return allHooks;
    }
    
    /**
     * 从类路径加载 Hooks
     */
    private List<HookSpec> loadHooksFromClasspath() {
        try {
            URL resource = getClass().getClassLoader().getResource("hooks");
            if (resource == null) {
                log.debug("No hooks directory found in classpath");
                return Collections.emptyList();
            }
            
            // JAR 包中运行时不支持直接遍历
            if (resource.getProtocol().equals("jar")) {
                log.debug("Running from JAR, skipping classpath hooks");
                return Collections.emptyList();
            }
            
            Path hooksDir = Paths.get(resource.toURI());
            return loadHooksFromDirectory(hooksDir, "classpath");
            
        } catch (Exception e) {
            log.warn("Failed to load hooks from classpath", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 从用户主目录加载 Hooks
     */
    private List<HookSpec> loadHooksFromUserHome() {
        String userHome = System.getProperty("user.home");
        Path hooksDir = Paths.get(userHome, ".jimi", "hooks");
        
        if (!Files.exists(hooksDir)) {
            log.debug("User hooks directory not found: {}", hooksDir);
            return Collections.emptyList();
        }
        
        return loadHooksFromDirectory(hooksDir, "user");
    }
    
    /**
     * 从项目目录加载 Hooks
     */
    private List<HookSpec> loadHooksFromProject(Path projectDir) {
        Path hooksDir = projectDir.resolve(".jimi").resolve("hooks");
        
        if (!Files.exists(hooksDir)) {
            log.debug("Project hooks directory not found: {}", hooksDir);
            return Collections.emptyList();
        }
        
        return loadHooksFromDirectory(hooksDir, "project");
    }
    
    /**
     * 从指定目录加载所有 Hook 配置文件
     * 
     * @param directory Hook 配置目录
     * @param source 来源标识 (用于日志)
     * @return Hook 列表
     */
    private List<HookSpec> loadHooksFromDirectory(Path directory, String source) {
        List<HookSpec> hooks = new ArrayList<>();
        
        if (!Files.isDirectory(directory)) {
            return hooks;
        }
        
        try (Stream<Path> paths = Files.walk(directory, 1)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().endsWith(".yaml") || 
                             p.getFileName().toString().endsWith(".yml"))
                 .forEach(file -> {
                     try {
                         HookSpec spec = parseHookFile(file);
                         if (spec != null) {
                             spec.setConfigFilePath(file.toString());
                             hooks.add(spec);
                             log.debug("Loaded hook '{}' from {} ({})", 
                                     spec.getName(), source, file.getFileName());
                         }
                     } catch (Exception e) {
                         log.error("Failed to load hook from {}: {}", 
                                 file, e.getMessage());
                     }
                 });
        } catch (IOException e) {
            log.error("Failed to scan hooks directory: {}", directory, e);
        }
        
        return hooks;
    }
    
    /**
     * 解析单个 Hook 配置文件
     * 
     * @param file 配置文件路径
     * @return Hook 规范
     */
    public HookSpec parseHookFile(Path file) {
        try {
            log.debug("Parsing hook file: {}", file);
            
            // 读取并解析 YAML
            HookSpec spec = yamlObjectMapper.readValue(
                file.toFile(), 
                HookSpec.class
            );
            
            // 验证配置
            spec.validate();
            
            return spec;
            
        } catch (IOException e) {
            throw new ConfigException("Failed to parse hook file: " + file, e);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Invalid hook configuration in " + file + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取用户 Hook 目录路径
     */
    public Path getUserHooksDirectory() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".jimi", "hooks");
    }
    
    /**
     * 获取项目 Hook 目录路径
     */
    public Path getProjectHooksDirectory(Path projectDir) {
        return projectDir.resolve(".jimi").resolve("hooks");
    }
    
    /**
     * 确保用户 Hook 目录存在
     */
    public void ensureUserHooksDirectory() {
        Path dir = getUserHooksDirectory();
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("Created user hooks directory: {}", dir);
            }
        } catch (IOException e) {
            log.error("Failed to create user hooks directory: {}", dir, e);
        }
    }
}

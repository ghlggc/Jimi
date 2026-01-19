package io.leavesfly.jimi.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板注册表
 * 管理可复用的工作流模板
 */
@Slf4j
@Service
public class TemplateRegistry {
    
    private final Map<String, Template> templates = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    
    private static final String TEMPLATES_DIR = ".jimi/templates";
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing TemplateRegistry...");
        loadTemplates();
    }
    
    /**
     * 创建模板
     */
    public Template create(Template template) {
        if (template.getId() == null || template.getId().isBlank()) {
            template.setId(UUID.randomUUID().toString());
        }
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        
        templates.put(template.getId(), template);
        saveToFile(template);
        
        log.info("Template created: {}", template.getName());
        return template;
    }
    
    /**
     * 更新模板
     */
    public Template update(Template template) {
        if (!templates.containsKey(template.getId())) {
            throw new IllegalArgumentException("Template not found: " + template.getId());
        }
        
        template.setUpdatedAt(LocalDateTime.now());
        templates.put(template.getId(), template);
        saveToFile(template);
        
        log.info("Template updated: {}", template.getName());
        return template;
    }
    
    /**
     * 删除模板
     */
    public void delete(String id) {
        Template removed = templates.remove(id);
        if (removed != null) {
            deleteFile(id);
            log.info("Template deleted: {}", removed.getName());
        }
    }
    
    /**
     * 获取模板
     */
    public Optional<Template> get(String id) {
        return Optional.ofNullable(templates.get(id));
    }
    
    /**
     * 按名称查找模板
     */
    public Optional<Template> findByName(String name) {
        return templates.values().stream()
            .filter(t -> t.getName().equalsIgnoreCase(name))
            .findFirst();
    }
    
    /**
     * 获取所有模板
     */
    public List<Template> listAll() {
        return new ArrayList<>(templates.values());
    }
    
    /**
     * 应用模板
     */
    public String apply(String templateId, Map<String, String> variables) {
        Template template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }
        return template.applyVariables(variables != null ? variables : Collections.emptyMap());
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", templates.size());
        
        // 按 Agent 分组统计
        Map<String, Long> byAgent = new HashMap<>();
        for (Template t : templates.values()) {
            String agent = t.getAgent() != null ? t.getAgent() : "default";
            byAgent.merge(agent, 1L, Long::sum);
        }
        stats.put("byAgent", byAgent);
        
        return stats;
    }
    
    // ==================== 文件操作 ====================
    
    private void loadTemplates() {
        Path userDir = getUserTemplatesDir();
        if (!Files.exists(userDir)) {
            log.info("Templates directory does not exist: {}", userDir);
            return;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(userDir, "*.yaml")) {
            for (Path file : stream) {
                try {
                    Template template = yamlMapper.readValue(file.toFile(), Template.class);
                    templates.put(template.getId(), template);
                    log.debug("Loaded template: {}", template.getName());
                } catch (Exception e) {
                    log.warn("Failed to load template from {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan templates directory", e);
        }
        
        log.info("Loaded {} templates", templates.size());
    }
    
    private void saveToFile(Template template) {
        Path userDir = getUserTemplatesDir();
        try {
            Files.createDirectories(userDir);
            Path file = userDir.resolve(template.getId() + ".yaml");
            yamlMapper.writeValue(file.toFile(), template);
        } catch (IOException e) {
            log.error("Failed to save template: {}", template.getName(), e);
        }
    }
    
    private void deleteFile(String id) {
        Path file = getUserTemplatesDir().resolve(id + ".yaml");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete template file: {}", file, e);
        }
    }
    
    private Path getUserTemplatesDir() {
        return Paths.get(System.getProperty("user.home"), TEMPLATES_DIR);
    }
    
    // ==================== 模板数据类 ====================
    
    @Data
    public static class Template {
        private String id;
        private String name;
        private String description;
        private String prompt;
        private String agent;
        private Map<String, String> variables = new HashMap<>();
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        /**
         * 应用变量替换
         */
        public String applyVariables(Map<String, String> values) {
            if (prompt == null) return "";
            
            String result = prompt;
            // 替换模板变量
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result = result.replace("${" + entry.getKey() + "}", entry.getValue());
            }
            // 替换默认变量
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                if (!values.containsKey(entry.getKey())) {
                    result = result.replace("${" + entry.getKey() + "}", entry.getValue());
                }
            }
            return result;
        }
    }
}

package io.leavesfly.jimi.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jimi.config.MemoryConfig;
import io.leavesfly.jimi.engine.runtime.Runtime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆管理器
 * 负责长期记忆的加载、保存和查询
 * 基于工作目录的本地JSON文件存储
 */
@Slf4j
@Component
public class MemoryManager {
    
    private final ObjectMapper objectMapper;
    private final MemoryConfig config;
    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();
    
    private Path memoryDir;
    private boolean initialized = false;
    
    public MemoryManager(ObjectMapper objectMapper, MemoryConfig config) {
        this.objectMapper = objectMapper;
        this.config = config;
    }
    
    /**
     * 初始化记忆目录（基于工作目录）
     * 
     * @param runtime 运行时上下文
     */
    public void initialize(Runtime runtime) {
        if (initialized) {
            return;
        }
        
        Path workDir = runtime.getWorkDir();
        this.memoryDir = workDir.resolve(".jimi").resolve("memory");
        
        try {
            Files.createDirectories(memoryDir);
            log.info("初始化记忆管理器，存储路径: {}", memoryDir);
            initialized = true;
        } catch (IOException e) {
            log.error("创建记忆目录失败: {}", memoryDir, e);
        }
    }
    
    /**
     * 检查是否已初始化
     */
    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("MemoryManager未初始化，请先调用initialize()方法");
        }
    }
    
    // ==================== 用户偏好管理 ====================
    
    /**
     * 加载用户偏好
     */
    public Mono<UserPreferences> loadPreferences() {
        return loadFromFile("preferences.json", UserPreferences.class)
                .defaultIfEmpty(UserPreferences.getDefault())
                .doOnNext(prefs -> log.debug("加载用户偏好: language={}, style={}", 
                        prefs.getCommunication().getLanguage(),
                        prefs.getCoding().getStyle()));
    }
    
    /**
     * 保存用户偏好
     */
    public Mono<Void> savePreferences(UserPreferences prefs) {
        return saveToFile("preferences.json", prefs)
                .doOnSuccess(v -> log.debug("保存用户偏好完成"));
    }
    
    // ==================== 项目知识管理 ====================
    
    /**
     * 查询项目知识（基于关键词）
     * 
     * @param keyword 关键词
     * @param limit 返回数量限制
     * @return 匹配的知识列表
     */
    public Mono<List<ProjectInsight>> queryInsights(String keyword, int limit) {
        if (!config.isLongTermEnabled()) {
            return Mono.just(List.of());
        }
        
        return loadFromFile("project_insights.json", ProjectInsightsStore.class)
                .map(store -> store.search(keyword, limit))
                .flatMap(insights -> {
                    if (!insights.isEmpty()) {
                        // 更新访问记录后保存
                        return saveInsightsStore()
                                .thenReturn(insights);
                    }
                    return Mono.just(insights);
                })
                .defaultIfEmpty(List.of())
                .doOnNext(insights -> {
                    if (!insights.isEmpty()) {
                        log.debug("查询知识 [{}] 匹配 {} 条", keyword, insights.size());
                    }
                });
    }
    
    /**
     * 添加项目知识
     * 
     * @param insight 知识条目
     */
    public Mono<Void> addInsight(ProjectInsight insight) {
        if (!config.isLongTermEnabled() || !config.isAutoExtract()) {
            return Mono.empty();
        }
        
        return loadFromFile("project_insights.json", ProjectInsightsStore.class)
                .defaultIfEmpty(createNewInsightsStore())
                .flatMap(store -> {
                    store.add(insight);
                    store.prune(config.getMaxInsights());
                    return saveToFile("project_insights.json", store);
                })
                .doOnSuccess(v -> log.debug("添加知识: [{}] {}", 
                        insight.getCategory(), 
                        insight.getContent().substring(0, Math.min(50, insight.getContent().length()))));
    }
    
    /**
     * 创建新的知识存储
     */
    private ProjectInsightsStore createNewInsightsStore() {
        ProjectInsightsStore store = new ProjectInsightsStore();
        store.setVersion("1.0");
        if (memoryDir != null) {
            store.setWorkspaceRoot(memoryDir.getParent().getParent().toString());
        }
        return store;
    }
    
    /**
     * 保存知识存储（用于更新访问记录）
     */
    private Mono<Void> saveInsightsStore() {
        Object cached = cache.get("project_insights.json");
        if (cached instanceof ProjectInsightsStore) {
            return saveToFile("project_insights.json", cached);
        }
        return Mono.empty();
    }
    
    // ==================== 任务模式管理 ====================
    
    /**
     * 查询任务模式
     * 
     * @param trigger 触发词
     * @return 匹配的任务模式
     */
    public Mono<TaskPattern> findPattern(String trigger) {
        if (!config.isLongTermEnabled()) {
            return Mono.empty();
        }
        
        return loadFromFile("task_patterns.json", TaskPatternStore.class)
                .map(store -> store.findByTrigger(trigger))
                .flatMap(pattern -> {
                    if (pattern != null) {
                        // 更新使用记录后保存
                        return savePatternStore()
                                .thenReturn(pattern);
                    }
                    return Mono.empty();
                })
                .doOnNext(pattern -> log.debug("找到任务模式: {}", pattern.getTrigger()));
    }
    
    /**
     * 添加任务模式
     * 
     * @param pattern 任务模式
     */
    public Mono<Void> addPattern(TaskPattern pattern) {
        if (!config.isLongTermEnabled()) {
            return Mono.empty();
        }
        
        return loadFromFile("task_patterns.json", TaskPatternStore.class)
                .defaultIfEmpty(new TaskPatternStore())
                .flatMap(store -> {
                    store.add(pattern);
                    return saveToFile("task_patterns.json", store);
                })
                .doOnSuccess(v -> log.debug("添加任务模式: {}", pattern.getTrigger()));
    }
    
    /**
     * 保存模式存储（用于更新使用记录）
     */
    private Mono<Void> savePatternStore() {
        Object cached = cache.get("task_patterns.json");
        if (cached instanceof TaskPatternStore) {
            return saveToFile("task_patterns.json", cached);
        }
        return Mono.empty();
    }
    
    // ==================== 任务历史管理 ====================
    
    /**
     * 添加任务历史
     * 
     * @param task 任务历史
     */
    public Mono<Void> addTaskHistory(TaskHistory task) {
        if (!config.isLongTermEnabled()) {
            return Mono.empty();
        }
        
        return loadFromFile("task_history.json", TaskHistoryStore.class)
                .defaultIfEmpty(new TaskHistoryStore())
                .flatMap(store -> {
                    store.add(task);
                    store.prune(config.getMaxTaskHistory(), config.getInsightExpiryDays());
                    return saveToFile("task_history.json", store);
                })
                .doOnSuccess(v -> log.info("记录任务历史: {}", task.getUserQuery()));
    }
    
    /**
     * 获取最近的任务历史
     * 
     * @param limit 返回数量
     * @return 任务列表
     */
    public Mono<List<TaskHistory>> getRecentTasks(int limit) {
        if (!config.isLongTermEnabled()) {
            return Mono.just(List.of());
        }
        
        return loadFromFile("task_history.json", TaskHistoryStore.class)
                .map(store -> store.getRecent(limit))
                .defaultIfEmpty(List.of());
    }
    
    /**
     * 按关键词搜索任务历史
     * 
     * @param keyword 关键词
     * @param limit 返回数量
     * @return 任务列表
     */
    public Mono<List<TaskHistory>> searchTaskHistory(String keyword, int limit) {
        if (!config.isLongTermEnabled()) {
            return Mono.just(List.of());
        }
        
        return loadFromFile("task_history.json", TaskHistoryStore.class)
                .map(store -> store.searchByKeyword(keyword, limit))
                .defaultIfEmpty(List.of());
    }
    
    /**
     * 按时间范围查询任务历史
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 任务列表
     */
    public Mono<List<TaskHistory>> getTasksByTimeRange(Instant startTime, Instant endTime) {
        if (!config.isLongTermEnabled()) {
            return Mono.just(List.of());
        }
        
        return loadFromFile("task_history.json", TaskHistoryStore.class)
                .map(store -> store.getByTimeRange(startTime, endTime))
                .defaultIfEmpty(List.of());
    }
    
    // ==================== 文件操作辅助方法 ====================
    
    /**
     * 从文件加载数据
     */
    private <T> Mono<T> loadFromFile(String filename, Class<T> clazz) {
        ensureInitialized();
        
        Path file = memoryDir.resolve(filename);
        if (!Files.exists(file)) {
            return Mono.empty();
        }
        
        return Mono.fromCallable(() -> {
            T data = objectMapper.readValue(file.toFile(), clazz);
            cache.put(filename, data);
            return data;
        }).onErrorResume(e -> {
            log.error("加载记忆文件失败: {}", filename, e);
            return Mono.empty();
        });
    }
    
    /**
     * 保存数据到文件
     */
    private Mono<Void> saveToFile(String filename, Object data) {
        ensureInitialized();
        
        Path file = memoryDir.resolve(filename);
        
        return Mono.fromRunnable(() -> {
            try {
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(file.toFile(), data);
                cache.put(filename, data);
                log.trace("保存记忆文件: {}", filename);
            } catch (IOException e) {
                log.error("保存记忆文件失败: {}", filename, e);
            }
        });
    }
    
    /**
     * 获取记忆目录路径
     */
    public Path getMemoryDir() {
        return memoryDir;
    }
}

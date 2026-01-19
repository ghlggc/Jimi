package io.leavesfly.jwork.service;

import io.leavesfly.jimi.JimiApplication;
import io.leavesfly.jimi.core.JimiEngine;
import io.leavesfly.jimi.core.JimiFactory;
import io.leavesfly.jimi.core.interaction.approval.ApprovalRequest;
import io.leavesfly.jimi.core.interaction.approval.ApprovalResponse;
import io.leavesfly.jimi.core.session.Session;
import io.leavesfly.jimi.llm.message.TextPart;
import io.leavesfly.jimi.tool.skill.SkillRegistry;
import io.leavesfly.jimi.tool.skill.SkillScope;
import io.leavesfly.jimi.tool.skill.SkillSpec;
import io.leavesfly.jimi.wire.message.ContentPartMessage;
import io.leavesfly.jimi.wire.message.TodoUpdateMessage;
import io.leavesfly.jimi.wire.message.ToolCallMessage;
import io.leavesfly.jimi.wire.message.WireMessage;
import io.leavesfly.jwork.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JWork 核心服务
 * 启动嵌入式 Jimi，管理会话和任务执行
 */
@Slf4j
public class JWorkService {
    
    private ConfigurableApplicationContext springContext;
    private JimiFactory jimiFactory;
    private SkillRegistry skillRegistry;
    private final Map<String, WorkSession> sessions = new ConcurrentHashMap<>();
    
    // 待处理的审批请求
    private final Map<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();
    
    // 模板存储
    private static final String SESSIONS_FILE = "sessions.json";
    private final ObjectMapper templateMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .registerModule(new JavaTimeModule());
    private Path templatesDir;
    
    // 初始化状态
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final CountDownLatch initLatch = new CountDownLatch(1);
    
    public JWorkService() {
        // 异步启动嵌入式 Jimi
        startEmbeddedJimi();
    }
    
    /**
     * 启动嵌入式 Jimi Spring 上下文
     */
    private void startEmbeddedJimi() {
        Thread initThread = new Thread(() -> {
            try {
                log.info("Starting embedded Jimi...");
                
                // 创建 Spring 应用
                SpringApplication app = new SpringApplication(JimiApplication.class);
                app.setBannerMode(Banner.Mode.OFF);
                app.setLogStartupInfo(false);
                // 设置为非 Web 应用
                app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
                
                // 禁用 CLI CommandLineRunner
                app.setDefaultProperties(java.util.Map.of(
                    "spring.main.lazy-initialization", "false",
                    "jimi.embedded", "true"  // 标记为嵌入式模式
                ));
                
                // 启动 Spring 上下文，不传递命令行参数以跳过 CLI 启动
                springContext = app.run(new String[]{"--jimi.embedded=true"});
                
                // 获取核心 Bean
                jimiFactory = springContext.getBean(JimiFactory.class);
                skillRegistry = springContext.getBean(SkillRegistry.class);
                
                // 设置模板存储目录
                templatesDir = Paths.get(System.getProperty("user.home"), ".jimi");
                try {
                    Files.createDirectories(templatesDir);
                } catch (IOException e) {
                    log.warn("Failed to create templates dir: {}", e.getMessage());
                }
                
                initialized.set(true);
                log.info("Embedded Jimi started successfully");
            } catch (Exception e) {
                log.error("Failed to start embedded Jimi", e);
            } finally {
                initLatch.countDown();
            }
        }, "jimi-init");
        initThread.setDaemon(true);
        initThread.start();
    }
    
    /**
     * 等待初始化完成
     */
    public boolean waitForInit(long timeout, TimeUnit unit) {
        try {
            return initLatch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }
    
    /**
     * 创建工作会话
     */
    public WorkSession createSession(Path workDir, String agentName) {
        ensureInitialized();
        log.info("Creating session: workDir={}, agent={}", workDir, agentName);
        
        String sessionId = UUID.randomUUID().toString();
        Path sessionsDir = workDir.resolve(".jimi/sessions/" + sessionId);
        
        try {
            java.nio.file.Files.createDirectories(sessionsDir);
        } catch (Exception e) {
            log.warn("Failed to create sessions dir: {}", e.getMessage());
        }
        
        Session session = Session.builder()
            .id(sessionId)
            .workDir(workDir)
            .historyFile(sessionsDir.resolve("history.jsonl"))
            .build();
        
        JimiEngine engine = jimiFactory.createEngine()
            .session(session)
            .agentSpec("default".equals(agentName) ? null : 
                Paths.get("agents/" + agentName + "/agent.yaml"))
            .model(null)
            .yolo(false)
            .mcpConfigs(null)
            .build()
            .block();
        
        WorkSession workSession = new WorkSession(engine, workDir, agentName);
        sessions.put(workSession.getId(), workSession);
        
        // 自动持久化会话元数据
        persistAllSessions();
        
        log.info("Session created: {}", workSession.getId());
        return workSession;
    }
    
    /**
     * 获取会话
     */
    public WorkSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    /**
     * 获取所有会话
     */
    public List<WorkSession> getAllSessions() {
        return new ArrayList<>(sessions.values());
    }
    
    /**
     * 关闭会话
     */
    public void closeSession(String sessionId) {
        WorkSession session = sessions.remove(sessionId);
        if (session != null) {
            // 持久化会话元数据
            persistAllSessions();
            log.info("Session closed: {}", sessionId);
        }
    }
    
    // ==================== 会话持久化方法 ====================
    
    /**
     * 持久化所有会话元数据
     */
    public void persistAllSessions() {
        // 加载现有记录，避免覆盖
        List<SessionMetadata> allMetadata = new ArrayList<>(loadSessionMetadataList());
        
        // 更新或添加当前内存中的会话
        for (WorkSession session : sessions.values()) {
            SessionMetadata meta = SessionMetadata.fromWorkSession(session);
            boolean found = false;
            for (int i = 0; i < allMetadata.size(); i++) {
                if (allMetadata.get(i).getId().equals(meta.getId())) {
                    allMetadata.set(i, meta);
                    found = true;
                    break;
                }
            }
            if (!found) {
                allMetadata.add(0, meta); // 新会话放在前面
            }
        }
        
        // 限制历史记录数量，例如最多 100 条
        if (allMetadata.size() > 100) {
            allMetadata = allMetadata.subList(0, 100);
        }
        
        Path sessionsFile = templatesDir.resolve(SESSIONS_FILE);
        try {
            templateMapper.writeValue(sessionsFile.toFile(), allMetadata);
            log.debug("Persisted {} session metadata", allMetadata.size());
        } catch (IOException e) {
            log.error("Failed to persist sessions", e);
        }
    }
    
    /**
     * 加载所有会话元数据
     */
    public List<SessionMetadata> loadSessionMetadataList() {
        Path sessionsFile = templatesDir.resolve(SESSIONS_FILE);
        if (!Files.exists(sessionsFile)) {
            return Collections.emptyList();
        }
        
        try {
            return templateMapper.readValue(
                sessionsFile.toFile(),
                new TypeReference<List<SessionMetadata>>() {}
            );
        } catch (IOException e) {
            log.error("Failed to load session metadata", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 恢复历史会话
     */
    public WorkSession restoreSession(SessionMetadata metadata) {
        ensureInitialized();
        log.info("Restoring session: id={}, workDir={}, historyFile={}", 
            metadata.getId(), metadata.getWorkDir(), metadata.getHistoryFile());
        
        Path workDir = Paths.get(metadata.getWorkDir());
        Path historyFile = Paths.get(metadata.getHistoryFile());
        
        // 检查历史文件是否存在
        if (!Files.exists(historyFile)) {
            log.warn("History file not found: {}", historyFile);
            // 尝试从相对路径查找
            Path relativeHistory = workDir.resolve(".jimi/sessions/" + metadata.getId() + "/history.jsonl");
            if (Files.exists(relativeHistory)) {
                log.info("Found history file at relative path: {}", relativeHistory);
                historyFile = relativeHistory;
            } else {
                log.warn("History file also not found at relative path: {}. Creating new session.", relativeHistory);
                return createSession(workDir, metadata.getAgentName());
            }
        }
        
        // 使用原有的历史文件创建 Session
        Session session = Session.builder()
            .id(metadata.getId())
            .workDir(workDir)
            .historyFile(historyFile)
            .build();
        
        JimiEngine engine = jimiFactory.createEngine()
            .session(session)
            .agentSpec("default".equals(metadata.getAgentName()) ? null : 
                Paths.get("agents/" + metadata.getAgentName() + "/agent.yaml"))
            .model(null)
            .yolo(false)
            .mcpConfigs(null)
            .build()
            .block();
        
        WorkSession workSession = new WorkSession(
            metadata.getId(), 
            engine, 
            workDir, 
            metadata.getAgentName(), 
            metadata.getCreatedAt()
        );
        sessions.put(workSession.getId(), workSession);
        
        // 更新访问时间并持久化
        persistAllSessions();
        
        log.info("Session restored: {}", workSession.getId());
        return workSession;
    }
    
    /**
     * 获取会话历史消息
     */
    public List<io.leavesfly.jimi.llm.message.Message> getSessionMessages(String sessionId) {
        WorkSession session = sessions.get(sessionId);
        if (session != null && session.getEngine() != null) {
            return session.getEngine().getContext().getHistory();
        }
        return Collections.emptyList();
    }
    
    /**
     * 执行任务（流式输出）
     */
    public Flux<StreamChunk> execute(String sessionId, String input) {
        ensureInitialized();
        WorkSession session = sessions.get(sessionId);
        if (session == null) {
            return Flux.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        if (session.isRunning()) {
            return Flux.error(new IllegalStateException("Session is already running"));
        }
        
        JimiEngine engine = session.getEngine();
        String jobId = UUID.randomUUID().toString();
        session.startJob(jobId);
        
        // 重置 Wire，确保新任务使用新的 Sink
        engine.getWire().reset();
        
        // 重置取消标志
        engine.getRuntime().getSession().resetCancelled();
        
        log.info("Executing task: sessionId={}, jobId={}, input={}", sessionId, jobId, 
            input.length() > 50 ? input.substring(0, 50) + "..." : input);
        
        // 使用 multicast sink，支持多次 emit
        Sinks.Many<StreamChunk> sink = Sinks.many().multicast().onBackpressureBuffer();
        CountDownLatch subscriptionReady = new CountDownLatch(1);
        
        // 在独立线程中执行任务
        Thread execThread = new Thread(() -> {
            reactor.core.Disposable subscription = null;
            try {
                // 订阅 Wire 消息 - 使用 publish().autoConnect() 确保即使是 share 也能收到消息
                subscription = engine.getWire().asFlux()
                    .doOnSubscribe(s -> subscriptionReady.countDown())
                    .subscribe(msg -> {
                        StreamChunk chunk = convertToChunk(msg, jobId);
                        if (chunk != null) {
                            Sinks.EmitResult result = sink.tryEmitNext(chunk);
                            if (result.isFailure()) {
                                log.warn("Failed to emit chunk: {}", result);
                            }
                        }
                    }, e -> {
                        log.error("Wire error", e);
                        sink.tryEmitNext(StreamChunk.error(e.getMessage()));
                    });
                
                // 等待订阅建立
                subscriptionReady.await(1, TimeUnit.SECONDS);
                Thread.sleep(50); // 确保订阅完全就绪
                
                // 同步执行任务
                engine.run(input).block();
                
                // 等待一小段时间确保所有 Wire 消息都被处理
                Thread.sleep(200);
                
                log.info("Task completed: jobId={}", jobId);
            } catch (Exception e) {
                log.error("Task error: jobId={}", jobId, e);
                sink.tryEmitNext(StreamChunk.error(e.getMessage()));
            } finally {
                // 确保清理
                if (subscription != null) {
                    subscription.dispose();
                }
                session.endJob();
                sink.tryEmitNext(StreamChunk.done());
                sink.tryEmitComplete();
            }
        }, "jwork-exec-" + jobId);
        execThread.setDaemon(true);
        execThread.start();
        
        return sink.asFlux();
    }
    
    /**
     * 处理审批
     */
    public void handleApproval(String toolCallId, ApprovalInfo.Response response) {
        PendingApproval pending = pendingApprovals.remove(toolCallId);
        if (pending == null) {
            log.warn("Approval not found: {}", toolCallId);
            return;
        }
        
        ApprovalResponse jimiResponse = switch (response) {
            case APPROVE -> ApprovalResponse.APPROVE;
            case APPROVE_SESSION -> ApprovalResponse.APPROVE_FOR_SESSION;
            case REJECT -> ApprovalResponse.REJECT;
        };
        
        pending.request.resolve(jimiResponse);
        log.info("Approval handled: toolCallId={}, response={}", toolCallId, response);
    }
    
    /**
     * 取消任务
     */
    public void cancelTask(String sessionId) {
        WorkSession session = sessions.get(sessionId);
        if (session != null && session.isRunning()) {
            // 设置 Session 取消标志
            JimiEngine engine = session.getEngine();
            if (engine != null) {
                engine.getRuntime().getSession().cancel();
            }
            session.endJob();
            log.info("Task cancelled: sessionId={}", sessionId);
        }
    }
    
    /**
     * 获取所有 Skills
     */
    public List<SkillInfo> getAllSkills() {
        if (skillRegistry == null) {
            return Collections.emptyList();
        }
        
        return skillRegistry.getAllSkills().stream()
            .map(spec -> new SkillInfo(
                spec.getName(),
                spec.getDescription(),
                spec.getVersion(),
                spec.getCategory(),
                spec.getScope() == SkillScope.GLOBAL ?
                    SkillInfo.Scope.GLOBAL : SkillInfo.Scope.PROJECT,
                true
            ))
            .toList();
    }
    
    /**
     * 安装 Skill
     */
    public void installSkill(Path skillPath) {
        ensureInitialized();
        if (skillRegistry == null) {
            throw new IllegalStateException("SkillRegistry not available");
        }
        skillRegistry.install(skillPath);
    }
    
    /**
     * 卸载 Skill
     */
    public void uninstallSkill(String skillName) {
        ensureInitialized();
        if (skillRegistry == null) {
            throw new IllegalStateException("SkillRegistry not available");
        }
        skillRegistry.uninstall(skillName);
    }
    
    /**
     * 获取可用的 Agent 列表
     */
    public List<String> getAvailableAgents() {
        // TODO: 从配置或文件系统加载
        return List.of("default", "code", "architect", "doc", "test");
    }
    
    /**
     * 关闭服务，释放所有资源
     */
    public void shutdown() {
        log.info("Shutting down JWorkService...");
        System.out.println("JWorkService shutting down...");
        
        // 1. 取消所有正在运行的任务
        for (String sessionId : sessions.keySet()) {
            try {
                cancelTask(sessionId);
            } catch (Exception e) {
                log.warn("Error cancelling task for session: {}", sessionId, e);
            }
        }
        sessions.clear();
        pendingApprovals.clear();
        
        // 2. 关闭 Spring 上下文
        if (springContext != null && springContext.isActive()) {
            try {
                log.info("Closing Spring context...");
                // 使用 separate thread 关闭 context，防止它挂起
                Thread contextCloseThread = new Thread(() -> {
                    try {
                        springContext.close();
                        log.info("Spring context closed successfully");
                    } catch (Exception e) {
                        log.error("Error during Spring context close", e);
                    }
                }, "spring-context-close");
                contextCloseThread.setDaemon(true);
                contextCloseThread.start();
                contextCloseThread.join(2000); // 最多等 2 秒
            } catch (Exception e) {
                log.error("Error closing Spring context", e);
            }
        }
        
        initialized.set(false);
        log.info("JWorkService shutdown complete");
        System.out.println("JWorkService shutdown complete.");
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 确保已初始化，否则抛异常
     */
    private void ensureInitialized() {
        if (!initialized.get()) {
            throw new IllegalStateException("JWorkService not initialized. Call waitForInit() first.");
        }
    }
    
    private StreamChunk convertToChunk(WireMessage msg, String jobId) {
        String msgType = msg.getMessageType();
        
        return switch (msgType) {
            case "content_part" -> {
                if (msg instanceof ContentPartMessage cpm) {
                    if (cpm.getContentPart() instanceof TextPart tp) {
                        yield StreamChunk.text(tp.getText());
                    }
                }
                yield null;
            }
            case "tool_call" -> {
                if (msg instanceof ToolCallMessage tcm) {
                    String toolName = tcm.getToolCall().getFunction().getName();
                    yield StreamChunk.toolCall(toolName);
                }
                yield null;
            }
            case "step_begin" -> StreamChunk.stepBegin();
            case "step_end" -> StreamChunk.stepEnd();
            case "todo_update" -> {
                if (msg instanceof TodoUpdateMessage tum) {
                    // 转换 Wire 消息到 UI 模型
                    List<TodoInfo> todoInfoList = tum.getTodos().stream()
                        .map(item -> TodoInfo.builder()
                            .id(item.getId())
                            .content(item.getTitle())
                            .status(TodoInfo.parseStatus(item.getStatus()))
                            .parentId(item.getParentId())
                            .build())
                        .toList();
                    
                    TodoInfo.TodoList todoList = TodoInfo.TodoList.builder()
                        .todos(todoInfoList)
                        .totalCount(tum.getTotalCount())
                        .pendingCount(tum.getPendingCount())
                        .inProgressCount(tum.getInProgressCount())
                        .doneCount(tum.getDoneCount())
                        .cancelledCount(tum.getCancelledCount())
                        .errorCount(tum.getErrorCount())
                        .build();
                    
                    yield StreamChunk.todoUpdate(todoList);
                }
                yield null;
            }
            default -> {
                // 检查是否是审批请求
                if (msg instanceof ApprovalRequest approvalRequest) {
                    ApprovalInfo info = new ApprovalInfo(
                        approvalRequest.getToolCallId(),
                        approvalRequest.getAction(),
                        approvalRequest.getDescription()
                    );
                    pendingApprovals.put(info.getToolCallId(), 
                        new PendingApproval(approvalRequest, jobId));
                    yield StreamChunk.approval(info);
                }
                yield null;
            }
        };
    }
    

    
    /**
     * 待处理审批
     */
    private record PendingApproval(ApprovalRequest request, String jobId) {}
}

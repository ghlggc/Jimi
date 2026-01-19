package io.leavesfly.jimi.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jimi.core.JimiEngine;
import io.leavesfly.jimi.core.JimiFactory;
import io.leavesfly.jimi.core.interaction.approval.ApprovalRequest;
import io.leavesfly.jimi.mcp.MCPSchema;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP工具注册表
 * 将Jimi核心能力封装为MCP Tools供外部调用
 */
@Slf4j
public class McpToolRegistry {
    
    private final JimiFactory jimiFactory;
    private final ObjectMapper objectMapper;
    private final Map<String, McpToolDefinition> tools;
    private final Map<String, SessionContext> sessions;
    // 任务执行上下文（用于流式输出轮询）
    private final Map<String, JobContext> jobs;
    // 待审批请求缓存（跨 job 查询）
    private final Map<String, ApprovalRequest> pendingApprovals;
    // toolCallId -> jobId 映射，用于审批归档
    private final Map<String, String> approvalJobMap;
    
    public McpToolRegistry(JimiFactory jimiFactory) {
        this.jimiFactory = jimiFactory;
        this.objectMapper = new ObjectMapper();
        this.tools = new LinkedHashMap<>();
        this.sessions = new ConcurrentHashMap<>();
        this.jobs = new ConcurrentHashMap<>();
        this.pendingApprovals = new ConcurrentHashMap<>();
        this.approvalJobMap = new ConcurrentHashMap<>();
        
        // 注册核心工具
        registerCoreTools();
    }
    
    /**
     * 注册核心工具
     */
    private void registerCoreTools() {
        // 工具0: jimi_session - 会话管理
        registerTool(
            "jimi_session",
            "Manage Jimi sessions (create/continue/list)",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "action", Map.of(
                        "type", "string",
                        "enum", List.of("create", "continue", "list"),
                        "description", "Session action"
                    ),
                    "sessionId", Map.of(
                        "type", "string",
                        "description", "Session ID (for continue)"
                    ),
                    "workDir", Map.of(
                        "type", "string",
                        "description", "Working directory path (optional)"
                    ),
                    "agent", Map.of(
                        "type", "string",
                        "description", "Agent name to use (optional)"
                    )
                ),
                "required", List.of("action")
            ),
            arguments -> manageSession(arguments)
        );
        
        // 工具1: jimi_execute - 执行Jimi任务
        registerTool(
            "jimi_execute",
            "Execute a Jimi task with natural language input",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "input", Map.of(
                        "type", "string",
                        "description", "The task description in natural language"
                    ),
                    "sessionId", Map.of(
                        "type", "string",
                        "description", "Session ID for continuous conversation (optional)"
                    ),
                    "workDir", Map.of(
                        "type", "string",
                        "description", "Working directory path (optional)"
                    ),
                    "agent", Map.of(
                        "type", "string",
                        "description", "Agent name to use (default/code/architect, optional)"
                    )
                ),
                "required", List.of("input")
            ),
            arguments -> executeJimiTask(arguments)
        );
        
        // 工具2: jimi_execute_stream - 流式执行（返回 jobId）
        registerTool(
            "jimi_execute_stream",
            "Execute a Jimi task with streaming output",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "input", Map.of(
                        "type", "string",
                        "description", "The task description in natural language"
                    ),
                    "sessionId", Map.of(
                        "type", "string",
                        "description", "Session ID for continuous conversation (optional)"
                    ),
                    "workDir", Map.of(
                        "type", "string",
                        "description", "Working directory path (optional)"
                    ),
                    "agent", Map.of(
                        "type", "string",
                        "description", "Agent name to use (default/code/architect, optional)"
                    )
                ),
                "required", List.of("input")
            ),
            arguments -> executeJimiTaskStream(arguments)
        );
        
        // 工具3: jimi_get_output - 获取流式输出（按游标拉取）
        registerTool(
            "jimi_get_output",
            "Get streaming output for a running job",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "jobId", Map.of(
                        "type", "string",
                        "description", "Job ID returned by jimi_execute_stream"
                    ),
                    "since", Map.of(
                        "type", "integer",
                        "description", "Output index to read from"
                    )
                ),
                "required", List.of("jobId")
            ),
            arguments -> getJobOutput(arguments)
        );
        
        // 工具4: jimi_approval - 审批处理（approve / reject）
        registerTool(
            "jimi_approval",
            "Handle approval requests",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "action", Map.of(
                        "type", "string",
                        "enum", List.of("list", "approve", "approve_session", "reject"),
                        "description", "Approval action"
                    ),
                    "toolCallId", Map.of(
                        "type", "string",
                        "description", "Tool call ID for approval"
                    ),
                    "sessionId", Map.of(
                        "type", "string",
                        "description", "Filter approvals by session ID (optional)"
                    )
                ),
                "required", List.of("action")
            ),
            arguments -> handleApproval(arguments)
        );
        
        // 工具5: jimi_cancel - 取消任务（尽力中断）
        registerTool(
            "jimi_cancel",
            "Cancel a running job",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "jobId", Map.of(
                        "type", "string",
                        "description", "Job ID to cancel"
                    )
                ),
                "required", List.of("jobId")
            ),
            arguments -> cancelJob(arguments)
        );
        
        log.info("Registered {} MCP tools", tools.size());
    }
    
    /**
     * 会话管理
     */
    private MCPSchema.CallToolResult manageSession(Map<String, Object> arguments) {
        try {
            String action = (String) arguments.get("action");
            String sessionId = (String) arguments.get("sessionId");
            String workDir = (String) arguments.getOrDefault("workDir", System.getProperty("user.dir"));
            String agentName = (String) arguments.getOrDefault("agent", "default");
            
            if (action == null) {
                return errorResult("Missing 'action'");
            }
            
            switch (action) {
                case "create" -> {
                    SessionContext ctx = createSessionContext(workDir, agentName);
                    sessions.put(ctx.sessionId, ctx);
                    return textResult("sessionId=" + ctx.sessionId);
                }
                case "continue" -> {
                    if (sessionId == null || sessionId.isBlank()) {
                        return errorResult("Missing 'sessionId'");
                    }
                    if (!sessions.containsKey(sessionId)) {
                        return errorResult("Session not found: " + sessionId);
                    }
                    return textResult("sessionId=" + sessionId);
                }
                case "list" -> {
                    return textResult("sessions=" + String.join(",", sessions.keySet()));
                }
                default -> {
                    return errorResult("Unknown action: " + action);
                }
            }
        } catch (Exception e) {
            log.error("Error managing session", e);
            return errorResult("Error: " + e.getMessage());
        }
    }
    
    /**
     * 执行Jimi任务
     */
    private MCPSchema.CallToolResult executeJimiTask(Map<String, Object> arguments) {
        try {
            String input = (String) arguments.get("input");
            String workDir = (String) arguments.getOrDefault("workDir", System.getProperty("user.dir"));
            String agentName = (String) arguments.getOrDefault("agent", "default");
            String sessionId = (String) arguments.get("sessionId");
            
            log.info("Executing Jimi task: input={}, workDir={}, agent={}", input, workDir, agentName);
            
            JimiEngine engine;
            if (sessionId != null && !sessionId.isBlank()) {
                SessionContext ctx = sessions.get(sessionId);
                if (ctx == null) {
                    return errorResult("Session not found: " + sessionId);
                }
                engine = ctx.engine;
            } else {
                engine = createEngine(workDir, agentName);
            }
            
            if (engine == null) {
                throw new RuntimeException("Failed to create Jimi Engine");
            }
            
            // 收集Wire消息
            StringBuilder output = new StringBuilder();
            List<String> steps = new java.util.ArrayList<>();
            
            // 订阅Wire消息
            engine.getWire().asFlux()
                .doOnNext(msg -> {
                    String msgType = msg.getMessageType();
                    log.debug("Wire message: {}", msgType);
                    
                    // 记录关键步骤
                    if (msgType.equals("step_begin")) {
                        steps.add("Step started");
                    } else if (msgType.equals("content_part")) {
                        io.leavesfly.jimi.wire.message.ContentPartMessage cpm = 
                            (io.leavesfly.jimi.wire.message.ContentPartMessage) msg;
                        if (cpm.getContentPart() instanceof io.leavesfly.jimi.llm.message.TextPart) {
                            io.leavesfly.jimi.llm.message.TextPart tp = 
                                (io.leavesfly.jimi.llm.message.TextPart) cpm.getContentPart();
                            output.append(tp.getText());
                        }
                    } else if (msgType.equals("tool_call")) {
                        io.leavesfly.jimi.wire.message.ToolCallMessage tcm = 
                            (io.leavesfly.jimi.wire.message.ToolCallMessage) msg;
                        steps.add("Tool: " + tcm.getToolCall().getFunction().getName());
                    }
                })
                .subscribe();
            
            // 执行任务(同步等待完成)
            engine.run(input).block();
            
            // 构建结果
            String result = output.toString();
            if (result.isEmpty()) {
                result = "Task completed successfully. Steps: " + String.join(", ", steps);
            }
            
            log.info("Task completed with {} steps", steps.size());
            
            return MCPSchema.CallToolResult.builder()
                .content(List.of(
                    MCPSchema.TextContent.builder()
                        .type("text")
                        .text(result)
                        .build()
                ))
                .isError(false)
                .build();
                
        } catch (Exception e) {
            log.error("Error executing Jimi task", e);
            return errorResult("Error: " + e.getMessage());
        }
    }
    
    private MCPSchema.CallToolResult executeJimiTaskStream(Map<String, Object> arguments) {
        try {
            String input = (String) arguments.get("input");
            String workDir = (String) arguments.getOrDefault("workDir", System.getProperty("user.dir"));
            String agentName = (String) arguments.getOrDefault("agent", "default");
            String sessionId = (String) arguments.get("sessionId");
            
            if (input == null || input.isBlank()) {
                return errorResult("Missing 'input'");
            }
            
            JimiEngine engine;
            if (sessionId != null && !sessionId.isBlank()) {
                SessionContext ctx = sessions.get(sessionId);
                if (ctx == null) {
                    return errorResult("Session not found: " + sessionId);
                }
                engine = ctx.engine;
            } else {
                SessionContext ctx = createSessionContext(workDir, agentName);
                sessions.put(ctx.sessionId, ctx);
                sessionId = ctx.sessionId;
                engine = ctx.engine;
            }
            
            String jobId = UUID.randomUUID().toString();
            JobContext job = new JobContext(jobId, sessionId, engine);
            jobs.put(jobId, job);
            
            job.start(input);
            
            return textResult("jobId=" + jobId);
        } catch (Exception e) {
            log.error("Error starting stream task", e);
            return errorResult("Error: " + e.getMessage());
        }
    }
    
    private MCPSchema.CallToolResult getJobOutput(Map<String, Object> arguments) {
        try {
            String jobId = (String) arguments.get("jobId");
            Number sinceNumber = (Number) arguments.getOrDefault("since", 0);
            int since = sinceNumber != null ? sinceNumber.intValue() : 0;
            
            if (jobId == null || jobId.isBlank()) {
                return errorResult("Missing 'jobId'");
            }
            JobContext job = jobs.get(jobId);
            if (job == null) {
                return errorResult("Job not found: " + jobId);
            }
            
            JobOutput output = job.getOutputSince(since);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chunks", output.chunks);
            result.put("next", output.nextIndex);
            result.put("done", job.done.get());
            result.put("error", job.error.get());
            result.put("approvals", job.getPendingApprovals());
            result.put("sessionId", job.sessionId);
            
            String payload = objectMapper.writeValueAsString(result);
            
            if (job.shouldCleanup(output.nextIndex)) {
                jobs.remove(jobId);
            }
            
            return textResult(payload);
        } catch (Exception e) {
            log.error("Error getting job output", e);
            return errorResult("Error: " + e.getMessage());
        }
    }
    
    private MCPSchema.CallToolResult handleApproval(Map<String, Object> arguments) {
        try {
            String action = (String) arguments.get("action");
            String toolCallId = (String) arguments.get("toolCallId");
            String sessionId = (String) arguments.get("sessionId");
            
            if (action == null) {
                return errorResult("Missing 'action'");
            }
            
            if ("list".equals(action)) {
                List<Map<String, Object>> approvals = listApprovals(sessionId);
                String payload = objectMapper.writeValueAsString(Map.of("approvals", approvals));
                return textResult(payload);
            }
            
            if (toolCallId == null || toolCallId.isBlank()) {
                return errorResult("Missing 'toolCallId'");
            }
            
            ApprovalRequest request = pendingApprovals.get(toolCallId);
            if (request == null) {
                return errorResult("Approval not found: " + toolCallId);
            }
            
            io.leavesfly.jimi.core.interaction.approval.ApprovalResponse response;
            switch (action) {
                case "approve" -> response = io.leavesfly.jimi.core.interaction.approval.ApprovalResponse.APPROVE;
                case "approve_session" -> response = io.leavesfly.jimi.core.interaction.approval.ApprovalResponse.APPROVE_FOR_SESSION;
                case "reject" -> response = io.leavesfly.jimi.core.interaction.approval.ApprovalResponse.REJECT;
                default -> {
                    return errorResult("Unknown action: " + action);
                }
            }
            
            request.resolve(response);
            pendingApprovals.remove(toolCallId);
            String jobId = approvalJobMap.remove(toolCallId);
            if (jobId != null) {
                JobContext job = jobs.get(jobId);
                if (job != null) {
                    job.removeApproval(toolCallId);
                }
            }
            
            return textResult("ok");
        } catch (Exception e) {
            log.error("Error handling approval", e);
            return errorResult("Error: " + e.getMessage());
        }
    }
    
    private List<Map<String, Object>> listApprovals(String sessionId) {
        List<Map<String, Object>> approvals = new ArrayList<>();
        for (Map.Entry<String, ApprovalRequest> entry : pendingApprovals.entrySet()) {
            String toolCallId = entry.getKey();
            ApprovalRequest request = entry.getValue();
            String jobId = approvalJobMap.get(toolCallId);
            JobContext job = jobId != null ? jobs.get(jobId) : null;
            if (sessionId != null && job != null && !sessionId.equals(job.sessionId)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("toolCallId", toolCallId);
            item.put("action", request.getAction());
            item.put("description", request.getDescription());
            if (job != null) {
                item.put("sessionId", job.sessionId);
                item.put("jobId", jobId);
            }
            approvals.add(item);
        }
        return approvals;
    }
    
    private MCPSchema.CallToolResult cancelJob(Map<String, Object> arguments) {
        try {
            String jobId = (String) arguments.get("jobId");
            if (jobId == null || jobId.isBlank()) {
                return errorResult("Missing 'jobId'");
            }
            JobContext job = jobs.get(jobId);
            if (job == null) {
                return errorResult("Job not found: " + jobId);
            }
            job.cancel();
            return textResult("cancelled");
        } catch (Exception e) {
            log.error("Error cancelling job", e);
            return errorResult("Error: " + e.getMessage());
        }
    }
    
    private SessionContext createSessionContext(String workDir, String agentName) {
        String sessionId = java.util.UUID.randomUUID().toString();
        java.nio.file.Path workPath = java.nio.file.Paths.get(workDir);
        java.nio.file.Path sessionsDir = workPath.resolve(".jimi/sessions/" + sessionId);
        try {
            java.nio.file.Files.createDirectories(sessionsDir);
        } catch (Exception e) {
            log.warn("Failed to create sessions dir: {}", e.getMessage());
        }
        
        io.leavesfly.jimi.core.session.Session session = io.leavesfly.jimi.core.session.Session.builder()
            .id(sessionId)
            .workDir(workPath)
            .historyFile(sessionsDir.resolve("history.jsonl"))
            .build();
        
        JimiEngine engine = jimiFactory.createEngine()
            .session(session)
            .agentSpec(agentName.equals("default") ? null :
                java.nio.file.Paths.get("agents/" + agentName + "/agent.yaml"))
            .model(null)
            .yolo(false)
            .mcpConfigs(null)
            .build()
            .block();
        
        return new SessionContext(sessionId, session, engine);
    }
    
    private JimiEngine createEngine(String workDir, String agentName) {
        SessionContext ctx = createSessionContext(workDir, agentName);
        return ctx.engine;
    }
    
    private MCPSchema.CallToolResult textResult(String text) {
        return MCPSchema.CallToolResult.builder()
            .content(List.of(
                MCPSchema.TextContent.builder()
                    .type("text")
                    .text(text)
                    .build()
            ))
            .isError(false)
            .build();
    }
    
    private MCPSchema.CallToolResult errorResult(String text) {
        return MCPSchema.CallToolResult.builder()
            .content(List.of(
                MCPSchema.TextContent.builder()
                    .type("text")
                    .text(text)
                    .build()
            ))
            .isError(true)
            .build();
    }
    
    /**
     * 注册工具
     */
    private void registerTool(
        String name,
        String description,
        Map<String, Object> inputSchema,
        ToolExecutor executor
    ) {
        MCPSchema.Tool tool = MCPSchema.Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(inputSchema)
            .build();
        
        tools.put(name, new McpToolDefinition(tool, executor));
        log.debug("Registered tool: {}", name);
    }
    
    /**
     * 获取所有工具列表
     */
    public List<MCPSchema.Tool> getAllTools() {
        return tools.values().stream()
            .map(def -> def.tool)
            .toList();
    }
    
    /**
     * 执行工具
     */
    public MCPSchema.CallToolResult executeTool(String toolName, Map<String, Object> arguments) {
        McpToolDefinition def = tools.get(toolName);
        
        if (def == null) {
            log.warn("Tool not found: {}", toolName);
            return MCPSchema.CallToolResult.builder()
                .content(List.of(
                    MCPSchema.TextContent.builder()
                        .type("text")
                        .text("Tool not found: " + toolName)
                        .build()
                ))
                .isError(true)
                .build();
        }
        
        return def.executor.execute(arguments);
    }
    
    /**
     * 工具执行器接口
     */
    @FunctionalInterface
    private interface ToolExecutor {
        MCPSchema.CallToolResult execute(Map<String, Object> arguments);
    }
    
    /**
     * 工具定义
     */
    private record McpToolDefinition(
        MCPSchema.Tool tool,
        ToolExecutor executor
    ) {}
    
    private static class SessionContext {
        final String sessionId;
        final io.leavesfly.jimi.core.session.Session session;
        final JimiEngine engine;
        
        SessionContext(String sessionId, io.leavesfly.jimi.core.session.Session session, JimiEngine engine) {
            this.sessionId = sessionId;
            this.session = session;
            this.engine = engine;
        }
    }
    
    private class JobContext {
        private final String jobId;
        private final String sessionId;
        private final JimiEngine engine;
        private final List<String> chunks;
        private final Map<String, ApprovalRequest> jobApprovals;
        private final AtomicBoolean done;
        private final AtomicBoolean cancelled;
        private final AtomicReference<String> error;
        private reactor.core.Disposable wireSubscription;
        private Thread thread;
        
        JobContext(String jobId, String sessionId, JimiEngine engine) {
            this.jobId = jobId;
            this.sessionId = sessionId;
            this.engine = engine;
            this.chunks = new CopyOnWriteArrayList<>();
            this.jobApprovals = new ConcurrentHashMap<>();
            this.done = new AtomicBoolean(false);
            this.cancelled = new AtomicBoolean(false);
            this.error = new AtomicReference<>(null);
        }
        
        void start(String input) {
            // 订阅 Wire 消息，转成可轮询的文本块
            wireSubscription = engine.getWire().asFlux().subscribe(msg -> {
                if (cancelled.get()) return;
                if (msg instanceof io.leavesfly.jimi.wire.message.ContentPartMessage cpm) {
                    if (cpm.getContentPart() instanceof io.leavesfly.jimi.llm.message.TextPart tp) {
                        chunks.add(tp.getText());
                    }
                } else if (msg instanceof io.leavesfly.jimi.wire.message.ToolCallMessage tcm) {
                    String toolName = tcm.getToolCall().getFunction().getName();
                    chunks.add("\n[Tool] " + toolName + "\n");
                } else if (msg instanceof ApprovalRequest approvalRequest) {
                    jobApprovals.put(approvalRequest.getToolCallId(), approvalRequest);
                    pendingApprovals.put(approvalRequest.getToolCallId(), approvalRequest);
                    approvalJobMap.put(approvalRequest.getToolCallId(), jobId);
                    chunks.add("\n[Approval required] " + approvalRequest.getAction() + ": " 
                        + approvalRequest.getDescription() + " (id=" + approvalRequest.getToolCallId() + ")\n");
                }
            });
            
            // 独立线程执行任务，完成后标记 done
            thread = new Thread(() -> {
                try {
                    engine.run(input).block();
                } catch (Exception e) {
                    error.set(e.getMessage());
                } finally {
                    done.set(true);
                    if (wireSubscription != null) {
                        wireSubscription.dispose();
                    }
                }
            }, "jimi-job-" + jobId);
            thread.setDaemon(true);
            thread.start();
        }
        
        JobOutput getOutputSince(int since) {
            // 按游标返回增量输出
            if (since < 0) since = 0;
            int size = chunks.size();
            if (since >= size) {
                return new JobOutput(Collections.emptyList(), size);
            }
            List<String> output = new ArrayList<>(chunks.subList(since, size));
            return new JobOutput(output, size);
        }
        
        List<Map<String, Object>> getPendingApprovals() {
            // 仅返回当前 job 的审批列表
            List<Map<String, Object>> approvals = new ArrayList<>();
            for (ApprovalRequest request : jobApprovals.values()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("toolCallId", request.getToolCallId());
                item.put("action", request.getAction());
                item.put("description", request.getDescription());
                approvals.add(item);
            }
            return approvals;
        }
        
        void removeApproval(String toolCallId) {
            jobApprovals.remove(toolCallId);
        }
        
        boolean shouldCleanup(int nextIndex) {
            return done.get() && jobApprovals.isEmpty() && nextIndex >= chunks.size();
        }
        
        void cancel() {
            // 尽力中断执行线程和订阅
            cancelled.set(true);
            error.set("cancelled");
            done.set(true);
            if (wireSubscription != null) {
                wireSubscription.dispose();
            }
            if (thread != null) {
                thread.interrupt();
            }
        }
    }
    
    private record JobOutput(List<String> chunks, int nextIndex) {}
}

package io.leavesfly.jwork.ui.component;

import io.leavesfly.jwork.model.ApprovalInfo;
import io.leavesfly.jwork.model.StreamChunk;
import io.leavesfly.jwork.model.TodoInfo;
import io.leavesfly.jwork.model.WorkSession;
import io.leavesfly.jwork.service.JWorkService;
import io.leavesfly.jimi.llm.message.Message;
import io.leavesfly.jimi.llm.message.MessageRole;
import io.leavesfly.jimi.llm.message.ToolCall;
import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * 聊天面板
 * 核心交互组件，支持 Markdown 渲染
 */
@Slf4j
public class ChatPane extends BorderPane {
    
    private final JWorkService service;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    
    // UI 组件
    private WebView outputView;
    private WebEngine webEngine;
    private TextField inputField;
    private Button sendButton;
    private Button stopButton;
    
    // 状态
    private WorkSession currentSession;
    private StringBuilder currentOutput;
    private volatile boolean isRunning;
    
    // Todo 更新回调
    private java.util.function.Consumer<TodoInfo.TodoList> todoUpdateCallback;
    
    public ChatPane(JWorkService service) {
        this.service = service;
        this.markdownParser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder().build();
        this.currentOutput = new StringBuilder();
        
        getStyleClass().add("chat-pane");
        initComponents();
        setupLayout();
        setupEventHandlers();
    }
    
    private void initComponents() {
        // 输出区域 - 使用 WebView 渲染 Markdown
        outputView = new WebView();
        // 设置背景透明以适应应用背景
        outputView.setPageFill(javafx.scene.paint.Color.TRANSPARENT);
        webEngine = outputView.getEngine();
        
        // 监听加载状态，加载完成后渲染历史记录
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                if (currentSession != null) {
                    loadHistoryMessages(currentSession);
                }
            }
        });
        
        webEngine.loadContent(getInitialHtml());
        
        // 输入区域
        inputField = new TextField();
        inputField.setPromptText("输入问题或命令...");
        inputField.getStyleClass().addAll("chat-input", Styles.TEXT_SMALL);
        
        sendButton = new Button("发送");
        sendButton.getStyleClass().addAll(Styles.ACCENT, Styles.BUTTON_OUTLINED);
        sendButton.setDisable(true);
        
        stopButton = new Button("停止");
        stopButton.getStyleClass().addAll(Styles.DANGER, Styles.BUTTON_OUTLINED);
        stopButton.setDisable(true);
    }
    
    private void setupLayout() {
        // 输出区域
        VBox outputContainer = new VBox(outputView);
        outputContainer.getStyleClass().add("output-container");
        VBox.setVgrow(outputView, Priority.ALWAYS);
        
        // 输入区域
        HBox inputBox = new HBox(10, inputField, sendButton, stopButton);
        inputBox.setAlignment(Pos.CENTER);
        inputBox.setPadding(new Insets(10));
        inputBox.getStyleClass().add("input-box");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        
        setCenter(outputContainer);
        setBottom(inputBox);
    }
    
    private void setupEventHandlers() {
        // 发送按钮
        sendButton.setOnAction(e -> executeInput());
        
        // 停止按钮
        stopButton.setOnAction(e -> stopExecution());
        
        // 回车发送
        inputField.setOnAction(e -> {
            if (!sendButton.isDisable()) {
                executeInput();
            }
        });
        
        // 输入变化监听
        inputField.textProperty().addListener((obs, old, newVal) -> {
            sendButton.setDisable(newVal.trim().isEmpty() || currentSession == null || isRunning);
        });
    }
    
    /**
     * 设置 Todo 更新回调
     */
    public void setTodoUpdateCallback(java.util.function.Consumer<TodoInfo.TodoList> callback) {
        this.todoUpdateCallback = callback;
    }
    
    /**
     * 设置当前会话
     */
    public void setSession(WorkSession session) {
        this.currentSession = session;
        sendButton.setDisable(session == null || inputField.getText().trim().isEmpty());
        clearOutput();
    }
    
    private void loadHistoryMessages(WorkSession session) {
        log.info("Loading history messages for session: {}", session.getId());
        List<Message> history = service.getSessionMessages(session.getId());
        if (!history.isEmpty()) {
            log.info("Found {} historical messages", history.size());
            StringBuilder fullHtml = new StringBuilder();
            for (Message msg : history) {
                fullHtml.append(getHistoricalMessageHtml(msg));
            }
            appendHtmlOnly(fullHtml.toString());
            appendSystemMessage("已恢复历史会话 (" + history.size() + " 条消息)");
        } else {
            log.info("No historical messages found for session: {}", session.getId());
            appendSystemMessage("会话已就绪: " + session.getDisplayName());
        }
    }
    
    private String getHistoricalMessageHtml(Message msg) {
        StringBuilder sb = new StringBuilder();
        
        // 提取角色字符串进行宽泛匹配
        String roleStr = "";
        if (msg.getRole() != null) {
            roleStr = msg.getRole().getValue();
        }
        
        // 调试：如果角色为空，尝试直接从 Map 中提取 (如果 Jackson 没能正确解析枚举)
        // 注意：这里假设 msg 对象可能未完全解析
        
        if (roleStr == null || roleStr.isEmpty()) {
            log.warn("Historical message role is empty, skipping rendering.");
            return "";
        }

        log.debug("Rendering historical message: role={}, content_type={}", roleStr, 
            msg.getContent() != null ? msg.getContent().getClass().getSimpleName() : "null");

        if ("user".equalsIgnoreCase(roleStr)) {
            String text = msg.getTextContent();
            log.info("Historical User Message: {}", text);
            if (text != null && !text.isEmpty()) {
                sb.append("<div class='message user-message'>")
                  .append("<div class='avatar user-avatar'>👤</div>")
                  .append("<div class='bubble user-bubble'>").append(renderMarkdown(text)).append("</div></div>");
            }
        } else if ("assistant".equalsIgnoreCase(roleStr)) {
            // 处理推理内容 (Reasoning)
            String reasoning = getReasoningContent(msg);
            if (reasoning != null && !reasoning.isEmpty()) {
                sb.append("<div class='message assistant-message'>")
                  .append("<div class='avatar assistant-avatar'>🤖</div>")
                  .append("<div class='bubble assistant-bubble'>")
                  .append("<blockquote class='reasoning'>").append(renderMarkdown(reasoning)).append("</blockquote>")
                  .append("</div></div>");
            }

            // 处理普通内容
            String content = msg.getTextContent();
            if (content != null && !content.isEmpty()) {
                sb.append("<div class='message assistant-message'>")
                  .append("<div class='avatar assistant-avatar'>🤖</div>")
                  .append("<div class='bubble assistant-bubble'>").append(renderMarkdown(content)).append("</div></div>");
            }
            
            // 处理工具调用
            List<ToolCall> calls = msg.getToolCalls();
            if (calls != null && !calls.isEmpty()) {
                for (ToolCall call : calls) {
                    sb.append("<div class='tool-indicator'>🔧 <span class='tool-name'>")
                      .append(escapeHtml(call.getFunction().getName()))
                      .append("</span></div>");
                }
            }
        }
        return sb.toString();
    }

    private String getReasoningContent(Message msg) {
        Object r = msg.getReasoning();
        if (r instanceof String) return (String) r;
        if (r instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object part : (List<?>) r) {
                if (part instanceof String) sb.append(part);
                else if (part instanceof java.util.Map) {
                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) part;
                    if (map.containsKey("text")) sb.append(map.get("text"));
                }
            }
            return sb.toString();
        }
        return null;
    }
    
    /**
     * 渲染历史消息 (已废弃，改用批量渲染)
     */
    private void renderHistoricalMessage(Message msg) {
        appendHtmlOnly(getHistoricalMessageHtml(msg));
    }
    
    /**
     * 执行输入
     */
    private void executeInput() {
        String input = inputField.getText().trim();
        if (input.isEmpty() || currentSession == null) return;
        
        inputField.clear();
        setRunning(true);
        
        // 显示用户输入
        appendUserMessage(input);
        currentOutput = new StringBuilder();
        
        // 执行任务
        service.execute(currentSession.getId(), input)
            .subscribe(
                chunk -> Platform.runLater(() -> handleChunk(chunk)),
                error -> Platform.runLater(() -> {
                    appendErrorMessage(error.getMessage());
                    setRunning(false);
                }),
                () -> Platform.runLater(() -> {
                    finalizeAssistantMessage();
                    setRunning(false);
                })
            );
    }
    
    /**
     * 处理流式输出块
     */
    private void handleChunk(StreamChunk chunk) {
        switch (chunk.getType()) {
            case TEXT -> {
                currentOutput.append(chunk.getContent());
                updateAssistantMessage(currentOutput.toString());
            }
            case TOOL_CALL -> appendToolCall(chunk.getContent());
            case APPROVAL -> showApprovalDialog(chunk.getApproval());
            case STEP_BEGIN -> appendStepMarker("▶ Step started");
            case STEP_END -> appendStepMarker("✓ Step completed");
            case TODO_UPDATE -> {
                // 通知 TimelinePane 更新
                if (todoUpdateCallback != null && chunk.getTodoList() != null) {
                    todoUpdateCallback.accept(chunk.getTodoList());
                }
            }
            case ERROR -> appendErrorMessage(chunk.getContent());
            case DONE -> { /* 完成，不做处理 */ }
            default -> { }
        }
    }
    
    /**
     * 显示审批对话框
     */
    private void showApprovalDialog(ApprovalInfo approval) {
        ApprovalDialog dialog = new ApprovalDialog(approval);
        dialog.showAndWait().ifPresent(response -> {
            service.handleApproval(approval.getToolCallId(), response);
            appendSystemMessage("审批结果: " + response);
        });
    }
    
    /**
     * 停止执行
     */
    private void stopExecution() {
        if (currentSession != null) {
            service.cancelTask(currentSession.getId());
            appendSystemMessage("任务已取消");
            setRunning(false);
        }
    }
    
    private void setRunning(boolean running) {
        this.isRunning = running;
        sendButton.setDisable(running || inputField.getText().trim().isEmpty());
        stopButton.setDisable(!running);
        inputField.setDisable(running);
    }
    
    // ==================== 消息渲染 ====================
    
    private void appendUserMessage(String message) {
        appendUserMessageOnly(message);
        createAssistantPlaceholder();
    }
    
    private void appendUserMessageOnly(String message) {
        String html = 
            "<div class='message user-message'>" +
            "<div class='avatar user-avatar'>👤</div>" +
            "<div class='bubble user-bubble'>" + renderMarkdown(message) + "</div></div>";
        appendHtmlOnly(html);
    }
    
    private void appendHtmlOnly(String html) {
        String escapedHtml = html.replace("\\", "\\\\")
                                 .replace("`", "\\`")
                                 .replace("$", "\\$");
        String script = 
            "var container = document.getElementById('messages');" +
            "var div = document.createElement('div');" +
            "div.innerHTML = `" + escapedHtml + "`;" +
            "while (div.firstChild) {" +
            "  container.appendChild(div.firstChild);" +
            "}" +
            "if(window.highlightAll) highlightAll();";
        try {
            webEngine.executeScript(script);
        } catch (Exception e) {
            log.error("Failed to execute script: {}", e.getMessage());
            // 备选方案：如果 JS 注入失败，尝试最简单的注入
            webEngine.executeScript("document.getElementById('messages').innerHTML += '<div>Error rendering message</div>';");
        }
        scrollToBottom();
    }
    
    private void createAssistantPlaceholder() {
        String script = 
            "var container = document.getElementById('messages');" +
            "var assistantDiv = document.createElement('div');" +
            "assistantDiv.id = 'assistant-current';" +
            "assistantDiv.className = 'message assistant-message';" +
            "assistantDiv.innerHTML = '<div class=\"avatar assistant-avatar\">🤖</div><div class=\"bubble assistant-bubble\"><div class=\"typing\"><span></span><span></span><span></span></div></div>';" +
            "container.appendChild(assistantDiv);";
        webEngine.executeScript(script);
        scrollToBottom();
    }
    
    private void updateAssistantMessage(String markdown) {
        String html = renderMarkdown(markdown);
        String escaped = html.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$");
        String script = 
            "var current = document.getElementById('assistant-current');" +
            "if(current) {" +
            "  var bubble = current.querySelector('.bubble');" +
            "  if(bubble) {" +
            "    bubble.innerHTML = `" + escaped + "`;" +
            "    if(window.highlightAll) highlightAll();" +
            "  }" +
            "}";
        try {
            webEngine.executeScript(script);
        } catch (Exception e) {
            log.warn("Failed to update message: {}", e.getMessage());
        }
        scrollToBottom();
    }
    
    private void finalizeAssistantMessage() {
        String html = renderMarkdown(currentOutput.toString());
        webEngine.executeScript(
            "var current = document.getElementById('assistant-current');" +
            "if(current) {" +
            "  current.removeAttribute('id');" +
            "  if(window.highlightAll) highlightAll();" +
            "}"
        );
    }
    
    private void appendToolCall(String toolName) {
        String html = "<div class='tool-indicator'>🔧 <span class='tool-name'>" + escapeHtml(toolName) + "</span></div>";
        appendHtmlOnly(html);
    }
    
    private void appendStepMarker(String marker) {
        String html = "<div class='step-marker'>" + escapeHtml(marker) + "</div>";
        appendHtmlOnly(html);
    }
    
    private void appendSystemMessage(String message) {
        String html = "<div class='system-message'>" + escapeHtml(message) + "</div>";
        appendHtmlOnly(html);
    }
    
    private void appendErrorMessage(String error) {
        String html = "<div class='error-message'>❌ " + escapeHtml(error) + "</div>";
        appendHtmlOnly(html);
    }
    
    @SuppressWarnings("unused")
    private void appendHtml(String html) {
        appendHtmlOnly(html);
    }
    
    private void scrollToBottom() {
        webEngine.executeScript("window.scrollTo(0, document.body.scrollHeight);");
    }
    
    private void clearOutput() {
        webEngine.loadContent(getInitialHtml());
    }
    
    private String renderMarkdown(String markdown) {
        Node document = markdownParser.parse(markdown);
        return htmlRenderer.render(document);
    }
    
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
    
    private String getInitialHtml() {
        boolean isDark = isDarkTheme();
        String bgColor = isDark ? "#0d1117" : "#ffffff";
        String textColor = isDark ? "#c9d1d9" : "#24292f";
        String bubbleBgUser = isDark ? "#161b22" : "#f6f8fa";
        String bubbleBgAssistant = isDark ? "#0d1117" : "#ffffff";
        String borderColor = isDark ? "#30363d" : "#d0d7de";
        String secondaryTextColor = isDark ? "#8b949e" : "#57606a";
        String accentColor = isDark ? "#58a6ff" : "#0969da";
        String preBg = isDark ? "#161b22" : "#f6f8fa";
        String prismTheme = isDark ? "prism-tomorrow.min.css" : "prism.min.css";
        String headerColor = isDark ? "#f0f6fc" : "#1f2328";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        sb.append("<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/").append(prismTheme).append("'>");
        sb.append("<style>");
        sb.append("* { box-sizing: border-box; margin: 0; padding: 0; }");
        sb.append("html, body { height: 100%; width: 100%; font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', 'PingFang SC', sans-serif; ");
        sb.append("font-size: 13px; line-height: 1.6; background: ").append(bgColor).append("; color: ").append(textColor).append("; ");
        sb.append("scrollbar-width: thin; scrollbar-color: ").append(borderColor).append(" transparent; }");
        sb.append("body::-webkit-scrollbar { width: 8px; }");
        sb.append("body::-webkit-scrollbar-track { background: transparent; }");
        sb.append("body::-webkit-scrollbar-thumb { background: ").append(borderColor).append("; border-radius: 4px; }");
        sb.append("#messages { max-width: 100%; padding: 20px; min-height: 100%; }");
        sb.append(".message { display: flex; margin-bottom: 20px; animation: slideIn 0.3s ease-out; align-items: flex-start; }");
        sb.append("@keyframes slideIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }");
        sb.append(".user-message { flex-direction: row-reverse; }");
        sb.append(".avatar { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
        sb.append(".user-avatar { background: #1f6feb; margin-left: 12px; }");
        sb.append(".assistant-avatar { background: #238636; margin-right: 12px; }");
        sb.append(".bubble { max-width: 85%; padding: 12px 16px; border-radius: 12px; word-wrap: break-word; overflow-wrap: break-word; position: relative; }");
        sb.append(".user-bubble { background: ").append(bubbleBgUser).append("; color: ").append(textColor).append("; border: 1px solid ").append(borderColor).append("; border-top-right-radius: 2px; }");
        sb.append(".assistant-bubble { background: ").append(bubbleBgAssistant).append("; color: ").append(textColor).append("; border: 1px solid ").append(borderColor).append("; border-top-left-radius: 2px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }");
        sb.append(".tool-indicator { background: ").append(bubbleBgUser).append("; padding: 8px 16px; border-radius: 8px; margin: 10px 44px; font-size: 12px; color: ").append(secondaryTextColor).append("; border: 1px solid ").append(borderColor).append("; display: flex; align-items: center; gap: 8px; }");
        sb.append(".tool-name { font-weight: 600; color: ").append(accentColor).append("; }");
        sb.append(".step-marker { color: ").append(secondaryTextColor).append("; font-size: 11px; text-align: center; margin: 10px 0; display: flex; align-items: center; justify-content: center; gap: 10px; }");
        sb.append(".step-marker::before, .step-marker::after { content: ''; height: 1px; background: ").append(borderColor).append("; flex-grow: 1; }");
        sb.append(".system-message { color: ").append(secondaryTextColor).append("; font-style: italic; text-align: center; margin: 15px 0; font-size: 12px; padding: 8px; background: rgba(175, 184, 193, 0.1); border-radius: 6px; }");
        sb.append(".error-message { background: rgba(248, 81, 73, 0.1); color: #cf222e; padding: 12px 16px; border-radius: 8px; margin: 10px 44px; border: 1px solid rgba(248, 81, 73, 0.4); font-size: 13px; }");
        sb.append("pre { background: ").append(preBg).append("; padding: 16px; border-radius: 8px; overflow-x: auto; margin: 12px 0; border: 1px solid ").append(borderColor).append("; position: relative; }");
        sb.append("code { font-family: 'SF Mono', Menlo, Monaco, Consolas, monospace; font-size: 12.5px; background: rgba(175, 184, 193, 0.2); padding: 2px 5px; border-radius: 4px; }");
        sb.append("pre code { background: none; padding: 0; color: inherit; }");
        sb.append(".typing { display: flex; gap: 5px; padding: 8px 0; }");
        sb.append(".typing span { width: 7px; height: 7px; background: #238636; border-radius: 50%; animation: bounce 1.4s infinite ease-in-out; }");
        sb.append(".typing span:nth-child(1) { animation-delay: 0s; }");
        sb.append(".typing span:nth-child(2) { animation-delay: 0.16s; }");
        sb.append(".typing span:nth-child(3) { animation-delay: 0.32s; }");
        sb.append("@keyframes bounce { 0%, 60%, 100% { transform: translateY(0); opacity: 0.4; } 30% { transform: translateY(-6px); opacity: 1; } }");
        sb.append("h1, h2, h3, h4 { margin: 16px 0 8px 0; color: ").append(headerColor).append("; }");
        sb.append("h1 { font-size: 1.5em; border-bottom: 1px solid ").append(borderColor).append("; padding-bottom: 4px; }");
        sb.append("h2 { font-size: 1.3em; border-bottom: 1px solid ").append(borderColor).append("; padding-bottom: 4px; }");
        sb.append("p { margin: 8px 0; }");
        sb.append("ul, ol { padding-left: 20px; margin: 8px 0; }");
        sb.append("li { margin: 4px 0; }");
        sb.append("a { color: ").append(accentColor).append("; text-decoration: none; }");
        sb.append("a:hover { text-decoration: underline; }");
        sb.append("blockquote { border-left: 4px solid ").append(borderColor).append("; margin: 12px 0; padding: 8px 16px; color: ").append(secondaryTextColor).append("; background: rgba(175, 184, 193, 0.1); }");
        sb.append(".reasoning { border-left: 3px solid ").append(accentColor).append(" !important; opacity: 0.8; font-style: italic; font-size: 0.95em; }");
        sb.append("table { border-collapse: collapse; margin: 12px 0; width: 100%; font-size: 12.5px; }");
        sb.append("th, td { border: 1px solid ").append(borderColor).append("; padding: 8px 12px; text-align: left; }");
        sb.append("th { background: ").append(bubbleBgUser).append("; color: ").append(headerColor).append("; }");
        sb.append("code[class*='language-'], pre[class*='language-'] { text-shadow: none; background: transparent; }");
        sb.append("</style></head><body><div id='messages'></div>");
        sb.append("<script src='https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/prism.min.js'></script>");
        sb.append("<script src='https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-java.min.js'></script>");
        sb.append("<script src='https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-bash.min.js'></script>");
        sb.append("<script src='https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-json.min.js'></script>");
        sb.append("<script src='https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-yaml.min.js'></script>");
        sb.append("<script>function scrollToBottom() { window.scrollTo(0, document.body.scrollHeight); }");
        sb.append("function highlightAll() { if (typeof Prism !== 'undefined') { Prism.highlightAll(); } }</script></body></html>");
        
        return sb.toString();
    }

    private boolean isDarkTheme() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac")) {
            try {
                Process process = Runtime.getRuntime().exec("defaults read -g AppleInterfaceStyle");
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    return "Dark".equalsIgnoreCase(line);
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

}

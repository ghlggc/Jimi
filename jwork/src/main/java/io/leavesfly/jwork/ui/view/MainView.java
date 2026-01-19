package io.leavesfly.jwork.ui.view;

import io.leavesfly.jwork.model.WorkSession;
import io.leavesfly.jwork.model.SessionMetadata;
import io.leavesfly.jwork.model.RemoteConnection;
import io.leavesfly.jwork.service.JWorkService;
import io.leavesfly.jwork.ui.component.ChatPane;
import io.leavesfly.jwork.ui.component.TimelinePane;
import io.leavesfly.jwork.ui.component.SkillManagerPane;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * 主视图
 * OpenWork 风格的布局
 */
@Slf4j
public class MainView extends BorderPane {
    
    private final JWorkService service;
    
    // 顶部工具栏
    private Label workspaceLabel;
    private ComboBox<String> agentSelector;
    private Button startButton;
    
    // 左侧导航
    private VBox sidebar;
    private ListView<WorkSession> sessionList;
    
    // 主内容区
    private StackPane contentPane;
    private ChatPane chatPane;
    private TimelinePane timelinePane;
    private SkillManagerPane skillManagerPane;
    
    // 底部状态栏
    private Label statusLabel;
    private ProgressBar progressBar;
    
    // 当前状态
    private Path currentWorkspace;
    private WorkSession currentSession;
    private RemoteConnection connectionConfig = new RemoteConnection();
    
    public MainView(JWorkService service) {
        this.service = service;
        getStyleClass().add("main-view");
        
        initComponents();
        setupLayout();
        setupEventHandlers();
    }
    
    private void initComponents() {
        // 工作区标签
        workspaceLabel = new Label("未选择工作区");
        workspaceLabel.getStyleClass().addAll("workspace-label", Styles.TEXT_MUTED);
        
        // Agent 选择器
        agentSelector = new ComboBox<>();
        agentSelector.getItems().addAll(service.getAvailableAgents());
        agentSelector.setValue("default");
        agentSelector.setPromptText("选择 Agent");
        
        // 开始按钮
        startButton = new Button("开始会话");
        startButton.getStyleClass().addAll(Styles.ACCENT, Styles.BUTTON_OUTLINED);
        startButton.setDisable(true);
        
        // 会话列表
        sessionList = new ListView<>();
        sessionList.setCellFactory(lv -> new SessionListCell());
        sessionList.getStyleClass().addAll("session-list", Styles.STRIPED);
        
        // 内容面板
        chatPane = new ChatPane(service);
        timelinePane = new TimelinePane();
        skillManagerPane = new SkillManagerPane(service);
        
        // 设置 Todo 更新回调，将 ChatPane 收到的 Todo 更新转发到 TimelinePane
        chatPane.setTodoUpdateCallback(todoList -> 
            javafx.application.Platform.runLater(() -> timelinePane.updateTodoList(todoList))
        );
        
        contentPane = new StackPane();
        contentPane.getChildren().add(createWelcomePane());
        
        // 状态栏
        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add(Styles.TEXT_SMALL);
        progressBar = new ProgressBar();
        progressBar.getStyleClass().add(Styles.MEDIUM);
        progressBar.setVisible(false);
        progressBar.setPrefWidth(100);
    }
    
    private void setupLayout() {
        // ===== 顶部工具栏 =====
        Button selectWorkspaceBtn = new Button("选择工作区");
        selectWorkspaceBtn.setOnAction(e -> selectWorkspace());
        
        HBox toolbarLeft = new HBox(10, selectWorkspaceBtn, workspaceLabel);
        toolbarLeft.setAlignment(Pos.CENTER_LEFT);
        
        HBox toolbarRight = new HBox(10, new Label("Agent:"), agentSelector, startButton);
        toolbarRight.setAlignment(Pos.CENTER_RIGHT);
        
        HBox toolbar = new HBox();
        toolbar.getStyleClass().add("toolbar");
        toolbar.setPadding(new Insets(10));
        HBox.setHgrow(toolbarLeft, Priority.ALWAYS);
        toolbar.getChildren().addAll(toolbarLeft, toolbarRight);
        
        setTop(toolbar);
        
        // ===== 左侧导航 =====
        Label sessionsHeader = new Label("会话");
        sessionsHeader.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        
        Button newSessionBtn = new Button("+");
        newSessionBtn.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
        newSessionBtn.setOnAction(e -> createNewSession());
        
        Button historyBtn = new Button("历史");
        historyBtn.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        historyBtn.setOnAction(e -> showHistorySessions());
        
        HBox sessionsTitle = new HBox(10, sessionsHeader, newSessionBtn, historyBtn);
        sessionsTitle.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(sessionsHeader, Priority.ALWAYS);
        
        // 导航按钮
        Button chatNavBtn = createNavButton("会话", () -> showPane(chatPane));
        Button timelineNavBtn = createNavButton("执行计划", () -> showPane(timelinePane));
        Button skillsNavBtn = createNavButton("Skills", () -> showPane(skillManagerPane));
        
        VBox navButtons = new VBox(5, chatNavBtn, timelineNavBtn, skillsNavBtn);
        navButtons.getStyleClass().add("nav-buttons");
        
        // 连接设置按钮
        Button connectionBtn = new Button("⚙ 连接设置");
        connectionBtn.getStyleClass().add("nav-button");
        connectionBtn.setMaxWidth(Double.MAX_VALUE);
        connectionBtn.setOnAction(e -> showConnectionSettings());
        
        sidebar = new VBox(15);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(10));
        sidebar.setPrefWidth(200);
        sidebar.getChildren().addAll(sessionsTitle, sessionList, new Separator(), navButtons, new Separator(), connectionBtn);
        VBox.setVgrow(sessionList, Priority.ALWAYS);
        
        setLeft(sidebar);
        
        // ===== 主内容区 =====
        setCenter(contentPane);
        
        // ===== 底部状态栏 =====
        HBox statusBar = new HBox(10, statusLabel, progressBar);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        
        setBottom(statusBar);
    }
    
    private void setupEventHandlers() {
        // 开始会话按钮
        startButton.setOnAction(e -> createNewSession());
        
        // 会话列表选择
        sessionList.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                if (newVal != null) {
                    selectSession(newVal);
                }
            }
        );
    }
    
    private void selectWorkspace() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择工作区");
        chooser.setInitialDirectory(new File(System.getProperty("user.home")));
        
        File selected = chooser.showDialog(getScene().getWindow());
        if (selected != null) {
            currentWorkspace = selected.toPath();
            workspaceLabel.setText(selected.getName());
            startButton.setDisable(false);
            setStatus("工作区: " + selected.getAbsolutePath());
            log.info("Workspace selected: {}", currentWorkspace);
        }
    }
    
    private void createNewSession() {
        if (currentWorkspace == null) {
            showAlert("请先选择工作区");
            return;
        }
        
        String agentName = agentSelector.getValue();
        WorkSession session = service.createSession(currentWorkspace, agentName);
        
        sessionList.getItems().add(session);
        sessionList.getSelectionModel().select(session);
        
        setStatus("会话已创建: " + session.getDisplayName());
    }
    
    private void selectSession(WorkSession session) {
        currentSession = session;
        chatPane.setSession(session);
        timelinePane.setSession(session);
        showPane(chatPane);
        setStatus("当前会话: " + session.getDisplayName());
    }
    
    private void showPane(Region pane) {
        contentPane.getChildren().clear();
        contentPane.getChildren().add(pane);
    }
    
    private Button createNavButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll(Styles.FLAT, Styles.TEXT_BOLD);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setOnAction(e -> action.run());
        return btn;
    }
    
    private Region createWelcomePane() {
        // Logo 容器
        StackPane logoContainer = new StackPane();
        logoContainer.setPrefSize(140, 140);
        logoContainer.setMaxSize(140, 140);
        
        // 外层六边形
        Region logoShape = new Region();
        logoShape.getStyleClass().add("welcome-logo-shape");
        
        Label logoText = new Label("J");
        logoText.setStyle("-fx-font-size: 70px; -fx-font-weight: bold; -fx-text-fill: white; -fx-padding: 0 0 10 0;");
        
        logoContainer.getChildren().addAll(logoShape, logoText);

        VBox content = new VBox(10);
        content.setAlignment(Pos.CENTER);
        
        Label title = new Label("JWork");
        title.getStyleClass().add("welcome-title");
        
        Label badge = new Label("AI-POWERED");
        badge.setStyle("-fx-background-color: #5c6bc0; -fx-text-fill: white; -fx-padding: 2 8; -fx-background-radius: 10; -fx-font-size: 10px; -fx-font-weight: bold;");
        
        HBox titleRow = new HBox(15, title, badge);
        titleRow.setAlignment(Pos.CENTER);
        
        Label subtitle = new Label("Java 程序员的专属 AI 协作台");
        subtitle.getStyleClass().add("welcome-subtitle");
        
        Label instruction = new Label("选择左侧工作区或历史会话，开启智能开发之旅");
        instruction.getStyleClass().add("welcome-instruction");
        
        VBox welcome = new VBox(30, logoContainer, content, new Separator(), instruction);
        content.getChildren().addAll(titleRow, subtitle);
        
        welcome.setAlignment(Pos.CENTER);
        welcome.getStyleClass().add("welcome-pane");
        welcome.setMaxWidth(600);
        
        return welcome;
    }
    
    private void setStatus(String text) {
        statusLabel.setText(text);
    }
    
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * 显示连接设置对话框
     */
    private void showConnectionSettings() {
        Dialog<RemoteConnection> dialog = new Dialog<>();
        dialog.setTitle("连接设置");
        dialog.setHeaderText("配置 Jimi 连接模式");
        
        // 创建内容
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        // 模式选择
        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton embeddedRadio = new RadioButton("本地嵌入式（默认）");
        embeddedRadio.setToggleGroup(modeGroup);
        embeddedRadio.setSelected(connectionConfig.getMode() == RemoteConnection.Mode.EMBEDDED);
        
        RadioButton remoteRadio = new RadioButton("远程服务器");
        remoteRadio.setToggleGroup(modeGroup);
        remoteRadio.setSelected(connectionConfig.getMode() == RemoteConnection.Mode.REMOTE);
        
        HBox modeBox = new HBox(20, embeddedRadio, remoteRadio);
        
        // 远程服务器配置
        Label urlLabel = new Label("服务器 URL:");
        TextField urlField = new TextField(connectionConfig.getServerUrl() != null ? connectionConfig.getServerUrl() : "");
        urlField.setPromptText("http://localhost:9527");
        urlField.setPrefWidth(300);
        
        Label apiKeyLabel = new Label("API Key (可选):");
        TextField apiKeyField = new TextField(connectionConfig.getApiKey() != null ? connectionConfig.getApiKey() : "");
        apiKeyField.setPromptText("可选");
        
        VBox remoteConfig = new VBox(10, urlLabel, urlField, apiKeyLabel, apiKeyField);
        remoteConfig.setDisable(connectionConfig.getMode() == RemoteConnection.Mode.EMBEDDED);
        
        // 模式切换时禁用/启用远程配置
        embeddedRadio.setOnAction(e -> remoteConfig.setDisable(true));
        remoteRadio.setOnAction(e -> remoteConfig.setDisable(false));
        
        // 提示信息
        Label hint = new Label("注意：远程模式需要先启动 Jimi 服务端 (java -jar jimi.jar --server)");
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");
        hint.setWrapText(true);
        
        content.getChildren().addAll(
            new Label("连接模式:"), modeBox,
            new Separator(),
            remoteConfig,
            new Separator(),
            hint
        );
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return RemoteConnection.builder()
                    .mode(embeddedRadio.isSelected() ? RemoteConnection.Mode.EMBEDDED : RemoteConnection.Mode.REMOTE)
                    .serverUrl(urlField.getText().trim())
                    .apiKey(apiKeyField.getText().trim())
                    .build();
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(config -> {
            this.connectionConfig = config;
            if (config.isRemote()) {
                setStatus("远程模式: " + config.getServerUrl());
                showAlert("远程连接功能开发中...\n当前会使用本地嵌入式模式");
            } else {
                setStatus("本地嵌入式模式");
            }
            log.info("Connection config updated: mode={}, serverUrl={}", config.getMode(), config.getServerUrl());
        });
    }
    
    /**
     * 显示历史会话列表
     */
    private void showHistorySessions() {
        List<SessionMetadata> historyList = service.loadSessionMetadataList();
        
        if (historyList.isEmpty()) {
            showAlert("暂无历史会话");
            return;
        }
        
        // 创建历史会话选择对话框
        Dialog<SessionMetadata> dialog = new Dialog<>();
        dialog.setTitle("历史会话");
        dialog.setHeaderText("选择要恢复的会话");
        
        ListView<SessionMetadata> historyListView = new ListView<>();
        historyListView.getItems().addAll(historyList);
        historyListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SessionMetadata meta, boolean empty) {
                super.updateItem(meta, empty);
                if (empty || meta == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox box = new VBox(4);
                    Label name = new Label(new java.io.File(meta.getWorkDir()).getName() + " (" + meta.getAgentName() + ")");
                    name.setStyle("-fx-font-weight: bold;");
                    Label time = new Label("创建: " + meta.getCreatedAt().toString());
                    time.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");
                    box.getChildren().addAll(name, time);
                    setGraphic(box);
                }
            }
        });
        historyListView.setPrefWidth(400);
        historyListView.setPrefHeight(300);
        
        dialog.getDialogPane().setContent(historyListView);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return historyListView.getSelectionModel().getSelectedItem();
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(selected -> {
            try {
                WorkSession restored = service.restoreSession(selected);
                sessionList.getItems().add(restored);
                sessionList.getSelectionModel().select(restored);
                currentWorkspace = java.nio.file.Paths.get(selected.getWorkDir());
                workspaceLabel.setText(new java.io.File(selected.getWorkDir()).getName());
                startButton.setDisable(false);
                setStatus("会话已恢复: " + restored.getDisplayName());
                log.info("Session restored: {}", restored.getId());
            } catch (Exception e) {
                log.error("Failed to restore session", e);
                showAlert("恢复会话失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 会话列表单元格
     */
    private static class SessionListCell extends ListCell<WorkSession> {
        @Override
        protected void updateItem(WorkSession session, boolean empty) {
            super.updateItem(session, empty);
            if (empty || session == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox box = new VBox(2);
                Label name = new Label(session.getDisplayName());
                name.getStyleClass().add("session-name");
                Label status = new Label(session.isRunning() ? "运行中" : "空闲");
                status.getStyleClass().add("session-status");
                box.getChildren().addAll(name, status);
                setGraphic(box);
            }
        }
    }
}

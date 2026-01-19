package io.leavesfly.jwork.ui.component;

import io.leavesfly.jwork.model.TodoInfo;
import io.leavesfly.jwork.model.WorkSession;
import atlantafx.base.theme.Styles;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.extern.slf4j.Slf4j;

/**
 * 执行时间线面板
 * 展示任务计划进度
 */
@Slf4j
public class TimelinePane extends BorderPane {
    
    private final ObservableList<TodoInfo> todos = FXCollections.observableArrayList();
    private ListView<TodoInfo> todoList;
    private Label statsLabel;
    private WorkSession currentSession;
    
    public TimelinePane() {
        getStyleClass().add("timeline-pane");
        initComponents();
        setupLayout();
    }
    
    private void initComponents() {
        todoList = new ListView<>(todos);
        todoList.setCellFactory(lv -> new TodoCell());
        todoList.getStyleClass().addAll("todo-list", Styles.STRIPED);
        todoList.setPlaceholder(new Label("暂无执行计划"));
    }
    
    private void setupLayout() {
        // 标题栏
        Label title = new Label("执行计划");
        title.getStyleClass().addAll(Styles.TITLE_3);
        
        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        refreshBtn.setOnAction(e -> refreshTodos());
        
        HBox header = new HBox(10, title, refreshBtn);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setPadding(new Insets(10));
        HBox.setHgrow(title, Priority.ALWAYS);
        
        // 统计信息
        statsLabel = new Label("等待任务...");
        statsLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        
        VBox footer = new VBox(statsLabel);
        footer.setPadding(new Insets(10));
        footer.getStyleClass().add("timeline-footer");
        
        setTop(header);
        setCenter(todoList);
        setBottom(footer);
    }
    
    /**
     * 设置当前会话
     */
    public void setSession(WorkSession session) {
        this.currentSession = session;
        todos.clear();
        if (session != null) {
            refreshTodos();
        }
    }
    
    /**
     * 批量更新任务列表（从 Wire 消息）
     */
    public void updateTodoList(TodoInfo.TodoList todoListData) {
        todos.clear();
        if (todoListData != null && todoListData.getTodos() != null) {
            todos.addAll(todoListData.getTodos());
        }
        
        // 更新统计信息
        if (todoListData != null) {
            statsLabel.setText(String.format(
                "总计: %d | 完成: %d | 进行中: %d | 待办: %d",
                todoListData.getTotalCount(),
                todoListData.getDoneCount(),
                todoListData.getInProgressCount(),
                todoListData.getPendingCount()
            ));
        }
        
        log.debug("Updated todo list with {} items", todos.size());
    }
    
    /**
     * 添加或更新单个任务
     */
    public void updateTodo(TodoInfo todo) {
        int index = findTodoIndex(todo.getId());
        if (index >= 0) {
            todos.set(index, todo);
        } else {
            todos.add(todo);
        }
    }
    
    /**
     * 刷新任务列表
     */
    private void refreshTodos() {
        // TODO: 从会话获取任务列表
        log.debug("Refreshing todos for session: {}", 
            currentSession != null ? currentSession.getId() : "none");
    }
    
    private int findTodoIndex(String id) {
        for (int i = 0; i < todos.size(); i++) {
            if (todos.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 任务列表单元格
     */
    private static class TodoCell extends ListCell<TodoInfo> {
        @Override
        protected void updateItem(TodoInfo todo, boolean empty) {
            super.updateItem(todo, empty);
            if (empty || todo == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            
            HBox box = new HBox(12);
            box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            box.setPadding(new Insets(8, 4, 8, 4));
            
            // 状态图标
            Label statusIcon = new Label(todo.getStatusIcon());
            statusIcon.getStyleClass().add(Styles.TEXT_BOLD);
            
            // 内容
            Label content = new Label(todo.getContent());
            content.setWrapText(true);
            content.getStyleClass().add(Styles.TEXT_SMALL);
            HBox.setHgrow(content, Priority.ALWAYS);
            
            // 状态标签
            Label statusLabel = new Label(todo.getStatus().name());
            statusLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
            
            // 根据状态应用不同颜色
            switch (todo.getStatus()) {
                case PENDING -> statusIcon.getStyleClass().add(Styles.WARNING);
                case IN_PROGRESS -> statusIcon.getStyleClass().add(Styles.ACCENT);
                case COMPLETE -> statusIcon.getStyleClass().add(Styles.SUCCESS);
                case ERROR -> statusIcon.getStyleClass().add(Styles.DANGER);
                case CANCELLED -> statusIcon.getStyleClass().add(Styles.TEXT_MUTED);
            }
            
            box.getChildren().addAll(statusIcon, content, statusLabel);
            setGraphic(box);
        }
    }
}

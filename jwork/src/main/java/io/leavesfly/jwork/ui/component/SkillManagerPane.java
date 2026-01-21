package io.leavesfly.jwork.ui.component;

import io.leavesfly.jwork.model.SkillInfo;
import io.leavesfly.jwork.service.JWorkService;
import atlantafx.base.theme.Styles;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.extern.slf4j.Slf4j;

import javafx.stage.DirectoryChooser;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Skills 管理面板
 */
@Slf4j
public class SkillManagerPane extends BorderPane {
    
    private final JWorkService service;
    private final ObservableList<SkillInfo> skills = FXCollections.observableArrayList();
    
    private ListView<SkillInfo> skillList;
    private TextArea detailArea;
    private TextField searchField;
    
    public SkillManagerPane(JWorkService service) {
        this.service = service;
        getStyleClass().add("skill-manager-pane");
        
        initComponents();
        setupLayout();
        loadSkills();
    }
    
    private void initComponents() {
        // 搜索框
        searchField = new TextField();
        searchField.setPromptText("搜索 Skills...");
        searchField.getStyleClass().addAll(Styles.TEXT_SMALL);
        
        // Skills 列表
        skillList = new ListView<>(skills);
        skillList.setCellFactory(lv -> new SkillCell());
        skillList.getStyleClass().addAll(Styles.STRIPED);
        skillList.setPlaceholder(new Label("暂无 Skills"));
        
        // 详情区域
        detailArea = new TextArea();
        detailArea.setEditable(false);
        detailArea.setWrapText(true);
        detailArea.setPromptText("选择一个 Skill 查看详情");
        detailArea.getStyleClass().addAll(Styles.TEXT_SMALL, "detail-area");
    }
    
    private void setupLayout() {
        // 标题栏
        Label title = new Label("Skills 管理");
        title.getStyleClass().add(Styles.TITLE_3);
        
        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        refreshBtn.setOnAction(e -> loadSkills());
        
        Button installBtn = new Button("安装");
        installBtn.getStyleClass().addAll(Styles.SMALL, Styles.ACCENT);
        installBtn.setOnAction(e -> showInstallDialog());
        
        Button uninstallBtn = new Button("卸载");
        uninstallBtn.getStyleClass().addAll(Styles.SMALL, Styles.DANGER, Styles.BUTTON_OUTLINED);
        uninstallBtn.setOnAction(e -> uninstallSelectedSkill());
        
        HBox header = new HBox(10, title, refreshBtn, installBtn, uninstallBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10));
        HBox.setHgrow(title, Priority.ALWAYS);
        
        // 左侧：搜索 + 列表
        VBox leftPane = new VBox(10, searchField, skillList);
        leftPane.setPadding(new Insets(10));
        leftPane.setPrefWidth(300);
        VBox.setVgrow(skillList, Priority.ALWAYS);
        
        // 右侧：详情
        VBox rightPane = new VBox(10);
        rightPane.setPadding(new Insets(10));
        
        Label detailTitle = new Label("Skill 详情");
        detailTitle.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED);
        
        rightPane.getChildren().addAll(detailTitle, detailArea);
        VBox.setVgrow(detailArea, Priority.ALWAYS);
        
        // 主内容
        SplitPane splitPane = new SplitPane(leftPane, rightPane);
        splitPane.setDividerPositions(0.4);
        
        setTop(header);
        setCenter(splitPane);
        
        // 选择监听
        skillList.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, newVal) -> showSkillDetail(newVal)
        );
        
        // 搜索过滤
        searchField.textProperty().addListener((obs, old, newVal) -> filterSkills(newVal));
    }
    
    private void loadSkills() {
        skills.clear();
        List<SkillInfo> loaded = service.getAllSkills();
        skills.addAll(loaded);
        log.info("Loaded {} skills", loaded.size());
    }
    
    private void filterSkills(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            loadSkills();
            return;
        }
        
        String lower = keyword.toLowerCase();
        List<SkillInfo> filtered = service.getAllSkills().stream()
            .filter(s -> s.getName().toLowerCase().contains(lower) ||
                        (s.getDescription() != null && s.getDescription().toLowerCase().contains(lower)))
            .toList();
        
        skills.clear();
        skills.addAll(filtered);
    }
    
    private void showSkillDetail(SkillInfo skill) {
        if (skill == null) {
            detailArea.clear();
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("名称: ").append(skill.getName()).append("\n\n");
        sb.append("描述: ").append(skill.getDescription()).append("\n\n");
        sb.append("版本: ").append(skill.getVersion()).append("\n");
        sb.append("分类: ").append(skill.getCategory()).append("\n");
        sb.append("作用域: ").append(skill.getScope()).append("\n");
        sb.append("状态: ").append(skill.isInstalled() ? "已安装" : "未安装");
        
        detailArea.setText(sb.toString());
    }
    
    private void showInstallDialog() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择 Skill 目录");
        chooser.setInitialDirectory(new File(System.getProperty("user.home")));
        
        File selected = chooser.showDialog(getScene().getWindow());
        if (selected != null) {
            Path skillPath = selected.toPath();
            
            // 检查是否包含 SKILL.md
            if (!skillPath.resolve("SKILL.md").toFile().exists()) {
                showAlert("无效的 Skill 目录，缺少 SKILL.md 文件");
                return;
            }
            
            try {
                service.installSkill(skillPath);
                loadSkills();
                showInfo("Skill 安装成功: " + selected.getName());
                log.info("Skill installed from: {}", skillPath);
            } catch (Exception ex) {
                log.error("Failed to install skill", ex);
                showAlert("安装失败: " + ex.getMessage());
            }
        }
    }
    
    private void uninstallSelectedSkill() {
        SkillInfo selected = skillList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("请先选择要卸载的 Skill");
            return;
        }
        
        // 确认对话框
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认卸载");
        confirm.setHeaderText(null);
        confirm.setContentText("确定要卸载 Skill \"" + selected.getName() + "\" 吗？");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                service.uninstallSkill(selected.getName());
                loadSkills();
                detailArea.clear();
                showInfo("Skill 卸载成功: " + selected.getName());
                log.info("Skill uninstalled: {}", selected.getName());
            } catch (Exception ex) {
                log.error("Failed to uninstall skill", ex);
                showAlert("卸载失败: " + ex.getMessage());
            }
        }
    }
    
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("警告");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Skill 列表单元格
     */
    private static class SkillCell extends ListCell<SkillInfo> {
        @Override
        protected void updateItem(SkillInfo skill, boolean empty) {
            super.updateItem(skill, empty);
            if (empty || skill == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            
            VBox box = new VBox(4);
            box.setPadding(new Insets(4, 0, 4, 0));
            
            HBox titleRow = new HBox(8);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label(skill.getName());
            name.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_SMALL);
            
            Label scope = new Label(skill.getScope() == SkillInfo.Scope.GLOBAL ? "全局" : "项目");
            scope.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED, Styles.SMALL);
            if (skill.getScope() == SkillInfo.Scope.GLOBAL) {
                scope.getStyleClass().add(Styles.SUCCESS);
            } else {
                scope.getStyleClass().add(Styles.WARNING);
            }
            
            titleRow.getChildren().addAll(name, scope);
            
            Label desc = new Label(skill.getDescription());
            desc.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
            desc.setWrapText(true);
            
            box.getChildren().addAll(titleRow, desc);
            setGraphic(box);
        }
    }
}

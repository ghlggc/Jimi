package io.leavesfly.jwork.ui.component;

import io.leavesfly.jwork.model.ApprovalInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

/**
 * 审批对话框
 * 展示待审批操作，让用户选择批准或拒绝
 */
public class ApprovalDialog extends Dialog<ApprovalInfo.Response> {
    
    private final ApprovalInfo approval;
    
    public ApprovalDialog(ApprovalInfo approval) {
        this.approval = approval;
        
        setTitle("需要审批");
        initModality(Modality.APPLICATION_MODAL);
        setResizable(false);
        
        initUI();
        setupResultConverter();
    }
    
    private void initUI() {
        // 图标
        Label icon = new Label("⚠️");
        icon.setStyle("-fx-font-size: 48px;");
        
        // 操作名称
        Label actionLabel = new Label("操作: " + approval.getAction());
        actionLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        // 描述
        TextArea descArea = new TextArea(approval.getDescription());
        descArea.setEditable(false);
        descArea.setWrapText(true);
        descArea.setPrefRowCount(6);
        descArea.setPrefWidth(400);
        descArea.setStyle("-fx-font-family: monospace;");
        
        // 警告提示
        Label warning = new Label("此操作可能修改文件或执行命令，请仔细确认");
        warning.setStyle("-fx-text-fill: #f57c00; -fx-font-size: 12px;");
        
        // 布局
        VBox content = new VBox(15);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20));
        content.getChildren().addAll(icon, actionLabel, descArea, warning);
        
        getDialogPane().setContent(content);
        
        // 按钮
        ButtonType approveBtn = new ButtonType("批准", ButtonBar.ButtonData.OK_DONE);
        ButtonType approveSessionBtn = new ButtonType("本次会话批准", ButtonBar.ButtonData.YES);
        ButtonType rejectBtn = new ButtonType("拒绝", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        getDialogPane().getButtonTypes().addAll(approveBtn, approveSessionBtn, rejectBtn);
        
        // 样式化按钮
        Button approve = (Button) getDialogPane().lookupButton(approveBtn);
        approve.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white;");
        
        Button reject = (Button) getDialogPane().lookupButton(rejectBtn);
        reject.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
    }
    
    private void setupResultConverter() {
        setResultConverter(buttonType -> {
            if (buttonType.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                return ApprovalInfo.Response.APPROVE;
            } else if (buttonType.getButtonData() == ButtonBar.ButtonData.YES) {
                return ApprovalInfo.Response.APPROVE_SESSION;
            } else {
                return ApprovalInfo.Response.REJECT;
            }
        });
    }
}

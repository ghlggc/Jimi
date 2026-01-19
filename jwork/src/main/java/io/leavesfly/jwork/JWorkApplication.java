package io.leavesfly.jwork;

import io.leavesfly.jwork.service.JWorkService;
import io.leavesfly.jwork.ui.view.MainView;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * JWork 桌面应用入口
 * 
 * OpenWork style AI agent interface
 */
@Slf4j
public class JWorkApplication extends Application {
    
    private JWorkService service;
    
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void init() throws Exception {
        log.info("Initializing JWork...");
        service = new JWorkService();
        applySystemTheme();
    }
    
    private void applySystemTheme() {
        try {
            if (isMacDarkTheme()) {
                log.info("System dark theme detected, applying PrimerDark");
                Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            } else {
                log.info("System light theme detected, applying PrimerLight");
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            }
        } catch (Exception e) {
            log.warn("Failed to detect system theme, defaulting to PrimerDark", e);
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        }
    }
    
    private boolean isMacDarkTheme() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac")) {
            try {
                Process process = Runtime.getRuntime().exec("defaults read -g AppleInterfaceStyle");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    return "Dark".equalsIgnoreCase(line);
                }
            } catch (Exception e) {
                // 如果命令执行失败（通常是因为是 Light 模式，该键不存在），则返回 false
                return false;
            }
        }
        return true; // 其他平台默认使用 dark，或者可以根据需要调整
    }

    
    @Override
    public void start(Stage primaryStage) {
        log.info("Starting JWork GUI...");
        
        // 显示加载界面
        StackPane loadingPane = new StackPane();
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(100, 100);
        loadingPane.getChildren().add(progress);
        loadingPane.setStyle("-fx-background-color: #1a1a2e;");
        
        Scene loadingScene = new Scene(loadingPane, 400, 300);
        primaryStage.setTitle("JWork - Starting...");
        primaryStage.setScene(loadingScene);
        primaryStage.show();
        
        // 在后台等待初始化完成
        Thread waitThread = new Thread(() -> {
            boolean ready = service.waitForInit(30, TimeUnit.SECONDS);
            
            Platform.runLater(() -> {
                if (ready && service.isInitialized()) {
                    showMainView(primaryStage);
                } else {
                    showError(primaryStage, "启动失败", "Jimi 引擎初始化超时，请检查日志。");
                }
            });
        }, "jwork-wait-init");
        waitThread.setDaemon(true);
        waitThread.start();
        
        // 设置窗口关闭处理
        primaryStage.setOnCloseRequest(event -> {
            log.info("Window close requested");
            Platform.exit();
        });
    }
    
    private void showMainView(Stage primaryStage) {
        try {
            MainView mainView = new MainView(service);
            Scene scene = new Scene(mainView, 1200, 800);
            
            // 加载样式
            String css = Objects.requireNonNull(
                getClass().getResource("/css/jwork.css")
            ).toExternalForm();
            scene.getStylesheets().add(css);
            
            primaryStage.setTitle("JWork - Jimi AI Assistant");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            
            // 尝试加载图标
            try {
                primaryStage.getIcons().add(
                    new Image(Objects.requireNonNull(
                        getClass().getResourceAsStream("/images/logo.png")
                    ))
                );
            } catch (Exception e) {
                log.debug("Logo not found, using default icon");
            }
            
            log.info("JWork started successfully");
            
        } catch (Exception e) {
            log.error("Failed to start JWork", e);
            showError(primaryStage, "启动失败", e.getMessage());
        }
    }
    
    private void showError(Stage stage, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        Platform.exit();
    }
    
    @Override
    public void stop() throws Exception {
        log.info("Stopping JWork...");
        System.out.println("JWork stopping...");
        
        // 创建一个守护线程来强制退出，以防正常关闭挂起
        Thread forceExitThread = new Thread(() -> {
            try {
                Thread.sleep(3000); // 缩短到 3 秒
                log.warn("Shutdown taking too long, forcing exit via halt(0)...");
                System.err.println("Shutdown taking too long, forcing exit via halt(0)...");
                Runtime.getRuntime().halt(0);
            } catch (InterruptedException e) {
                // Ignore
            }
        });
        forceExitThread.setDaemon(true);
        forceExitThread.start();

        if (service != null) {
            // 在独立线程中关闭，避免阻塞 JavaFX 线程
            Thread shutdownThread = new Thread(() -> {
                try {
                    service.shutdown();
                } catch (Exception e) {
                    log.error("Error during shutdown", e);
                }
            }, "jwork-shutdown");
            shutdownThread.start();
            
            // 等待关闭完成（最多2.5秒，给 forceExitThread 留点余量）
            try {
                shutdownThread.join(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("JWork stopped");
        System.out.println("JWork exit.");
        
        // 最终强制退出，不走正常的 System.exit(0)，因为它可能被挂起的 shutdown hook 阻塞
        Runtime.getRuntime().halt(0);
    }
}

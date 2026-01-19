# JWork 技术设计文档

## 1. 概述

JWork 是 Jimi 的桌面 GUI 模块，参考 OpenWork 设计理念，提供可视化的 AI 代理交互体验。

**核心特性**：
- 工作区选择与管理
- 会话管理与流式输出
- 执行计划时间线
- 权限审批中心
- Skills 管理器
- Templates 模板复用

## 2. 架构设计

### 2.1 模块关系

```
jwork (桌面应用)
    │
    └── 直接依赖 ──→ jimi (核心模块)
                      ├── JimiFactory
                      ├── JimiEngine
                      ├── Wire (消息总线)
                      ├── Approval (审批)
                      ├── SkillRegistry
                      └── TemplateRegistry (新增)
```

### 2.2 分层架构

```
┌─────────────────────────────────────────────────┐
│                  UI Layer (JavaFX)              │
│  MainView / SessionView / SkillView / Timeline  │
├─────────────────────────────────────────────────┤
│                Controller Layer                  │
│  MainController / SessionController / ...        │
├─────────────────────────────────────────────────┤
│                 Service Layer                    │
│  JWorkService / SessionManager / SkillManager    │
├─────────────────────────────────────────────────┤
│                  Jimi Core                       │
│  JimiEngine / Wire / Approval / SkillRegistry    │
└─────────────────────────────────────────────────┘
```

## 3. 目录结构

```
jwork/
├── pom.xml
├── docs/
│   └── TECHNICAL_DESIGN.md
└── src/main/
    ├── java/io/leavesfly/jwork/
    │   ├── JWorkApplication.java          # 入口
    │   ├── service/
    │   │   ├── JWorkService.java          # 核心服务
    │   │   ├── SessionManager.java        # 会话管理
    │   │   └── TemplateManager.java       # 模板管理
    │   ├── controller/
    │   │   ├── MainController.java        # 主控制器
    │   │   └── SessionController.java     # 会话控制器
    │   ├── model/
    │   │   ├── WorkSession.java           # 会话模型
    │   │   ├── StreamChunk.java           # 流式输出块
    │   │   └── Template.java              # 模板模型
    │   └── ui/
    │       ├── view/
    │       │   ├── MainView.java          # 主视图
    │       │   ├── SessionView.java       # 会话视图
    │       │   └── SkillManagerView.java  # Skills视图
    │       └── component/
    │           ├── ChatPane.java          # 聊天组件
    │           ├── TimelinePane.java      # 时间线组件
    │           └── ApprovalDialog.java    # 审批弹窗
    └── resources/
        ├── css/jwork.css
        └── images/logo.png
```

## 4. 核心类设计

### 4.1 JWorkService

```java
/**
 * 核心服务 - 封装 Jimi 调用
 */
public class JWorkService {
    
    // 依赖 Jimi 核心组件
    private final JimiFactory jimiFactory;
    private final SkillRegistry skillRegistry;
    private final TemplateRegistry templateRegistry;
    
    // 会话管理
    private final Map<String, WorkSession> sessions;
    
    // 核心方法
    WorkSession createSession(Path workDir, String agent);
    Flux<StreamChunk> execute(String sessionId, String input);
    void handleApproval(String toolCallId, ApprovalAction action);
    void cancelJob(String sessionId);
}
```

### 4.2 WorkSession

```java
/**
 * 工作会话 - 封装 JimiEngine
 */
public class WorkSession {
    private final String id;
    private final JimiEngine engine;
    private final Path workDir;
    private final String agentName;
    private final LocalDateTime createdAt;
    
    // 当前任务状态
    private volatile String currentJobId;
    private volatile boolean running;
}
```

### 4.3 StreamChunk

```java
/**
 * 流式输出块 - UI 展示单元
 */
public class StreamChunk {
    enum Type { TEXT, TOOL_CALL, TOOL_RESULT, APPROVAL, STEP_BEGIN, STEP_END, ERROR }
    
    private final Type type;
    private final String content;
    private final ApprovalInfo approval;  // 仅 APPROVAL 类型使用
}
```

## 5. 通信流程

### 5.1 执行流程

```
User Input
    │
    ▼
JWorkService.execute(sessionId, input)
    │
    ▼
JimiEngine.run(input)
    │
    ├──▶ Wire.asFlux() ──▶ StreamChunk 转换 ──▶ UI 更新
    │
    └──▶ Approval 请求 ──▶ ApprovalDialog ──▶ 用户响应 ──▶ 继续执行
```

### 5.2 Wire 消息映射

| Wire MessageType | StreamChunk Type | UI 处理 |
|------------------|------------------|---------|
| content_part     | TEXT             | 追加文本 |
| tool_call        | TOOL_CALL        | 显示工具调用 |
| tool_result      | TOOL_RESULT      | 显示结果 |
| approval_required| APPROVAL         | 弹出审批对话框 |
| step_begin       | STEP_BEGIN       | 时间线标记 |
| step_end         | STEP_END         | 时间线完成 |

## 6. Jimi 核心扩展

### 6.1 TemplateRegistry (新增)

位置: `jimi/src/main/java/io/leavesfly/jimi/template/`

```java
@Service
public class TemplateRegistry {
    Template create(Template template);
    void update(Template template);
    void delete(String id);
    List<Template> listAll();
    String apply(String templateId, Map<String, String> variables);
}
```

### 6.2 SkillRegistry 增强

新增方法:
- `install(Path skillPath)` - 安装 Skill
- `uninstall(String name)` - 卸载 Skill
- `listAll()` - 获取所有 Skills 信息

## 7. 依赖配置

```xml
<dependencies>
    <!-- Jimi 核心 -->
    <dependency>
        <groupId>io.leavesfly</groupId>
        <artifactId>jimi</artifactId>
        <version>${project.version}</version>
    </dependency>
    
    <!-- JavaFX -->
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-controls</artifactId>
        <version>21</version>
    </dependency>
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-web</artifactId>
        <version>21</version>
    </dependency>
    
    <!-- Markdown 渲染 -->
    <dependency>
        <groupId>org.commonmark</groupId>
        <artifactId>commonmark</artifactId>
        <version>0.21.0</version>
    </dependency>
</dependencies>
```

## 8. 实施计划

| 阶段 | 内容 | 预计时间 |
|------|------|---------|
| 1 | 基础框架：模块结构、入口类、核心服务 | 2天 |
| 2 | 核心交互：ChatPane、流式输出、审批 | 2-3天 |
| 3 | Jimi扩展：TemplateRegistry、SkillRegistry增强 | 1-2天 |
| 4 | 高级功能：时间线、Skills管理、模板管理 | 2-3天 |
| 5 | 打包发布：可执行JAR、启动脚本 | 1天 |

## 9. 兼容性说明

- **对 Jimi 核心的影响**: 仅新增 TemplateRegistry 和 SkillRegistry 少量方法，不修改现有逻辑
- **对现有功能的影响**: 无，JWork 是独立模块，不影响 CLI 和 IDEA 插件
- **向后兼容**: 完全兼容

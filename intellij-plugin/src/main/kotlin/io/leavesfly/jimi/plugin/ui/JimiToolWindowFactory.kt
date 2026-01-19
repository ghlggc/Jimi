package io.leavesfly.jimi.plugin.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.contents.DiffContentFactory
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.diff.impl.patch.apply.ApplyPatchStatus
import com.intellij.openapi.diff.impl.patch.apply.ApplyTextFilePatch
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.leavesfly.jimi.plugin.JimiPluginService
import io.leavesfly.jimi.plugin.ApprovalInfo
import java.awt.*
import java.nio.charset.StandardCharsets
import javax.swing.*
import javax.swing.text.*

class JimiToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JimiToolWindowPanel(project)
        val contentFactory = ApplicationManager.getApplication().getService(ContentFactory::class.java)
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class JimiToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val outputPane = JTextPane().apply {
        isEditable = false
        background = Color.WHITE
        border = JBUI.Borders.empty(8)
    }
    
    private val styledDoc = outputPane.styledDocument
    private val styles = mutableMapOf<String, Style>()
    
    private val inputField = JTextField().apply {
        font = Font("Monospaced", Font.PLAIN, 13)
        border = JBUI.Borders.empty(8)
    }
    
    private val sendButton = JButton("Send").apply {
        preferredSize = Dimension(80, 32)
    }
    
    private val stopButton = JButton("Stop").apply {
        preferredSize = Dimension(80, 32)
        isEnabled = false
    }
    
    // 应用助手返回的 diff 补丁
    private val applyButton = JButton("Apply Patch").apply {
        preferredSize = Dimension(120, 32)
        isEnabled = false
    }
    
    // 预览 diff，不直接改文件
    private val previewButton = JButton("Preview Diff").apply {
        preferredSize = Dimension(120, 32)
        isEnabled = false
    }
    
    private val clearButton = JButton("Clear").apply {
        preferredSize = Dimension(70, 32)
    }

    private val statusLabel = JLabel("Ready").apply {
        foreground = JBColor(Color(96, 125, 139), Color(144, 164, 174))
        border = JBUI.Borders.empty(0, 4, 0, 4)
    }
    
    private val includeSelectionCheck = JCheckBox("Selection", true)
    private val includeFileCheck = JCheckBox("File", true)
    private val includeProblemsCheck = JCheckBox("Problems", true)
    
    @Volatile
    private var currentJobId: String? = null
    
    @Volatile
    private var stopRequested: Boolean = false
    
    // 缓存本次助手输出，便于提取 diff
    private var currentAssistantBuffer = StringBuilder()
    private var lastAssistantOutput: String? = null
    
    init {
        initStyles()
        setupUI()
        sendButton.addActionListener { executeInput() }
        stopButton.addActionListener { requestStop() }
        applyButton.addActionListener { applyLastPatch() }
        previewButton.addActionListener { previewLastPatch() }
        clearButton.addActionListener { clearOutput() }
        inputField.addActionListener { executeInput() }
        showWelcomeMessage()
    }
    
    private fun initStyles() {
        styles["normal"] = styledDoc.addStyle("normal", null).apply {
            StyleConstants.setFontFamily(this, "Monospaced")
            StyleConstants.setFontSize(this, 13)
            StyleConstants.setForeground(this, UIUtil.getLabelForeground())
        }
        styles["user"] = styledDoc.addStyle("user", styles["normal"]).apply {
            StyleConstants.setBold(this, true)
            StyleConstants.setForeground(this, JBColor(Color(33, 150, 243), Color(100, 181, 246)))
        }
        styles["assistant"] = styledDoc.addStyle("assistant", styles["normal"]).apply {
            StyleConstants.setForeground(this, UIUtil.getLabelForeground())
        }
        styles["error"] = styledDoc.addStyle("error", styles["normal"]).apply {
            StyleConstants.setForeground(this, JBColor(Color(244, 67, 54), Color(229, 115, 115)))
        }
        styles["info"] = styledDoc.addStyle("info", styles["normal"]).apply {
            StyleConstants.setForeground(this, JBColor(Color(96, 125, 139), Color(144, 164, 174)))
        }
    }
    
    private fun setupUI() {
        val scrollPane = JBScrollPane(outputPane).apply {
            border = JBUI.Borders.empty()
        }
        add(scrollPane, BorderLayout.CENTER)
        
        val inputPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
        }
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        buttonPanel.add(clearButton)
        buttonPanel.add(stopButton)
        buttonPanel.add(applyButton)
        buttonPanel.add(previewButton)
        buttonPanel.add(sendButton)
        inputPanel.add(inputField, BorderLayout.CENTER)
        inputPanel.add(buttonPanel, BorderLayout.EAST)
        val southPanel = JPanel(BorderLayout())
        val contextPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JLabel("Context:"))
            add(includeSelectionCheck)
            add(includeFileCheck)
            add(includeProblemsCheck)
        }
        val statusPanel = JPanel(BorderLayout()).apply {
            add(statusLabel, BorderLayout.WEST)
            add(contextPanel, BorderLayout.EAST)
        }
        southPanel.add(statusPanel, BorderLayout.NORTH)
        southPanel.add(inputPanel, BorderLayout.CENTER)
        add(southPanel, BorderLayout.SOUTH)
    }
    
    private fun showWelcomeMessage() {
        appendText("=".repeat(40) + "\n", "info")
        appendText("  Jimi AI Assistant\n", "info")
        appendText("=".repeat(40) + "\n\n", "info")
        appendText("Ready. 输入问题或 /help 查看命令\n\n", "info")
        setStatus("Ready")
    }
    
    private fun clearOutput() {
        try {
            styledDoc.remove(0, styledDoc.length)
            showWelcomeMessage()
        } catch (_: BadLocationException) {}
    }

    fun submitExternalInput(input: String) {
        SwingUtilities.invokeLater {
            inputField.text = input
            inputField.requestFocusInWindow()
            executeInput()
        }
    }

    private fun setStatus(text: String, isError: Boolean = false) {
        statusLabel.text = text
        statusLabel.foreground = if (isError) {
            JBColor(Color(244, 67, 54), Color(229, 115, 115))
        } else {
            JBColor(Color(96, 125, 139), Color(144, 164, 174))
        }
    }
    
    private fun executeInput() {
        val input = inputField.text.trim()
        if (input.isEmpty()) return
        
        inputField.text = ""
        setRunningState(true)
        
        // 显示用户输入
        appendText("👤 ", "user")
        appendText("$input\n\n", "user")
        
        setStatus("Running...")
        appendText("🤖 ", "info")
        currentAssistantBuffer = StringBuilder()
        lastAssistantOutput = null
        // 新一轮请求前先禁用 diff 按钮
        applyButton.isEnabled = false
        previewButton.isEnabled = false
        
        val service = JimiPluginService.getInstance(project)
        var hasOutput = false
        
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val contextPrefix = buildContextPrefix()
                val finalInput = if (contextPrefix.isBlank()) input else "$contextPrefix\n\n$input"
                val jobId = service.startStream(finalInput)
                if (jobId == null) {
                    SwingUtilities.invokeLater {
                        appendText("Error: failed to start stream\n\n", "error")
                        setStatus("Error", isError = true)
                        setRunningState(false)
                    }
                    return@executeOnPooledThread
                }
                currentJobId = jobId
                
                var cursor = 0
                var error: String? = null
                
                while (true) {
                    if (stopRequested) {
                        break
                    }
                    val output = service.pollOutput(jobId, cursor)
                    if (output == null) {
                        error = "Failed to poll output"
                        break
                    }
                    if (output.chunks.isNotEmpty()) {
                        SwingUtilities.invokeLater {
                            hasOutput = true
                            output.chunks.forEach {
                                currentAssistantBuffer.append(it)
                                appendText(it, "assistant")
                            }
                        }
                    }
                    if (output.approvals.isNotEmpty()) {
                        handleApprovals(service, output.approvals)
                    }
                    if (output.error != null) {
                        error = output.error
                        break
                    }
                    cursor = output.next
                    if (output.done) {
                        break
                    }
                    Thread.sleep(200)
                }
                
                SwingUtilities.invokeLater {
                    lastAssistantOutput = currentAssistantBuffer.toString()
                    val hasPatch = containsPatch(lastAssistantOutput ?: "")
                    applyButton.isEnabled = hasPatch
                    previewButton.isEnabled = hasPatch
                    if (stopRequested) {
                        appendText("\nCancelled\n", "info")
                        setStatus("Cancelled")
                    } else if (error != null) {
                        appendText("\nError: $error", "error")
                        setStatus("Error", isError = true)
                    } else if (!hasOutput) {
                        appendText("\n(no output)\n", "info")
                        setStatus("Done")
                    } else {
                        setStatus("Done")
                    }
                    appendText("\n\n", "normal")
                    setRunningState(false)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    appendText("Error: ${e.message}\n\n", "error")
                    setStatus("Error", isError = true)
                    setRunningState(false)
                }
            }
        }
    }
    
    private fun handleApprovals(service: JimiPluginService, approvals: List<ApprovalInfo>) {
        for (approval in approvals) {
            val choice = SwingUtilities.invokeAndGet {
                val message = "Action: ${approval.action}\n\n${approval.description}"
                val options = arrayOf("Approve", "Approve Session", "Reject")
                Messages.showDialog(
                    project,
                    message,
                    "Jimi Approval",
                    options,
                    0,
                    Messages.getQuestionIcon()
                )
            }
            val action = when (choice) {
                0 -> "approve"
                1 -> "approve_session"
                else -> "reject"
            }
            service.handleApproval(approval.toolCallId, action)
        }
    }
    
    private fun requestStop() {
        stopRequested = true
        currentJobId?.let { jobId ->
            JimiPluginService.getInstance(project).cancelJob(jobId)
        }
    }
    
    private fun setRunningState(running: Boolean) {
        sendButton.isEnabled = !running
        inputField.isEnabled = !running
        stopButton.isEnabled = running
        val hasPatch = containsPatch(lastAssistantOutput ?: "")
        applyButton.isEnabled = !running && hasPatch
        previewButton.isEnabled = !running && hasPatch
        if (running) {
            stopRequested = false
        } else {
            currentJobId = null
            stopRequested = false
            inputField.requestFocusInWindow()
        }
    }
    
    private fun appendText(text: String, styleName: String) {
        try {
            val style = styles[styleName] ?: styles["normal"]!!
            styledDoc.insertString(styledDoc.length, text, style)
            outputPane.caretPosition = styledDoc.length
        } catch (_: BadLocationException) {}
    }
    
    private fun buildContextPrefix(): String {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return ""
        val parts = ArrayList<String>()
        
        if (includeSelectionCheck.isSelected) {
            val selectedText = editor.selectionModel.selectedText?.trim()
            if (!selectedText.isNullOrBlank()) {
                parts.add("[Selection]\n```\n${selectedText}\n```")
            }
        }
        
        if (includeFileCheck.isSelected) {
            val document = editor.document
            val file = FileDocumentManager.getInstance().getFile(document)
            val filePath = file?.path ?: "unknown"
            val content = document.text
            val limited = limitText(content, 6000)
            parts.add("[Current File] $filePath\n```\n$limited\n```")
        }
        
        if (includeProblemsCheck.isSelected) {
            val problems = collectEditorProblems(editor)
            if (problems.isNotEmpty()) {
                val body = problems.joinToString("\n")
                parts.add("[Problems]\n$body")
            }
        }
        
        return parts.joinToString("\n\n")
    }
    
    private fun collectEditorProblems(editor: com.intellij.openapi.editor.Editor): List<String> {
        val results = ArrayList<String>()
        val highlighters = editor.markupModel.allHighlighters
        for (highlighter in highlighters) {
            val tooltip = highlighter.errorStripeTooltip ?: continue
            val text = tooltip.toString().trim()
            if (text.isBlank()) continue
            val line = editor.document.getLineNumber(highlighter.startOffset) + 1
            results.add("Line $line: $text")
            if (results.size >= 20) break
        }
        return results
    }
    
    private fun limitText(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        return text.substring(0, maxChars) + "\n... (truncated)"
    }
    
    private fun applyLastPatch() {
        // 从最新助手输出提取并应用补丁
        val patches = readPatchesFromOutput() ?: return
        if (patches.isEmpty()) return
        
        val failures = ArrayList<String>()
        val baseDir = project.baseDir ?: return
        
        WriteCommandAction.runWriteCommandAction(project) {
            for (patch in patches) {
                val targetPath = when {
                    patch.isDeletedFile -> patch.beforeFileName
                    patch.isNewFile -> patch.afterFileName
                    else -> patch.afterFileName ?: patch.beforeFileName
                } ?: continue
                
                val targetFile = baseDir.findFileByRelativePath(targetPath)
                val originalText = targetFile?.contentsToByteArray()?.toString(StandardCharsets.UTF_8) ?: ""
                
                val applier = ApplyTextFilePatch(patch, baseDir.toNioPath().toFile())
                val status = applier.execute(originalText)
                
                if (status.type != ApplyPatchStatus.SUCCESS) {
                    failures.add("$targetPath: ${status.type}")
                    continue
                }
                
                val newText = status.newText
                if (patch.isDeletedFile) {
                    targetFile?.delete(this)
                    continue
                }
                
                val file = targetFile ?: createFile(baseDir, targetPath)
                if (file == null) {
                    failures.add("$targetPath: cannot create file")
                    continue
                }
                file.setBinaryContent(newText.toByteArray(StandardCharsets.UTF_8))
            }
        }
        
        if (failures.isNotEmpty()) {
            Messages.showWarningDialog(project, "Some patches failed:\n${failures.joinToString("\n")}", "Apply Patch")
        } else {
            Messages.showInfoMessage(project, "Patch applied successfully.", "Apply Patch")
        }
    }
    
    private fun previewLastPatch() {
        // 生成补丁后的结果并用 Diff Viewer 预览
        val patches = readPatchesFromOutput() ?: return
        if (patches.isEmpty()) return
        val baseDir = project.baseDir ?: return
        
        val requests = ArrayList<SimpleDiffRequest>()
        val failures = ArrayList<String>()
        val contentFactory = DiffContentFactory.getInstance()
        
        for (patch in patches) {
            val targetPath = when {
                patch.isDeletedFile -> patch.beforeFileName
                patch.isNewFile -> patch.afterFileName
                else -> patch.afterFileName ?: patch.beforeFileName
            } ?: continue
            
            val targetFile = baseDir.findFileByRelativePath(targetPath)
            val originalText = targetFile?.contentsToByteArray()?.toString(StandardCharsets.UTF_8) ?: ""
            
            val applier = ApplyTextFilePatch(patch, baseDir.toNioPath().toFile())
            val status = applier.execute(originalText)
            if (status.type != ApplyPatchStatus.SUCCESS) {
                failures.add("$targetPath: ${status.type}")
                continue
            }
            
            val fileType = targetFile?.fileType ?: PlainTextFileType.INSTANCE
            val beforeContent = if (targetFile != null) {
                contentFactory.create(project, targetFile)
            } else {
                contentFactory.create(project, "")
            }
            val afterText = status.newText
            val afterContent = contentFactory.create(project, afterText, fileType)
            
            val title = "Patch Preview: $targetPath"
            requests.add(SimpleDiffRequest(title, beforeContent, afterContent, "Before", "After"))
        }
        
        if (requests.isEmpty()) {
            Messages.showWarningDialog(project, "No previewable patches.\n${failures.joinToString("\n")}", "Preview Diff")
            return
        }
        
        val chain = SimpleDiffRequestChain(requests)
        DiffManager.getInstance().showDiff(project, chain)
        
        if (failures.isNotEmpty()) {
            Messages.showWarningDialog(project, "Some patches failed:\n${failures.joinToString("\n")}", "Preview Diff")
        }
    }
    
    private fun readPatchesFromOutput(): List<TextFilePatch>? {
        // 统一解析助手输出，避免预览/应用逻辑分叉
        val output = lastAssistantOutput ?: ""
        val patchText = extractPatchText(output)
        if (patchText.isBlank()) {
            Messages.showInfoMessage(project, "No patch found in last response.", "Patch")
            return null
        }
        
        val reader = try {
            PatchReader(patchText)
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Patch parse error: ${e.message}", "Patch")
            return null
        }
        
        val patches = try {
            reader.readTextPatches()
        } catch (e: PatchSyntaxException) {
            Messages.showErrorDialog(project, "Patch syntax error: ${e.message}", "Patch")
            return null
        }
        
        if (patches.isEmpty()) {
            Messages.showInfoMessage(project, "No applicable patches found.", "Patch")
            return null
        }
        return patches
    }
    
    private fun createFile(baseDir: com.intellij.openapi.vfs.VirtualFile, relativePath: String)
        : com.intellij.openapi.vfs.VirtualFile? {
        val parts = relativePath.split("/")
        var dir = baseDir
        for (i in 0 until parts.size - 1) {
            val part = parts[i]
            dir = dir.findChild(part) ?: dir.createChildDirectory(this, part)
        }
        val name = parts.last()
        return dir.findChild(name) ?: dir.createChildData(this, name)
    }
    
    private fun containsPatch(text: String): Boolean {
        return extractPatchText(text).isNotBlank()
    }
    
    private fun extractPatchText(text: String): String {
        // 优先解析 ```diff / ```patch 代码块
        val lines = text.lines()
        val blocks = ArrayList<String>()
        var inDiffFence = false
        val buffer = StringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (!inDiffFence && (trimmed == "```diff" || trimmed == "```patch")) {
                    inDiffFence = true
                    buffer.clear()
                    continue
                }
                if (inDiffFence) {
                    blocks.add(buffer.toString())
                    inDiffFence = false
                    buffer.clear()
                }
                continue
            }
            if (inDiffFence) {
                buffer.append(line).append("\n")
            }
        }
        if (blocks.isNotEmpty()) {
            return blocks.joinToString("\n")
        }
        
        // 兜底：从 diff --git 或 --- 开始截取
        val idx = lines.indexOfFirst { it.startsWith("diff --git") || it.startsWith("--- ") }
        if (idx >= 0) {
            return lines.subList(idx, lines.size).joinToString("\n")
        }
        return ""
    }
}

private fun <T> SwingUtilities.invokeAndGet(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) {
        return block()
    }
    val result = arrayOfNulls<Any>(1)
    SwingUtilities.invokeAndWait {
        result[0] = block()
    }
    @Suppress("UNCHECKED_CAST")
    return result[0] as T
}

package io.leavesfly.jimi.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.leavesfly.jimi.plugin.mcp.McpJsonRpcClient
import java.io.File
import java.io.ProcessBuilder
import java.util.concurrent.TimeUnit

/**
 * Jimi 插件服务 - Less is more
 * 通过 MCP (JSON-RPC over StdIO) 与 Jimi 进程通信
 */
@Service(Service.Level.PROJECT)
class JimiPluginService(private val project: Project) : Disposable {
    
    companion object {
        fun getInstance(project: Project): JimiPluginService {
            return project.getService(JimiPluginService::class.java)
        }
    }
    
    private val projectPath: String get() = project.basePath ?: "."
    private val mapper = ObjectMapper()
    
    private var process: Process? = null
    private var mcpClient: McpJsonRpcClient? = null
    private var sessionId: String? = null
    
    /**
     * 启动 Jimi 服务
     */
    @Synchronized
    fun start(): Boolean {
        if (process?.isAlive == true) return true
        
        return try {
            val jimiJar = findJimiJar() ?: run {
                println("[Jimi] ERROR: JAR not found")
                return false
            }
            
            val pb = ProcessBuilder(
                "java", 
                "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
                "-jar", jimiJar.absolutePath, 
                "--mcp-server"
            ).directory(File(projectPath))
            
            // 设置 JAVA_HOME
            findJavaHome()?.let { pb.environment()["JAVA_HOME"] = it }
            
            process = pb.start()
            mcpClient = McpJsonRpcClient(process!!)
            
            Thread.sleep(2000) // 等待进程启动
            mcpClient!!.initialize()
            if (!ensureSession()) {
                println("[Jimi] ERROR: Failed to create session")
                cleanup()
                return false
            }
            
            println("[Jimi] Service started")
            true
            
        } catch (e: Exception) {
            println("[Jimi] ERROR: ${e.message}")
            cleanup()
            false
        }
    }
    
    /**
     * 执行任务（MCP调用）
     */
    fun executeTask(input: String, onChunk: (String) -> Unit): String? {
        if (!start()) {
            onChunk("Error: Failed to start Jimi service")
            return "Error: Failed to start Jimi service"
        }
        
        return try {
            val jobId = startStream(input) ?: return "Error: Failed to start streaming task"
            var cursor = 0
            while (true) {
                val output = pollOutput(jobId, cursor) ?: return "Error: Failed to poll output"
                if (output.chunks.isNotEmpty()) {
                    output.chunks.forEach { onChunk(it) }
                }
                if (output.error != null) {
                    return output.error
                }
                cursor = output.next
                if (output.done) {
                    return null
                }
                Thread.sleep(200)
            }
        } catch (e: Exception) {
            onChunk("Error: ${e.message}")
            "Error: ${e.message}"
        }
    }
    
    /**
     * 执行任务（同步，简单版）
     */
    fun executeTask(input: String): String {
        val result = StringBuilder()
        val error = executeTask(input) { result.append(it) }
        return if (error != null) "Error: $error" else result.toString()
    }
    
    /**
     * 查找 Jimi JAR
     */
    private fun findJimiJar(): File? {
        listOf(
            File(projectPath, "../Jimi/target/jimi-0.1.0.jar"),
            File(System.getProperty("user.home"), ".jimi/jimi-0.1.0.jar")
        ).forEach { if (it.exists()) return it }
        return null
    }
    
    /**
     * 查找 Java 17+
     */
    private fun findJavaHome(): String? {
        System.getenv("JAVA_HOME")?.takeIf { File(it).exists() }?.let { return it }
        
        try {
            val proc = ProcessBuilder("/usr/libexec/java_home", "-v", "17").start()
            val result = proc.inputStream.bufferedReader().readText().trim()
            if (proc.waitFor() == 0 && result.isNotEmpty()) return result
        } catch (_: Exception) {}
        
        return null
    }
    
    private fun cleanup() {
        try {
            mcpClient?.close()
            process?.let {
                it.destroy()
                if (!it.waitFor(3, TimeUnit.SECONDS)) {
                    it.destroyForcibly()
                    println("[Jimi] Process force killed")
                }
            }
        } catch (_: Exception) {}
        process = null
        mcpClient = null
        sessionId = null
    }
    
    override fun dispose() {
        println("[Jimi] Disposing service, killing process...")
        cleanup()
    }

    private fun ensureSession(): Boolean {
        if (sessionId != null) return true
        return try {
            val result = mcpClient!!.callTool(
                "jimi_session",
                mapOf(
                    "action" to "create",
                    "workDir" to projectPath
                )
            )
            if (result.isError) {
                return false
            }
            sessionId = parseSessionId(result.text)
            sessionId != null
        } catch (_: Exception) {
            false
        }
    }

    private fun parseSessionId(text: String): String? {
        val key = "sessionId="
        val idx = text.indexOf(key)
        if (idx >= 0) {
            return text.substring(idx + key.length).trim().ifBlank { null }
        }
        return null
    }
    
    fun startStream(input: String): String? {
        if (!start()) {
            return null
        }
        if (!ensureSession()) {
            return null
        }
        val result = mcpClient!!.callTool(
            "jimi_execute_stream",
            mapOf(
                "input" to input,
                "workDir" to projectPath,
                "sessionId" to sessionId
            )
        )
        if (result.isError) {
            return null
        }
        return parseJobId(result.text)
    }
    
    fun pollOutput(jobId: String, since: Int): StreamOutput? {
        if (!start()) {
            return StreamOutput(emptyList(), since, done = true, error = "Service not started", approvals = emptyList())
        }
        val result = mcpClient!!.callTool(
            "jimi_get_output",
            mapOf(
                "jobId" to jobId,
                "since" to since
            )
        )
        if (result.isError) {
            return StreamOutput(emptyList(), since, done = true, error = result.text, approvals = emptyList())
        }
        return parseStreamOutput(result.text)
    }
    
    fun handleApproval(toolCallId: String, action: String): Boolean {
        if (!start()) {
            return false
        }
        val result = mcpClient!!.callTool(
            "jimi_approval",
            mapOf(
                "action" to action,
                "toolCallId" to toolCallId,
                "sessionId" to sessionId
            )
        )
        return !result.isError
    }
    
    fun cancelJob(jobId: String): Boolean {
        if (!start()) {
            return false
        }
        val result = mcpClient!!.callTool(
            "jimi_cancel",
            mapOf("jobId" to jobId)
        )
        return !result.isError
    }
    
    private fun parseJobId(text: String): String? {
        val key = "jobId="
        val idx = text.indexOf(key)
        if (idx >= 0) {
            return text.substring(idx + key.length).trim().ifBlank { null }
        }
        return null
    }
    
    private fun parseStreamOutput(text: String): StreamOutput? {
        if (text.isBlank()) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val payload = mapper.readValue(text, Map::class.java) as Map<String, Any?>
            val chunks = (payload["chunks"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val next = (payload["next"] as? Number)?.toInt() ?: 0
            val done = payload["done"] as? Boolean ?: false
            val error = payload["error"] as? String
            val approvals = parseApprovals(payload["approvals"])
            StreamOutput(chunks, next, done, error, approvals)
        } catch (_: Exception) {
            null
        }
    }
    
    private fun parseApprovals(raw: Any?): List<ApprovalInfo> {
        val list = raw as? List<*> ?: return emptyList()
        val approvals = ArrayList<ApprovalInfo>()
        for (item in list) {
            val map = item as? Map<*, *> ?: continue
            val toolCallId = map["toolCallId"] as? String ?: continue
            val action = map["action"] as? String ?: ""
            val description = map["description"] as? String ?: ""
            approvals.add(ApprovalInfo(toolCallId, action, description))
        }
        return approvals
    }
}

data class StreamOutput(
    val chunks: List<String>,
    val next: Int,
    val done: Boolean,
    val error: String?,
    val approvals: List<ApprovalInfo>
)

data class ApprovalInfo(
    val toolCallId: String,
    val action: String,
    val description: String
)

package io.leavesfly.jimi.plugin.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class McpJsonRpcClient(private val process: Process) : AutoCloseable {

    private val objectMapper = ObjectMapper()
    private val writer = BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8))
    private val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
    private val requestIdCounter = AtomicInteger(1)
    private val responseCache = ConcurrentHashMap<Any, Map<String, Any?>>()
    @Volatile private var closed = false

    private val readerThread = Thread(this::readLoop, "MCP-Reader").apply {
        isDaemon = true
        start()
    }

    fun initialize() {
        val params = mapOf(
            "protocolVersion" to "2024-11-05",
            "capabilities" to emptyMap<String, Any>(),
            "clientInfo" to mapOf("name" to "jimi-intellij-plugin", "version" to "0.1.0")
        )
        val response = sendRequest("initialize", params)
        val error = response["error"] as? Map<*, *>
        if (error != null) {
            throw RuntimeException("Initialize failed: ${error["message"]}")
        }
    }

    fun callTool(toolName: String, arguments: Map<String, Any?>): CallToolResult {
        val params = mapOf(
            "name" to toolName,
            "arguments" to arguments
        )
        val response = sendRequest("tools/call", params)
        val error = response["error"] as? Map<*, *>
        if (error != null) {
            return CallToolResult(
                isError = true,
                text = "Call tool failed: ${error["message"]}"
            )
        }

        val result = response["result"] as? Map<*, *> ?: return CallToolResult(
            isError = true,
            text = "Call tool failed: invalid result"
        )
        val isError = result["isError"] as? Boolean ?: false
        val text = extractTextContent(result["content"])
        return CallToolResult(isError = isError, text = text)
    }

    private fun extractTextContent(content: Any?): String {
        val list = content as? List<*> ?: return ""
        val builder = StringBuilder()
        for (item in list) {
            val map = item as? Map<*, *> ?: continue
            val type = map["type"] as? String ?: continue
            if (type == "text") {
                val text = map["text"] as? String ?: ""
                builder.append(text)
            }
        }
        return builder.toString()
    }

    private fun sendRequest(method: String, params: Map<String, Any?>): Map<String, Any?> {
        val requestId = requestIdCounter.getAndIncrement()
        val request = mapOf(
            "jsonrpc" to "2.0",
            "id" to requestId,
            "method" to method,
            "params" to params
        )
        val requestJson = objectMapper.writeValueAsString(request)
        synchronized(this) {
            writer.write(requestJson)
            writer.write("\n")
            writer.flush()
        }
        val startTime = System.currentTimeMillis()
        while (!responseCache.containsKey(requestId)) {
            if (closed) {
                throw RuntimeException("Client closed")
            }
            if (System.currentTimeMillis() - startTime > 30000) {
                throw RuntimeException("Request timeout")
            }
            Thread.sleep(50)
        }
        return responseCache.remove(requestId) ?: emptyMap()
    }

    private fun readLoop() {
        try {
            var line: String?
            while (!closed && reader.readLine().also { line = it } != null) {
                val content = line?.trim().orEmpty()
                if (content.isEmpty()) continue
                try {
                    @Suppress("UNCHECKED_CAST")
                    val response = objectMapper.readValue(content, Map::class.java) as Map<String, Any?>
                    val id = response["id"]
                    if (id != null) {
                        responseCache[id] = response
                    }
                } catch (_: Exception) {
                    // Ignore malformed lines
                }
            }
        } catch (_: Exception) {
            if (!closed) {
                // Ignore read errors after close
            }
        }
    }

    override fun close() {
        closed = true
        try {
            writer.close()
        } catch (_: Exception) {}
        try {
            reader.close()
        } catch (_: Exception) {}
        try {
            process.destroy()
        } catch (_: Exception) {}
    }
}

data class CallToolResult(
    val isError: Boolean,
    val text: String
)

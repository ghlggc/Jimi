package io.leavesfly.jimi.llm.provider;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Cursor CLI 进程执行器
 * 负责执行 cursor-agent 命令并处理流式输出
 */
@Slf4j
public class CursorProcessExecutor {

    /**
     * 执行 cursor-agent 命令
     *
     * @param command 命令列表（已包含 prompt 参数）
     * @param prompt 已废弃，保留以保持接口兼容，prompt 应在 command 中传递
     * @param workingDir 工作目录
     * @param onStdout 标准输出回调
     * @param timeout 超时时间(毫秒)
     * @return 执行结果
     */
    public ExecutionResult execute(
            List<String> command,
            String prompt,  // 已废弃
            String workingDir,
            Consumer<String> onStdout,
            long timeout
    ) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        
        if (workingDir != null && !workingDir.isEmpty()) {
            pb.directory(new File(workingDir));
        }
        
        pb.redirectErrorStream(false);
        
        log.debug("Executing cursor command: {}", String.join(" ", command));
        
        Process process = pb.start();
        
        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();
        
        // 异步读取标准输出
        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdoutBuilder.append(line).append("\n");
                    if (onStdout != null) {
                        onStdout.accept(line);
                    }
                }
            } catch (Exception e) {
                log.error("Error reading stdout", e);
            }
        });
        
        // 异步读取标准错误
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderrBuilder.append(line).append("\n");
                    log.debug("Cursor stderr: {}", line);
                }
            } catch (Exception e) {
                log.error("Error reading stderr", e);
            }
        });
        
        // 先启动读取线程
        stdoutThread.start();
        stderrThread.start();
        
        // 注意：cursor-agent 不需要通过 stdin 传递 prompt
        // prompt 已经作为命令行参数传递，这里直接关闭 stdin 防止进程等待输入
        try {
            process.getOutputStream().close();
            log.debug("Closed stdin (cursor-agent uses command-line args for prompt)");
        } catch (Exception e) {
            log.debug("Failed to close stdin", e);
        }
        
        // 等待进程完成
        boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Cursor process timeout after " + timeout + "ms");
        }
        
        // 等待输出线程完成
        stdoutThread.join(5000);
        stderrThread.join(5000);
        
        int exitCode = process.exitValue();
        
        return ExecutionResult.builder()
                .exitCode(exitCode)
                .stdout(stdoutBuilder.toString())
                .stderr(stderrBuilder.toString())
                .build();
    }
    
    /**
     * 检查 cursor-agent CLI 是否已安装
     */
    public static boolean isCliInstalled(String cliCommand) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cliCommand, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 执行结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ExecutionResult {
        private int exitCode;
        private String stdout;
        private String stderr;
    }
}

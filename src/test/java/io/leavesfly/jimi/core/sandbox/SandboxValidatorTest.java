package io.leavesfly.jimi.core.sandbox;

import io.leavesfly.jimi.config.info.SandboxConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SandboxValidator 单元测试
 */
class SandboxValidatorTest {
    
    @TempDir
    Path tempDir;
    
    private SandboxValidator validator;
    private SandboxConfig config;
    
    @BeforeEach
    void setUp() {
        // 创建默认配置
        config = SandboxConfig.builder()
                .enabled(true)
                .mode(SandboxConfig.SandboxMode.RESTRICTED)
                .filesystem(SandboxConfig.FilesystemConfig.builder()
                        .allowWriteOutsideWorkspace(false)
                        .deniedPaths(List.of("/etc/**", "/usr/**", "/System/**", "**/.ssh/**"))
                        .maxFileSize(1024 * 1024) // 1MB
                        .build())
                .shell(SandboxConfig.ShellConfig.builder()
                        .allowDangerousCommands(false)
                        .dangerousPatterns(List.of("rm -rf", ":(){ :|:& };:", "dd if=", "mkfs"))
                        .build())
                .network(SandboxConfig.NetworkConfig.builder()
                        .allowExternalAccess(true)
                        .deniedDomains(List.of("169.254.169.254", "metadata.google.internal"))
                        .build())
                .build();
        
        validator = new SandboxValidator(config, tempDir);
    }
    
    // ==================== 文件路径验证测试 ====================
    
    @Test
    void testFilePathInWorkspace_Allowed() {
        Path filePath = tempDir.resolve("test.txt");
        SandboxValidator.ValidationResult result = 
                validator.validateFilePath(filePath, SandboxValidator.FileOperation.WRITE);
        
        assertTrue(result.isAllowed());
        assertFalse(result.isRequiresApproval());
    }
    
    @Test
    void testFilePathOutsideWorkspace_RequiresApproval() {
        Path filePath = Path.of("/tmp/test.txt");
        SandboxValidator.ValidationResult result = 
                validator.validateFilePath(filePath, SandboxValidator.FileOperation.WRITE);
        
        assertFalse(result.isAllowed());
        assertTrue(result.isRequiresApproval());
        assertEquals(SandboxValidator.SandboxViolationType.OUTSIDE_WORKSPACE, result.getViolationType());
    }
    
    @Test
    void testFilePathInDeniedList_Denied() {
        Path filePath = Path.of("/etc/passwd");
        SandboxValidator.ValidationResult result = 
                validator.validateFilePath(filePath, SandboxValidator.FileOperation.WRITE);
        
        assertFalse(result.isAllowed());
        assertFalse(result.isRequiresApproval());
        assertEquals(SandboxValidator.SandboxViolationType.DENIED_PATH, result.getViolationType());
    }
    
    @Test
    void testFilePathReadOperation_Allowed() {
        // 读操作不受工作区限制
        Path filePath = Path.of("/tmp/test.txt");
        SandboxValidator.ValidationResult result = 
                validator.validateFilePath(filePath, SandboxValidator.FileOperation.READ);
        
        assertTrue(result.isAllowed());
    }
    
    @Test
    void testFilePathWithAllowedWritePath() {
        // 添加允许路径
        config.getFilesystem().getAllowedWritePaths().add("/tmp/**");
        
        Path filePath = Path.of("/tmp/test.txt");
        SandboxValidator.ValidationResult result = 
                validator.validateFilePath(filePath, SandboxValidator.FileOperation.WRITE);
        
        assertTrue(result.isAllowed());
    }
    
    @Test
    void testFileSizeExceedsLimit_Denied() {
        long largeSize = 2 * 1024 * 1024; // 2MB (超过1MB限制)
        SandboxValidator.ValidationResult result = validator.validateFileSize(largeSize);
        
        assertFalse(result.isAllowed());
        assertEquals(SandboxValidator.SandboxViolationType.FILE_SIZE_EXCEEDED, result.getViolationType());
    }
    
    // ==================== Shell 命令验证测试 ====================
    
    @Test
    void testSafeCommand_Allowed() {
        String command = "ls -la";
        SandboxValidator.ValidationResult result = validator.validateShellCommand(command);
        
        assertTrue(result.isAllowed());
    }
    
    @Test
    void testDangerousCommand_Denied() {
        String command = "rm -rf /";
        SandboxValidator.ValidationResult result = validator.validateShellCommand(command);
        
        assertFalse(result.isAllowed());
        assertFalse(result.isRequiresApproval());
        assertEquals(SandboxValidator.SandboxViolationType.DANGEROUS_COMMAND, result.getViolationType());
    }
    
    @Test
    void testDangerousRedirect_Denied() {
        String command = "echo 'test' > /dev/sda";
        SandboxValidator.ValidationResult result = validator.validateShellCommand(command);
        
        assertFalse(result.isAllowed());
        assertEquals(SandboxValidator.SandboxViolationType.DANGEROUS_REDIRECT, result.getViolationType());
    }
    
    @Test
    void testCommandWithWhitelist_RequiresApproval() {
        // 设置白名单
        config.getShell().getAllowedCommands().addAll(List.of("ls", "cat", "grep"));
        
        // 不在白名单的命令
        String command = "curl https://example.com";
        SandboxValidator.ValidationResult result = validator.validateShellCommand(command);
        
        assertFalse(result.isAllowed());
        assertTrue(result.isRequiresApproval());
        assertEquals(SandboxValidator.SandboxViolationType.NOT_IN_WHITELIST, result.getViolationType());
    }
    
    @Test
    void testCommandInWhitelist_Allowed() {
        // 设置白名单
        config.getShell().getAllowedCommands().addAll(List.of("ls", "cat", "grep"));
        
        String command = "ls -la";
        SandboxValidator.ValidationResult result = validator.validateShellCommand(command);
        
        assertTrue(result.isAllowed());
    }
    
    @Test
    void testCommandWithEnvironmentVariable() {
        String command = "ENV_VAR=value ls -la";
        SandboxValidator.ValidationResult result = validator.validateShellCommand(command);
        
        assertTrue(result.isAllowed());
    }
    
    // ==================== 网络访问验证测试 ====================
    
    @Test
    void testNormalURL_Allowed() {
        String url = "https://www.google.com";
        SandboxValidator.ValidationResult result = validator.validateNetworkAccess(url);
        
        assertTrue(result.isAllowed());
    }
    
    @Test
    void testDeniedDomain_Denied() {
        String url = "http://169.254.169.254/latest/meta-data/";
        SandboxValidator.ValidationResult result = validator.validateNetworkAccess(url);
        
        assertFalse(result.isAllowed());
        assertEquals(SandboxValidator.SandboxViolationType.DENIED_DOMAIN, result.getViolationType());
    }
    
    @Test
    void testNetworkAccessDisabled_RequiresApproval() {
        config.getNetwork().setAllowExternalAccess(false);
        
        String url = "https://www.google.com";
        SandboxValidator.ValidationResult result = validator.validateNetworkAccess(url);
        
        assertFalse(result.isAllowed());
        assertTrue(result.isRequiresApproval());
        assertEquals(SandboxValidator.SandboxViolationType.NETWORK_ACCESS, result.getViolationType());
    }
    
    // ==================== 沙箱禁用测试 ====================
    
    @Test
    void testSandboxDisabled_AllowsEverything() {
        config.setEnabled(false);
        
        // 危险命令应该被允许
        String dangerousCommand = "rm -rf /";
        SandboxValidator.ValidationResult commandResult = validator.validateShellCommand(dangerousCommand);
        assertTrue(commandResult.isAllowed());
        
        // 工作区外路径应该被允许
        Path outsidePath = Path.of("/etc/passwd");
        SandboxValidator.ValidationResult pathResult = 
                validator.validateFilePath(outsidePath, SandboxValidator.FileOperation.WRITE);
        assertTrue(pathResult.isAllowed());
        
        // 被拒绝的域名应该被允许
        String deniedURL = "http://169.254.169.254/";
        SandboxValidator.ValidationResult urlResult = validator.validateNetworkAccess(deniedURL);
        assertTrue(urlResult.isAllowed());
    }
}

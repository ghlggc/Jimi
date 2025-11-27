---
name: security-checklist
description: OWASP 安全检查清单
version: 1.0.0
category: security
triggers:
  - security
  - 安全检查
  - OWASP
  - 安全审计
  - security audit
scriptPath: security-scan.sh
scriptType: bash
autoExecute: true
scriptTimeout: 30
scriptEnv:
  SCAN_TARGET: "src/"
---

# 安全检查清单技能包

基于 OWASP Top 10，全面保障应用安全。

## OWASP Top 10 (2021)

### A01:2021 – 访问控制失效

**风险**：未经授权的用户可以访问敏感功能或数据

**防护措施**：
- ✓ 默认拒绝访问，明确授权
- ✓ 实现基于角色的访问控制（RBAC）
- ✓ 禁用目录列表
- ✓ 记录访问控制失败并告警
- ✓ 限制 API 访问速率

**代码示例**：
```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/users")
public List<User> getUsers() {
    return userService.findAll();
}
```

### A02:2021 – 加密机制失效

**风险**：敏感数据未加密或使用弱加密

**防护措施**：
- ✓ 使用 HTTPS 传输所有敏感数据
- ✓ 静态数据加密（数据库、文件）
- ✓ 禁用弱加密算法（MD5、SHA1）
- ✓ 使用强密码哈希（bcrypt、Argon2）
- ✓ 密钥管理（定期轮换、安全存储）

**代码示例**：
```java
// 使用 BCrypt 加密密码
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
String hashedPassword = encoder.encode(rawPassword);
```

### A03:2021 – 注入

**风险**：SQL 注入、命令注入、LDAP 注入等

**防护措施**：

#### SQL 注入防护
```java
// ❌ 不安全
String query = "SELECT * FROM users WHERE username = '" + username + "'";

// ✓ 安全：使用参数化查询
String query = "SELECT * FROM users WHERE username = ?";
PreparedStatement stmt = conn.prepareStatement(query);
stmt.setString(1, username);
```

#### NoSQL 注入防护
```java
// ✓ 使用参数绑定
Query query = new Query(Criteria.where("username").is(username));
```

#### 命令注入防护
```java
// ❌ 不安全
Runtime.getRuntime().exec("ls " + userInput);

// ✓ 安全：验证和转义
if (userInput.matches("[a-zA-Z0-9]+")) {
    ProcessBuilder pb = new ProcessBuilder("ls", userInput);
    pb.start();
}
```

### A04:2021 – 不安全设计

**风险**：架构和设计层面的安全缺陷

**防护措施**：
- ✓ 威胁建模
- ✓ 安全设计模式
- ✓ 最小权限原则
- ✓ 纵深防御
- ✓ 安全开发生命周期（SDLC）

### A05:2021 – 安全配置错误

**风险**：默认配置、未更新软件、不必要的功能

**防护措施**：
- ✓ 最小化安装（移除不必要的功能）
- ✓ 禁用默认账户和密码
- ✓ 错误信息不暴露敏感信息
- ✓ 定期更新和打补丁
- ✓ 安全配置审查

**检查清单**：
```yaml
# application.yml
server:
  error:
    include-stacktrace: never  # 生产环境不暴露堆栈
spring:
  devtools:
    restart:
      enabled: false  # 生产环境禁用 devtools
```

### A06:2021 – 易受攻击和过时的组件

**风险**：使用有已知漏洞的库和框架

**防护措施**：
- ✓ 移除未使用的依赖
- ✓ 持续监控依赖漏洞
- ✓ 从官方源获取组件
- ✓ 使用签名验证组件完整性

**工具**：
```bash
# Maven 依赖检查
mvn dependency:tree
mvn versions:display-dependency-updates

# OWASP Dependency Check
mvn org.owasp:dependency-check-maven:check
```

### A07:2021 – 身份识别和身份验证失败

**风险**：弱密码、会话管理不当、凭据暴露

**防护措施**：

#### 密码策略
- ✓ 最小长度 8 位
- ✓ 复杂度要求（大小写、数字、特殊字符）
- ✓ 检查弱密码和常用密码
- ✓ 限制登录失败次数
- ✓ 多因素认证（MFA）

#### 会话管理
```java
// Session 配置
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) {
    http.sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .maximumSessions(1)
        .maxSessionsPreventsLogin(true);
    return http.build();
}
```

### A08:2021 – 软件和数据完整性失败

**风险**：不安全的 CI/CD、自动更新、反序列化

**防护措施**：
- ✓ 代码签名
- ✓ CI/CD 安全加固
- ✓ 依赖完整性验证
- ✓ 避免不安全的反序列化

```java
// ❌ 不安全的反序列化
ObjectInputStream ois = new ObjectInputStream(inputStream);
Object obj = ois.readObject();

// ✓ 使用 JSON 等安全格式
ObjectMapper mapper = new ObjectMapper();
User user = mapper.readValue(json, User.class);
```

### A09:2021 – 安全日志和监控失败

**风险**：攻击无法被检测和响应

**防护措施**：
- ✓ 记录所有登录、访问控制失败
- ✓ 高价值交易审计日志
- ✓ 日志格式规范化
- ✓ 集中式日志管理
- ✓ 实时告警

**日志示例**：
```java
log.warn("Failed login attempt: user={}, ip={}", 
         username, request.getRemoteAddr());
log.info("Password changed: user={}, timestamp={}", 
         username, System.currentTimeMillis());
```

### A10:2021 – 服务器端请求伪造（SSRF）

**风险**：攻击者控制服务器发起的请求

**防护措施**：
- ✓ 禁止用户控制 URL
- ✓ URL 白名单
- ✓ 禁用 HTTP 重定向
- ✓ 网络层隔离

```java
// ✓ URL 验证
private boolean isAllowedUrl(String url) {
    try {
        URL u = new URL(url);
        String host = u.getHost();
        return ALLOWED_HOSTS.contains(host);
    } catch (MalformedURLException e) {
        return false;
    }
}
```

## 额外安全措施

### XSS 防护

```java
// 输出转义
String safe = HtmlUtils.htmlEscape(userInput);

// Content Security Policy
response.setHeader("Content-Security-Policy", 
    "default-src 'self'; script-src 'self' 'unsafe-inline'");
```

### CSRF 防护

```java
// Spring Security 自动启用 CSRF
http.csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
```

### 点击劫持防护

```java
// X-Frame-Options
response.setHeader("X-Frame-Options", "DENY");
```

### 安全响应头

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'
```

## 敏感信息检测

**不应硬编码的信息**：
- ❌ 数据库密码
- ❌ API 密钥
- ❌ 加密密钥
- ❌ 私钥证书
- ❌ OAuth 密钥

**正确做法**：
```yaml
# application.yml
spring:
  datasource:
    password: ${DB_PASSWORD}  # 从环境变量读取
```

## 安全开发流程

### 1. 需求阶段
- 识别安全需求
- 威胁建模

### 2. 设计阶段
- 安全架构设计
- 安全设计评审

### 3. 编码阶段
- 安全编码规范
- 代码审查

### 4. 测试阶段
- 安全测试
- 渗透测试
- 漏洞扫描

### 5. 部署阶段
- 安全配置检查
- 最小权限部署

### 6. 运维阶段
- 安全监控
- 补丁管理
- 应急响应

## 工具推荐

### 静态分析
- **SpotBugs**：Java 代码缺陷检测
- **SonarQube**：代码质量和安全
- **Checkmarx**：商业静态分析工具

### 依赖检查
- **OWASP Dependency-Check**：依赖漏洞扫描
- **Snyk**：开源依赖漏洞检测

### 动态测试
- **OWASP ZAP**：Web 应用安全测试
- **Burp Suite**：渗透测试工具

### 密钥扫描
- **git-secrets**：防止密钥提交
- **TruffleHog**：Git 历史密钥扫描

## 安全检查清单

### 认证与授权
- [ ] 实现强密码策略
- [ ] 使用多因素认证
- [ ] 会话超时设置
- [ ] 安全的密码重置流程
- [ ] 基于角色的访问控制

### 数据保护
- [ ] HTTPS 加密传输
- [ ] 敏感数据加密存储
- [ ] 安全的密钥管理
- [ ] 数据脱敏

### 输入验证
- [ ] 所有输入验证
- [ ] 参数化查询防 SQL 注入
- [ ] 输出编码防 XSS
- [ ] 文件上传验证

### 安全配置
- [ ] 禁用不必要的功能
- [ ] 安全的默认配置
- [ ] 错误消息不暴露敏感信息
- [ ] 定期更新依赖

### 日志监控
- [ ] 记录安全事件
- [ ] 审计关键操作
- [ ] 异常告警
- [ ] 日志保护

## 应急响应

### 发现漏洞后
1. **评估影响**：范围、严重程度
2. **隔离系统**：防止进一步损害
3. **修复漏洞**：紧急补丁
4. **验证修复**：安全测试
5. **总结复盘**：防止再次发生

## 参考资源

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [CWE Top 25](https://cwe.mitre.org/top25/)
- [SANS Top 25](https://www.sans.org/top25-software-errors/)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)

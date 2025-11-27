---
name: api-design
description: RESTful API 设计最佳实践
version: 1.0.0
category: development
triggers:
  - api design
  - RESTful
  - 接口设计
  - API 规范
  - REST API
---

# RESTful API 设计技能包

设计规范、易用、可维护的 RESTful API。

## REST 核心原则

### 1. 资源导向（Resource-Oriented）

API 应该围绕资源而不是动作设计。

**✅ 好的设计**：
```
GET    /users          # 获取用户列表
GET    /users/123      # 获取特定用户
POST   /users          # 创建用户
PUT    /users/123      # 更新用户
DELETE /users/123      # 删除用户
```

**❌ 不好的设计**：
```
GET    /getUsers
POST   /createUser
POST   /updateUser
POST   /deleteUser
```

### 2. 使用标准 HTTP 方法

| 方法 | 用途 | 幂等性 | 安全性 |
|------|------|--------|--------|
| **GET** | 获取资源 | ✓ | ✓ |
| **POST** | 创建资源 | ✗ | ✗ |
| **PUT** | 更新资源（完整） | ✓ | ✗ |
| **PATCH** | 更新资源（部分） | ✗ | ✗ |
| **DELETE** | 删除资源 | ✓ | ✗ |

### 3. 无状态（Stateless）

每个请求应该包含完整的信息，服务器不应依赖会话状态。

## URI 设计规范

### 命名规则

1. **使用名词复数**：`/users` 而不是 `/user`
2. **小写字母 + 连字符**：`/user-profiles` 而不是 `/userProfiles`
3. **避免深层嵌套**：最多 2-3 层
4. **不包含文件扩展名**：`/users` 而不是 `/users.json`

### 资源层次

```
GET /organizations/123/departments/456/employees
```

**建议**：嵌套不超过 3 层，过深的嵌套使用查询参数：
```
GET /employees?departmentId=456&organizationId=123
```

### 子资源

```
GET    /users/123/orders          # 获取用户的订单列表
POST   /users/123/orders          # 为用户创建订单
GET    /users/123/orders/789      # 获取用户的特定订单
```

## HTTP 状态码

### 2xx 成功

| 状态码 | 说明 | 使用场景 |
|-------|------|---------|
| **200 OK** | 请求成功 | GET、PUT、PATCH 成功 |
| **201 Created** | 资源创建成功 | POST 成功 |
| **204 No Content** | 成功但无返回内容 | DELETE 成功 |

### 4xx 客户端错误

| 状态码 | 说明 | 使用场景 |
|-------|------|---------|
| **400 Bad Request** | 请求参数错误 | 参数验证失败 |
| **401 Unauthorized** | 未认证 | 缺少或无效的认证信息 |
| **403 Forbidden** | 无权限 | 认证成功但无访问权限 |
| **404 Not Found** | 资源不存在 | 请求的资源不存在 |
| **409 Conflict** | 资源冲突 | 数据冲突（如重复创建） |
| **422 Unprocessable Entity** | 语义错误 | 请求格式正确但语义错误 |
| **429 Too Many Requests** | 请求过多 | 触发限流 |

### 5xx 服务器错误

| 状态码 | 说明 | 使用场景 |
|-------|------|---------|
| **500 Internal Server Error** | 服务器内部错误 | 未预期的服务器错误 |
| **503 Service Unavailable** | 服务不可用 | 服务维护或过载 |

## 请求与响应格式

### 请求头

```http
Content-Type: application/json
Accept: application/json
Authorization: Bearer <token>
```

### 请求体（POST/PUT）

```json
{
  "name": "张三",
  "email": "zhangsan@example.com",
  "age": 25
}
```

### 成功响应

**单个资源**：
```json
{
  "id": 123,
  "name": "张三",
  "email": "zhangsan@example.com",
  "createdAt": "2025-01-01T10:00:00Z"
}
```

**资源列表**：
```json
{
  "data": [
    {
      "id": 123,
      "name": "张三"
    },
    {
      "id": 124,
      "name": "李四"
    }
  ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "total": 100,
    "totalPages": 5
  }
}
```

### 错误响应

统一的错误格式：

```json
{
  "error": {
    "code": "INVALID_EMAIL",
    "message": "邮箱格式不正确",
    "details": {
      "field": "email",
      "value": "invalid-email"
    }
  }
}
```

## 版本控制

### 方式 1：URL 路径（推荐）

```
GET /api/v1/users
GET /api/v2/users
```

### 方式 2：请求头

```http
GET /api/users
Accept: application/vnd.example.v1+json
```

### 版本策略

- 大版本变更：`v1` → `v2`（不兼容变更）
- 小版本变更：通过响应字段扩展（兼容变更）
- 废弃通知：在响应头中添加 `Deprecated: true`

## 分页、过滤、排序

### 分页

```
GET /users?page=1&pageSize=20
```

**响应**：
```json
{
  "data": [...],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "total": 100
  }
}
```

### 过滤

```
GET /users?status=active&role=admin
GET /orders?createdAfter=2025-01-01&amount>100
```

### 排序

```
GET /users?sort=createdAt:desc,name:asc
```

### 字段选择

```
GET /users?fields=id,name,email
```

## 认证与授权

### Bearer Token（推荐）

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

### API Key

```http
X-API-Key: your-api-key
```

### OAuth 2.0

```http
Authorization: Bearer <access_token>
```

## 速率限制

### 响应头

```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1640995200
```

### 超限响应

```
HTTP/1.1 429 Too Many Requests
Retry-After: 3600

{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "请求过于频繁，请稍后再试"
  }
}
```

## HATEOAS（可选）

在响应中包含相关链接：

```json
{
  "id": 123,
  "name": "张三",
  "links": {
    "self": "/users/123",
    "orders": "/users/123/orders",
    "profile": "/users/123/profile"
  }
}
```

## 幂等性设计

### 幂等性 Key

```http
POST /orders
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

服务器应该：
1. 检查该 Key 是否已处理
2. 如果已处理，返回之前的结果
3. 如果未处理，执行操作并记录 Key

## 批量操作

```
POST /users/batch
Content-Type: application/json

{
  "operations": [
    { "method": "POST", "path": "/users", "body": {...} },
    { "method": "PUT", "path": "/users/123", "body": {...} }
  ]
}
```

## 异步操作

对于耗时操作：

```
POST /reports/generate

HTTP/1.1 202 Accepted
Location: /reports/12345
```

查询状态：
```
GET /reports/12345

{
  "status": "processing",
  "progress": 45,
  "estimatedTime": 120
}
```

## 最佳实践清单

- [ ] 使用名词复数命名资源
- [ ] 正确使用 HTTP 方法和状态码
- [ ] 提供统一的错误响应格式
- [ ] 实现版本控制
- [ ] 支持分页、过滤、排序
- [ ] 添加认证和授权
- [ ] 实现速率限制
- [ ] 提供完整的 API 文档
- [ ] 设计幂等性保证
- [ ] 考虑向后兼容性

## 文档工具

- **OpenAPI (Swagger)**：标准 API 规范
- **Postman**：API 测试和文档
- **ReDoc**：美观的文档展示
- **SpringDoc**：Spring Boot 集成

## 示例：用户管理 API

```
# 用户 CRUD
GET    /api/v1/users              # 获取用户列表
POST   /api/v1/users              # 创建用户
GET    /api/v1/users/{id}         # 获取用户详情
PUT    /api/v1/users/{id}         # 更新用户
PATCH  /api/v1/users/{id}         # 部分更新用户
DELETE /api/v1/users/{id}         # 删除用户

# 用户订单
GET    /api/v1/users/{id}/orders  # 获取用户订单列表
POST   /api/v1/users/{id}/orders  # 为用户创建订单

# 搜索
GET    /api/v1/users/search?q=keyword

# 批量操作
POST   /api/v1/users/batch-delete
```

## 参考资源

- [Microsoft REST API Guidelines](https://github.com/microsoft/api-guidelines)
- [Google API Design Guide](https://cloud.google.com/apis/design)
- [RESTful API Design Best Practices](https://restfulapi.net/)

# 内容审核集成接口使用文档

## 概述

本接口供队友子系统（Web端/App端）在用户提交评论或上传图片时调用，触发内容审核流程。

**基地址**：`http://<服务器IP>:8080`

**认证方式**：请求头携带 API Key

---

## 认证

所有 `/api/integration/**` 接口（除 `/health`）都需要在请求头中携带密钥：

```
X-Integration-Api-Key: dev-integration-key-change-me
```

> 密钥不正确时返回 401：
> ```json
> {"success": false, "message": "集成 API 密钥无效或缺失，请在请求头 X-Integration-Api-Key 中携带正确密钥", "data": null}
> ```

---

## 接口列表

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/integration/health` | 健康检查（无需认证） |
| POST | `/api/integration/comments` | 提交评论审核 |
| POST | `/api/integration/photos` | 提交图片审核 |
| POST | `/api/integration/logins` | 用户登录上报 |

---

## 1. 提交评论

### 请求

```
POST /api/integration/comments
Content-Type: application/json
X-Integration-Api-Key: dev-integration-key-change-me
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 发表评论的用户ID（统一用户中心ID） |
| museumId | Integer | 是 | 馆别ID |
| objectId | String | 是 | 文物编号 |
| content | String | 是 | 评论内容文本 |
| source | String | 是 | 来源系统，填 `"web"` 或 `"app"` |

### 请求示例

```json
{
  "userId": 1001,
  "museumId": 1,
  "objectId": "BJ-001",
  "content": "这个文物很精美！",
  "source": "web"
}
```

### 响应

```json
{
  "success": true,
  "message": "操作成功",
  "data": {
    "id": 42,
    "sourceTable": "comment",
    "reviewStatus": "APPROVED",
    "riskScore": 0,
    "sensitiveWordsHit": null,
    "displayable": true,
    "pendingManualReview": false,
    "message": "审核通过，可以展示"
  }
}
```

### 审核结果说明

| reviewStatus | displayable | pendingManualReview | 含义 | 你的系统应如何处理 |
|--------------|-------------|---------------------|------|-------------------|
| `APPROVED` | true | false | 自动审核通过 | 直接展示评论 |
| `PENDING` | false | true | 进入人工审核队列 | 提示用户"评论已提交，等待审核" |
| `REJECTED` | false | false | 自动审核拒绝 | 提示用户评论违规，不展示 |

### 拒绝时的响应示例

```json
{
  "success": true,
  "message": "操作成功",
  "data": {
    "id": null,
    "sourceTable": "comment",
    "reviewStatus": "REJECTED",
    "riskScore": 100,
    "sensitiveWordsHit": null,
    "displayable": false,
    "pendingManualReview": false,
    "message": "内容违规无法发布，请修改后重试（命中：暴力）"
  }
}
```

---

## 2. 提交图片

### 请求

```
POST /api/integration/photos
Content-Type: application/json
X-Integration-Api-Key: dev-integration-key-change-me
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 上传用户的ID |
| photoUrl | String | 是 | 图片的完整URL地址 |
| description | String | 否 | 图片描述文字 |
| museumId | Integer | 否 | 馆别ID |
| objectId | String | 否 | 文物编号 |
| source | String | 是 | 来源系统，填 `"web"` 或 `"app"` |

### 请求示例

```json
{
  "userId": 1001,
  "photoUrl": "https://your-oss.com/uploads/photo123.jpg",
  "description": "故宫文物拍摄",
  "museumId": 1,
  "objectId": "BJ-002",
  "source": "app"
}
```

### 响应

```json
{
  "success": true,
  "message": "操作成功",
  "data": {
    "id": 15,
    "sourceTable": "user_upload_photo",
    "reviewStatus": "PENDING",
    "riskScore": 0,
    "sensitiveWordsHit": null,
    "displayable": false,
    "pendingManualReview": true,
    "message": "已提交，等待人工审核"
  }
}
```

> **注意**：图片审核采用**全人工审核**模式，所有图片提交后状态均为 `PENDING`，需等待管理员人工审核通过后才能展示。

---

## 3. 用户登录上报

### 请求

```
POST /api/integration/logins
Content-Type: application/json
X-Integration-Api-Key: dev-integration-key-change-me
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 登录用户ID |
| source | String | 是 | 来源系统 `"web"` 或 `"app"` |
| ipAddress | String | 否 | 用户登录IP |

### 请求示例

```json
{
  "userId": 1001,
  "source": "web",
  "ipAddress": "192.168.1.100"
}
```

### 响应

```json
{
  "success": true,
  "message": "操作成功",
  "data": {
    "userId": 1001,
    "source": "web"
  }
}
```

---

## 4. 健康检查

### 请求

```
GET /api/integration/health
```

（无需携带 API Key）

### 响应

```json
{
  "success": true,
  "message": "操作成功",
  "data": {
    "status": "up",
    "service": "admin-backend-content-review"
  }
}
```

---

## 审核流程说明

```
用户提交内容
    │
    ▼
┌─────────────────┐
│  调用集成接口    │
│  POST /comments │
│  POST /photos   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  风险评分计算    │
│  (敏感词+模型)  │
└────────┬────────┘
         │
    ┌────┼────────────┐
    │    │             │
    ▼    ▼             ▼
 评论             图片
    │                  │
    ▼                  ▼
┌──────────┐    ┌──────────────┐
│按策略决策 │    │ 一律人工审核  │
│≤20:通过  │    │ status=PENDING│
│21~60:人工│    └──────────────┘
│>60:拒绝  │
└──────────┘
```

---

## 接入步骤

1. **获取 API Key**：联系后台管理员获取 `X-Integration-Api-Key` 值
2. **配置请求头**：所有请求带上 `X-Integration-Api-Key` 和 `Content-Type: application/json`
3. **调用接口**：用户发布评论/上传图片时，先调用对应接口
4. **处理响应**：
   - `displayable = true` → 直接展示内容
   - `pendingManualReview = true` → 提示用户等待审核
   - `reviewStatus = REJECTED` → 提示用户内容违规

---

## 错误码

| HTTP状态码 | 含义 | 示例场景 |
|-----------|------|---------|
| 200 | 成功（包括审核拒绝） | 正常审核流程 |
| 400 | 请求参数错误 | 缺少必填字段、用户不存在 |
| 401 | API Key 无效 | 未携带或密钥错误 |
| 500 | 服务器内部错误 | 数据库异常等 |

---

## 注意事项

1. **用户ID**：`userId` 必须是统一用户中心中已存在的用户，否则返回 400 "用户不存在"
2. **图片URL**：`photoUrl` 必须是可公网访问的完整URL（含 http/https 协议头）
3. **并发安全**：接口支持并发调用，无需调用方做额外锁控制
4. **幂等性**：每次调用都会创建新的审核记录，调用方需自行保证不重复提交

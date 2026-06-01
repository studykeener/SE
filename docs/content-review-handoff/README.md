# 内容审核 — Web/App 子系统对接包（共用 MySQL 版）

**提供方：** 子系统 5 后台管理  
**与 admin-backend 代码一致：** `ReviewQueueService` + `DefaultImageModerationService`（关键词部分）  
**更新：** 2026-06-01

---

## 1. 对接方式（本包）

**不再要求** Web 调 `http://xxx:8080/api/integration/**`（admin-backend 可以不开）。

Web/App 在用户提交评论/图片时：

1. 连接 **共用 MySQL**（与后台相同库）
2. 每次提交前 **读取** `sensitive_words`、`review_strategy_config`（id=1）
3. 调用本目录 **`ContentReviewEngine`**（Java）或 **`content_review_engine.py`**（Python）算风险分、定状态
4. **INSERT** 到 `comment` 或 `user_upload_photo`
5. 子系统 5 管理后台「内容审核」页会 **直接读同一张表**，人工复审

你在后台改敏感词/策略 → 他们 **下一次提交** 自动读到新规则。

---

## 2. 数据库

| 项目 | 值 |
|------|-----|
| 库名 | `overseas_chinese_artifacts`（以组内实际为准） |
| 评论表 | `comment` |
| 图片表 | `user_upload_photo` |
| 敏感词 | `sensitive_words`（`enabled=1` 参与匹配） |
| 策略 | `review_strategy_config`（**固定 id=1**，无则按默认策略） |

### 2.1 评论 `comment` 关键字段

| 字段 | 说明 |
|------|------|
| `user_id` | 统一用户表 `user.user_id` |
| `museum_id`, `object_id` | 关联文物 |
| `content` | 评论正文 |
| `source` | `web` 或 `app`（小写） |
| `audit_status` | **0**待审 **1**通过 **2**拒绝 **3**复审 |
| `audit_method` | **1**自动审核 **2**人工 **3**自动通过 |
| `auto_audit_status` | 风险分 0–100（tinyint） |
| `sensitive_words_hit` | 命中词，逗号分隔 |
| `status` | **1**正常显示 **0**用户删 **2**后台屏蔽（新建评论填 **1**） |

**前台展示评论：** 仅展示 `audit_status = 1` 且 `status = 1` 的记录。

### 2.2 图片 `user_upload_photo` 关键字段

| 字段 | 说明 |
|------|------|
| `photo_url` | 图片 **URL**（先上传到 OSS/静态服务，再写链接） |
| `description` | 可选说明 |
| `status` | 同评论 audit_status：**0**待审 **1**通过 **2**拒绝 **3**复审 |
| `audit_method` | 图片固定写 **2**（人工审核通道） |
| `auto_audit_score` | 风险分（decimal，供审核员参考） |
| `auto_audit_status` | **1** 或 **2**（≥60 写 2，否则 1，与现网一致） |

**当前规则：所有用户上传图片一律 `status=0`（待人工审核）**，不论风险分高低。

---

## 3. 审核算法（与后台完全一致）

实现见 `ContentReviewEngine.java` / `content_review_engine.py`。

### 3.1 风险分 `computeRisk(text, url, isImage)`

1. 加载 `enabled=1` 的敏感词，按 `word` 升序（与后台 JPA 一致）
2. 在 `(text + " " + url).toLowerCase()` 中 **子串** 匹配（非整词边界）
3. 命中 **SEVERE** 级敏感词 → 立即 **100 分**，不再继续
4. 普通 **LIGHT** 词：每出现 1 次 +10 分
5. **图片** 额外逻辑：
   - 若配置了 `externalImageScore`（可选，见下）→ `score = max(score, externalImageScore)`
   - URL/描述含 `childporn, terror, 爆炸物, 恋童, 极端暴力, 严重违规` → **100 分**
   - 含 `violence, porn, bloody, 涉黄, 暴力, 违规` → +10 分
6. 最终 `min(100, max(0, score))`

### 3.2 评论自动策略 `applyCommentReview(score, strategy)`

读 `review_strategy_config` id=1：

| 分数区间 | 默认动作 |
|----------|----------|
| ≤ `low_risk_max_score`（默认 20） | `low_risk_action` → 默认 **AUTO_APPROVE** |
| ≤ `medium_risk_max_score`（默认 60） | `medium_risk_action` → 默认 **MANUAL_REVIEW** |
| 更高 | `high_risk_action` → 默认 **AUTO_REJECT** |

动作映射：

| 动作 | audit_status | audit_method | 是否 INSERT |
|------|--------------|--------------|-------------|
| AUTO_APPROVE | 1 | 3 | **是** |
| MANUAL_REVIEW | 0 | 1 | **是** |
| AUTO_REJECT | — | — | **否**，直接提示用户（与 integration API 一致） |

**拒绝提示文案（与后台一致）：**

- 有命中词：`内容违规无法发布，请修改后重试（命中：词1,词2）`
- 否则：`内容违规无法发布，请修改后重试`

### 3.3 图片策略

- 一律：`status=0`，`audit_method=2`
- 必须 **INSERT**（进入人工队列）
- 提示用户：`已提交，等待人工审核`

### 3.4 关于 NSFW 模型（图片）

admin-backend 若配置 `image-moderation.mode=local`，会用 **ONNX 模型** 对图片 URL 再打分，Web **本包不含模型**。

- **图片审核状态** 不受影响（仍全是待审）
- **auto_audit_score** 可能与后台略有差异（仅影响审核员看到的参考分）

若需完全一致，可选：仍调用 `POST /api/integration/photos` 仅由后台入库（旧方案）。

---

## 4. 提交前校验

```sql
SELECT 1 FROM user WHERE user_id = ? LIMIT 1;
```

用户不存在则拒绝提交。

`source` 规范化：`APP`/`app` → `app`，其余 → `web`。

---

## 5. SQL 示例

### 5.1 加载敏感词

```sql
SELECT word, level FROM sensitive_words WHERE enabled = 1 ORDER BY word ASC;
```

### 5.2 加载策略

```sql
SELECT low_risk_max_score, medium_risk_max_score,
       low_risk_action, medium_risk_action, high_risk_action
FROM review_strategy_config WHERE id = 1;
```

无记录时使用默认：20 / 60 / AUTO_APPROVE / MANUAL_REVIEW / AUTO_REJECT。

### 5.3 插入评论（自动通过后示例）

```sql
INSERT INTO comment (
  user_id, museum_id, object_id, content, source,
  audit_method, audit_status, auto_audit_status, sensitive_words_hit, status
) VALUES (
  1001, 1, 'ld1-xxx', '这个文物很精美', 'web',
  3, 1, 0, NULL, 1
);
```

### 5.4 插入图片（一律待审）

```sql
INSERT INTO user_upload_photo (
  user_id, museum_id, object_id, photo_url, description, source,
  status, audit_method, auto_audit_status, auto_audit_score
) VALUES (
  1001, 1, 'ld1-xxx', 'https://your-cdn.com/u/1.jpg', '说明', 'web',
  0, 2, 1, 10.00
);
```

---

## 6. 文件清单

| 文件 | 用途 |
|------|------|
| `README.md` | 本说明 |
| `ContentReviewEngine.java` | Java 参考实现（无 Spring 依赖，可复制进项目） |
| `content_review_engine.py` | Python 参考实现 |
| `example_usage.java` | Java 调用示例 |
| `example_usage.py` | Python 调用示例 |

---

## 7. 联调自检

- [ ] Web 发一条低风险评论 → `audit_status=1`，前台可见
- [ ] 发一条含 **SEVERE** 敏感词 → **不入库**，用户看到拒绝提示
- [ ] 发一条中等风险 → `audit_status=0`，前台不可见，后台待审列表有
- [ ] 上传图片 → `user_upload_photo.status=0`，后台待审有记录
- [ ] 后台改敏感词/策略 → Web 再发一条，行为随之变化

---

## 8. 与旧 integration API 的关系

| 方式 | 适用 |
|------|------|
| **本包（共用库 + 引擎代码）** | 推荐；不要求 8080 常开 |
| `POST /api/integration/comments` | 可选；逻辑与本包一致，由后台代写库 |

两者不要对同一条内容 **重复 INSERT**。

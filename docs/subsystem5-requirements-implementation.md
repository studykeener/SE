# 子系统5（后台管理）需求与实现对照文档

> 对照来源：`d:\SE\1.课程设计题目-海外藏中国文物知识管理与服务平台.docx` 第五节「后台管理子系统」  
> 代码仓库：`d:\SE\admin-backend`  
> 文档版本：与当前代码一致（2026-05）

---

## 0. 总体架构说明

### 0.1 技术栈

| 层次 | 技术 |
|------|------|
| 后端 | Java 17、Spring Boot 3.3、Spring Security、JWT、Spring Data JPA |
| 关系库 | MySQL 8，库名 `overseas_artifacts`，**21 张表**（6 共用 + 15 子系统5） |
| 图数据库 | Neo4j 5（知识图谱，可选，`kg.neo4j.enabled`） |
| 前端 | 单页 `src/main/resources/static/index.html` |

### 0.2 账号体系分离

课程要求：**后台管理员与前台用户账号分离**。

| 账号类型 | 数据表 | 登录入口 |
|----------|--------|----------|
| 后台管理员 | `admin_users` | `POST /api/admin/auth/login` |
| 前台用户（Web/App） | `user` | 由各子系统自行实现；本后台仅管理 |

首次启动自动初始化超级管理员：`admin / 123456`（见 `AdminAccountInitializer.java`）。

### 0.3 与队友子系统的协作方式

- **共用 MySQL**：Web/App 用户、评论、上传照片等写入共用表，后台直接读写。
- **集成 API**（可选）：`POST /api/integration/comments|photos|logins`，请求头 `X-Integration-Api-Key`。
- **知识图谱**：Neo4j 由 `graph-db` 项目 Docker 启动，后台通过 Bolt 直连。

### 0.4 实现状态图例

| 标记 | 含义 |
|------|------|
| ✅ 已实现 | 功能可用，与需求基本对齐 |
| ⚠️ 部分实现 | 有核心能力，但与文档描述有差距 |
| ❌ 未实现 | 代码中不存在或仅为占位 |
| 🔵 选做 | 课程文档标注为选做项 |

### 0.5 前端菜单与模块映射

| 菜单 | 对应需求章节 |
|------|--------------|
| 系统概览 | （7）系统监控看板 |
| 管理员管理 | （1）角色与权限 — 管理员账号 |
| 角色权限管理 | （1）角色与权限 |
| 平台用户管理 | （2）用户管理 |
| 内容审核 | （3）内容审核 |
| 文物数据管理 | （4）数据管理 — 文物 |
| 知识图谱管理 | （4）数据管理 — 图谱 |
| 数据备份与恢复 | （5）备份恢复 |
| 日志管理 | （6）日志管理 |

---

## （1）角色与权限管理

### 1.1 课程要求摘要

- 基于角色的访问控制（RBAC）
- 至少三类角色：超级管理员、内容审核员、数据管理员
- 支持自定义角色与细粒度权限（查看/编辑/删除等）
- 所有权限变更须记录操作日志

### 1.2 数据模型

| 表名 | 说明 |
|------|------|
| `role_definitions` | 角色定义（内置 + 自定义） |
| `permission_definitions` | 权限定义（模块 + 动作） |
| `role_permission_assignments` | 角色 ↔ 权限 |
| `admin_users` | 管理员账号，**每人一个** `role_id` |
| `admin_role_permission_audit` | 角色/权限变更专用审计 |
| `operation_logs` | 同步写入操作日志 |

### 1.3 三类内置角色

启动时由 `RolePermissionService.initDefaults()` 初始化：

| 角色编码 | 名称 | 默认权限 | 实现状态 |
|----------|------|----------|----------|
| `SUPER_ADMIN` | 超级管理员 | **自动拥有全部 18 项权限**（`ensureSuperAdminHasAllPermissions()`） | ✅ |
| `CONTENT_REVIEWER` | 内容审核员 | `REVIEW_VIEW`、`REVIEW_ACTION` | ✅ |
| `DATA_ADMIN` | 数据管理员 | `ARTIFACT_*`（查看/编辑/删除/导入导出）、`STATS_VIEW` | ✅ |

**权限码清单**（`PermissionCodes.java`）：

```
USER_VIEW / USER_EDIT / USER_DELETE / USER_BAN
REVIEW_VIEW / REVIEW_ACTION
ARTIFACT_VIEW / ARTIFACT_EDIT / ARTIFACT_DELETE / ARTIFACT_IMPORT_EXPORT
LOG_VIEW / STATS_VIEW
ROLE_VIEW / ROLE_CREATE / ROLE_ASSIGN / PERMISSION_ASSIGN
ADMIN_MANAGE / BACKUP_MANAGE
```

Spring Security 中 authority 格式为 `PERM_` + 权限码；各 Controller 方法使用 `@PreAuthorize("hasAuthority('PERM_xxx')")` 拦截。

**与课程要求的对齐：**

| 要求 | 状态 | 说明 |
|------|------|------|
| 超级管理员全部权限 | ✅ | 含创建管理员、备份恢复、RBAC 配置等 |
| 内容审核员仅审核 | ✅ | 无 `USER_*`、`LOG_VIEW`（完整日志）、`BACKUP_*` 等 |
| 数据管理员管文物、不看用户/系统日志 | ⚠️ | 有文物 CRUD + 看板；**无** `LOG_VIEW`，符合「无法操作系统日志」；但可通过 `STATS_VIEW` 看统计看板 |
| 自定义角色 | ✅ | `POST /api/admin/rbac/roles` + 分配权限 |
| 细粒度权限 | ✅ | 18 项权限按模块划分 |
| 权限变更记日志 | ✅ | 见 1.5 |

### 1.4 API 与代码

| 功能 | HTTP | 控制器 |
|------|------|--------|
| 角色列表 | `GET /api/admin/rbac/roles` | `RolePermissionController` |
| 创建自定义角色 | `POST /api/admin/rbac/roles` | 需 `ROLE_CREATE` |
| 权限列表 | `GET /api/admin/rbac/permissions` | |
| 查询/设置角色权限 | `GET/POST /api/admin/rbac/roles/{roleId}/permissions` | 需 `PERMISSION_ASSIGN` |
| 为管理员分配角色 | `POST /api/admin/rbac/admins/{adminId}/roles` | 需 `ROLE_ASSIGN` |
| 管理员 CRUD | `GET/POST/PATCH/DELETE /api/admin/users` | `AdminUserController`，需 `ADMIN_MANAGE` |
| 登录 / 当前用户 | `POST /api/admin/auth/login`、`GET /api/admin/auth/me` | `AuthController` |

**鉴权链路：**

```
登录 → JWT → JwtAuthenticationFilter → @PreAuthorize → 业务方法
```

`AdminUserDetailsService` 从 `admin_users` 加载用户，经 `RolePermissionService.getPermissionCodesByAdminId()` 注入权限列表。

### 1.5 权限变更审计

| 审计渠道 | 记录内容 | 状态 |
|----------|----------|------|
| `admin_role_permission_audit` | 创建角色、分配角色权限、分配管理员角色；含 before/after JSON 快照 | ✅ 写入 |
| `operation_logs` | 同上，类型 `CREATE_ROLE`、`ASSIGN_ROLE_PERMISSION`、`ASSIGN_ADMIN_ROLE` | ✅ |
| 专用查询 API | 无 REST 列表接口 | ❌ |
| 前端 RBAC 审计页 | 无 | ❌ |

可在「日志管理 → 操作日志 / 安全日志」中间接查看权限相关变更。

### 1.6 差距与答辩说明

| 项目 | 说明 |
|------|------|
| 单管理员单角色 | 每人仅一个 `role_id`，不支持多角色叠加 |
| 无角色删除/改名 API | 自定义角色创建后无法在 API 层删除 |
| `DATA_ADMIN` 启动校正 | 仅 `CONTENT_REVIEWER` 在每次启动时强制同步权限，`DATA_ADMIN` 不自动校正 |
| 内置 `admin` 账号保护 | 不可删除、不可禁用、不可降级角色 |

---

## （2）用户管理

### 2.1 课程要求摘要

- 统一管理全平台用户（知识服务 Web、掌上博物馆 App 等）
- 用户信息 CRUD、筛选、批量操作
- 启用/禁用；细粒度限制（禁评论、禁上传）
- 用户行为记录（评论、上传等）
- 🔵 违规处理记录与申诉（选做）

### 2.2 数据模型

| 表名 | 用途 |
|------|------|
| `user` | 前台用户主表（共用），含 `can_comment`、`can_upload` |
| `user_permission_audit` | 用户状态/权限变更审计 |
| `comment`、`user_upload_photo`、`user_favorite`、`user_like` | 行为追溯数据源 |

**说明：** 后台管理员在 `admin_users`，**不在** `user` 表中；平台用户管理页管的是前台用户。

### 2.3 用户信息管理

| 要求 | 状态 | 实现 |
|------|------|------|
| 增删改查 | ✅ | `UnifiedUserController` → `/api/admin/unified-users` |
| 按用户名筛选 | ✅ | `?username=` |
| 按注册时间筛选 | ✅ | `?createdFrom=&createdTo=` |
| 按用户来源筛选 | ✅ | `?sourceSystem=web|app`（字段 `user.user_source`） |
| 按状态筛选 | ✅ | `?status=` |
| 分页 | ✅ | `?page=&size=`（单页最大 200） |
| 批量删除 | ✅ | `DELETE /api/admin/unified-users/batch` |
| 批量改状态 | ✅ | `PATCH /api/admin/unified-users/batch/status` |

创建用户默认密码 `ChangeMe123`；删除用户会级联清理其评论、上传、收藏、点赞。

### 2.4 用户状态与细粒度权限

| 要求 | 状态 | 实现 |
|------|------|------|
| 启用/禁用账号 | ✅ | `PATCH /{id}/status?status=ENABLED\|DISABLED&reason=`，写 `user.status` |
| 禁止评论 | ✅ | `user.can_comment`，`PATCH /{id}/permissions` |
| 禁止上传 | ✅ | `user.can_upload`，同上 |
| 变更审计 | ✅ | 写入 `user_permission_audit` + `operation_logs` |
| 批量限制评论/上传 | ⚠️ | 前端循环调用单用户 API，无专用批量接口 |

禁用时会记录 `disabled_reason`、`disabled_by`、`disabled_at`。

### 2.5 用户行为记录

| 要求 | 状态 | 实现 |
|------|------|------|
| 查看特定用户操作历史 | ✅ | `GET /api/admin/unified-users/{id}/behaviors` |
| 评论记录 | ✅ | 来自 `comment` |
| 上传记录 | ✅ | 来自 `user_upload_photo` |
| 收藏/点赞 | ✅ | 来自 `user_favorite`、`user_like` |
| 按类型/时间筛选 | ✅ | `?type=COMMENT|UPLOAD|FAVORITE|LIKE&from=&to=` |
| 分页 | ✅ | `?page=&size=` |

实现类：`UserActivityTraceService.java`。前端「平台用户管理 → 行为记录」弹窗展示。

**不包含：** 登录记录（在 `login_logs`）、管理员操作（在 `operation_logs`）。

### 2.6 违规处理记录（选做）

| 要求 | 状态 |
|------|------|
| 独立违规/处罚表 | ❌ 未建 `user_violation_records` |
| 申诉流程 | ❌ |
| 替代方案 | ⚠️ `user_permission_audit` 记录权限/状态变更；行为追溯中可见被拒绝的 UGC（`auditStatus`） |

### 2.7 差距与答辩说明

- 用户来源字段为 `web` / `app`，与「知识服务 / 掌上博物馆」子系统名称需在答辩中口头映射。
- 队友子系统用户须写入共用 `user` 表，或由本后台创建，才能在此统一管理。
- 无用户列表 CSV 导出。

---

## （3）内容审核

### 3.1 课程要求摘要

**自动审核：** 敏感词、图片违规检测、可配置策略  
**人工审核：** 待审队列、单条/批量操作、审核统计

### 3.2 数据模型

| 表名 | 用途 |
|------|------|
| `comment` | 评论 + 审核字段（待审队列之一） |
| `user_upload_photo` | 上传照片 + 审核字段（待审队列之二） |
| `sensitive_words` | 敏感词库 |
| `review_strategy_config` | 自动审核策略（单行 id=1） |

未单独建 `review_contents` 表，待审队列直接读共用表。

### 3.3 自动审核 — 文本

| 要求 | 状态 | 实现 |
|------|------|------|
| 维护敏感词库 | ✅ | `ReviewController` → `/api/admin/reviews/sensitive-words` CRUD |
| 在线增删敏感词 | ✅ | 前端「内容审核 → 敏感词库」 |
| 敏感词分级 | ✅ | `LIGHT` / `SEVERE`（`SensitiveWordLevel`） |
| 提交时自动检测 | ✅ | `ReviewQueueService.computeRisk()` |
| 命中则拦截 | ✅ | 高风险 + 策略为自动拒绝时，集成 API 返回错误，不入库展示 |
| 提示用户修改 | ✅ | 异常信息：`内容违规无法发布，请修改后重试（命中：xxx）` |

**文本风险分算法**（`computeRisk(text, url, image=false)`）：

1. 将文本转小写，遍历启用敏感词
2. 命中 **SEVERE** → 分数 **100**
3. 每命中一个 **LIGHT** → **+10**
4. 上限 100

### 3.4 自动审核 — 图片

| 要求 | 状态 | 实现 |
|------|------|------|
| 调用图像识别接口 | ❌ | **未接入**任何 Vision / 内容安全 API |
| 违规图片自动屏蔽 | ⚠️ | 仅基于 **描述文字 + 图片 URL 字符串** 的关键词规则 |
| 记录审核结果 | ✅ | `auto_audit_score`、`auto_audit_status`、`audit_method` |

**图片额外规则**（`image=true` 时）：

- URL/描述含 `childporn`、`terror`、`恋童` 等 → 分数 **100**
- 含 `porn`、`violence`、`涉黄`、`违规` 等 → **+10**

**答辩务必说明：** 当前不能识别图片像素内容；真实图像审核需对接阿里云/腾讯云等 API，框架（风险分 + 策略 + 人工队列）已预留。

### 3.5 自动审核策略配置

| 要求 | 状态 | 实现 |
|------|------|------|
| 低风险/中风险/高风险阈值 | ✅ | `review_strategy_config.low_risk_max_score`（默认 20）、`medium_risk_max_score`（默认 60） |
| 各档动作 | ✅ | `AUTO_APPROVE` / `MANUAL_REVIEW` / `AUTO_REJECT` |
| 在线修改 | ✅ | `GET/PUT /api/admin/reviews/strategy` |
| 策略变更日志 | ✅ | `GET /api/admin/reviews/strategy/logs` |

默认策略：低分自动通过、中分转人工、高分自动拒绝。

### 3.6 人工审核

| 要求 | 状态 | 实现 |
|------|------|------|
| 待审队列 | ✅ | `GET /api/admin/reviews`，合并 `comment` + `user_upload_photo` |
| 按内容类型筛选 | ✅ | `?contentType=COMMENT|IMAGE`（VIDEO/AUDIO 仅 UI 占位） |
| 按提交时间筛选 | ✅ | `?submitFrom=&submitTo=` |
| 按来源筛选 | ✅ | `?sourceSystem=web|app` |
| 按风险分筛选 | ✅ | `?riskMin=&riskMax=` |
| 通过/拒绝/复审 | ✅ | `PATCH /api/admin/reviews/{sourceTable}/{id}/action` |
| 拒绝填原因 | ✅ | `REJECTED` / `RECHECK` 必填 `rejectReason` |
| 批量审核 | ✅ | `PATCH /api/admin/reviews/batch/action` |
| 音视频审核 | ❌ | `ContentType.VIDEO/AUDIO` 存在但提交时报「待队友接入」 |

**内容入口：**

- 后台测试：`POST /api/admin/reviews`
- 队友集成：`POST /api/integration/comments`、`POST /api/integration/photos`

### 3.7 审核统计

| 要求 | 状态 | 实现 |
|------|------|------|
| 每日审核量 | ✅ | `GET /api/admin/reviews/stats?from=&to=` → `daily[]` |
| 通过率/拒绝率 | ✅ | `approveRate`、`rejectRate` |
| 按审核员统计工作量 | ✅ | `reviewerWorkload`、`contentReviewerWorkload` |
| 预聚合日表 | ❌ | 实时计算，未建 `review_statistics_daily` |

### 3.8 差距汇总

| 项目 | 状态 |
|------|------|
| 真实图像识别 | ❌ |
| 音视频内容 | ❌ |
| 待审列表服务端分页 | ❌（全量加载后内存过滤） |

---

## （4）数据管理

### 4.1 课程要求摘要

- 文物数据 CRUD、CSV/JSON 导入导出、图片批量上传替换
- 知识图谱三元组在线编辑、同步 Neo4j
- 用户生成内容管理
- 🔵 数据一致性检查（选做）

### 4.2 文物数据管理（MySQL）

| 要求 | 状态 | 实现 |
|------|------|------|
| 增删改查 | ✅ | `ArtifactController` → `/api/admin/artifacts` |
| 筛选（关键词、馆别、年代、类型等） | ✅ | `GET` 查询参数 + JPA Specification |
| CSV 导出 | ✅ | `GET /api/admin/artifacts/export` |
| CSV 导入 | ✅ | `POST /api/admin/artifacts/import`（multipart） |
| JSON 导入导出 | ❌ | 仅 CSV |
| 图片批量上传/替换 | ❌ | 文物图片字段为 URL/路径文本，无文件上传接口 |
| 分页 | ❌ | 列表返回全量 |

**数据表：** `artifact`（复合主键 `museum_id` + `object_id`）  
**导入服务：** `ArtifactImportService.java`

### 4.3 知识图谱管理（Neo4j）

| 要求 | 状态 | 实现 |
|------|------|------|
| 实体 CRUD | ✅ | `/api/admin/kg/entities` |
| 关系类型管理 | ✅ | `/api/admin/kg/relation-types` |
| 三元组 CRUD | ✅ | `/api/admin/kg/triples` |
| 图谱概览 | ✅ | `/api/admin/kg/overview` |
| 同步任务 | ✅ | `/api/admin/kg/sync/jobs` |
| 修改后更新图库 | ✅ | 直连 Neo4j 写入，即时生效 |
| Neo4j 未启动时 | ⚠️ | `KgController` 不加载（`@ConditionalOnBean`），前端图谱页不可用 |

**支持实体类型：** Artifact、Museum、Dynasty、Artist、Material、ArtifactType、Location、Culture  
**实现：** `Neo4jKgService.java`、`KgController.java`  
**配置：** `application.yml` → `kg.neo4j.uri/username/password`

MySQL `artifact` 与 Neo4j 图谱节点**无自动双向同步**；两边需分别维护或通过图谱构建子系统导入。

### 4.4 用户生成内容（UGC）管理

| 要求 | 状态 | 实现 |
|------|------|------|
| 管理评论 | ✅ | 通过「内容审核」队列 + 筛选 |
| 管理上传图片 | ✅ | 同上 |
| 按来源/类型/审核状态筛选 | ✅ | 审核页筛选条件 |
| 管理音视频 | ❌ | 未建 `user_upload_video` / `user_upload_audio` |
| 独立 UGC 管理页（非审核流） | ❌ | 无单独 CRUD 页，需在审核队列操作 |
| 收藏/点赞管理 | ❌ | 仅行为追溯只读 |

### 4.5 数据一致性检查（选做）

| 要求 | 状态 |
|------|------|
| MySQL ↔ Neo4j 定期比对 | ❌ |
| 不一致报告表 | ❌（`data_consistency_reports` 未建） |
| 部分相关 | ⚠️ 文物列表可按 `kgSyncStatus` 筛选（基于 `artist_enriched_at` 字段，非实时图库比对） |

---

## （5）数据备份与恢复

### 5.1 课程要求摘要

手动/定时备份、加密存储、记录管理、超级管理员二次确认恢复、保留策略。

### 5.2 实现对照

| 要求 | 状态 | 实现 |
|------|------|------|
| 手动全量备份 | ✅ | `POST /api/admin/backup/manual`，`backupType=FULL` |
| 指定表备份 | ✅ | `backupType=TABLES` + `tables[]` |
| 定时自动备份 | ✅ | `BackupService.autoBackupSchedulerTick()`，cron 默认 `0 0 2 * * *` |
| 备份文件加密 | ✅ | AES-CBC + 随机 IV，密钥 `backup.aes-key-base64` |
| 备份记录列表 | ✅ | `backup_records` 表 + `GET /api/admin/backup/records` |
| 下载备份 | ✅ | `GET /api/admin/backup/records/{id}/download` |
| 超级管理员二次确认恢复 | ✅ | `confirmText=CONFIRM_RESTORE` + `acknowledged=true`；且 `canRestore()` 仅 `SUPER_ADMIN` |
| 恢复审计 | ✅ | `restore_logs` + `operation_logs` |
| 保留策略 | ✅ | `backup_task_config.retention_days`（默认 30），每日 3:10 清理 |
| 配置定时任务 | ✅ | `GET/PUT /api/admin/backup/config` |

**代码：** `BackupController.java`、`BackupService.java`

### 5.3 备份范围与限制

| 项目 | 说明 |
|------|------|
| 备份对象 | **仅 MySQL** 业务表 |
| 排除表 | `backup_records`、`backup_task_config`、`restore_logs` |
| Neo4j | ❌ 不在备份范围内 |
| 恢复方式 | TRUNCATE 目标表 + 重新 INSERT（** destructive **） |
| checksum 字段 | 表中有定义，代码未写入校验值 |

---

## （6）日志管理

### 6.1 课程要求摘要

操作日志、系统日志、安全日志、多维检索、CSV/Excel 导出。

### 6.2 日志类型对照

| 课程要求 | 实现 | 数据表 / 来源 |
|----------|------|---------------|
| 操作日志 | ✅ | `operation_logs` — 操作人、时间、类型、对象、前后 JSON |
| 系统日志 | ✅ | `system_logs` — 异常、定时任务、备份事件等 |
| 安全日志 | ⚠️ | **无独立 `security_logs` 表**；`GET /api/admin/logs/security` 合并 `login_logs` + 权限类 `operation_logs` |
| 登录行为（含失败） | ✅ | `login_logs`；管理员失败见 `AuthController` → `result=FAILED` |
| 数据变更日志 | ✅ | `data_change_logs`（补充能力） |

### 6.3 查询与导出

| 功能 | API | 筛选参数 |
|------|-----|----------|
| 操作日志 | `GET /api/admin/logs` | operator, operationType, keyword, from, to |
| 系统日志 | `GET /api/admin/logs/system` | level, eventType, keyword, from, to |
| 安全日志 | `GET /api/admin/logs/security` | operator, keyword, from, to |
| 登录日志 | `GET /api/admin/logs/login` | username, result, from, to |
| 数据变更 | `GET /api/admin/logs/data-change` | operator, changeType, keyword, from, to |
| CSV 导出 | `GET /api/admin/logs/export/{operation\|system\|security\|login\|data-change}` | 同上 |

**权限：** 需 `LOG_VIEW`；内容审核员仅有 `REVIEW_VIEW` 时，操作日志仅能看到审核相关类型（`CREATE_REVIEW_CONTENT` 等）。

### 6.4 差距

| 项目 | 状态 |
|------|------|
| Excel 导出 | ❌（仅 CSV） |
| 服务端分页 | ❌（全表加载后内存过滤） |
| 敏感数据访问专项日志 | ❌ |

---

## （7）系统监控看板

### 7.1 课程要求摘要

实时指标、访问量趋势、数据增长趋势、🔵 异常告警。

### 7.2 实现对照

**API：** `GET /api/admin/dashboard/overview`（需 `STATS_VIEW`）  
**代码：** `DashboardController.java`

| 要求 | 响应字段 | 计算逻辑 | 状态 |
|------|----------|----------|------|
| 当前在线用户数 | `onlineUsers` | 近 15 分钟 `login_logs`（USER+SUCCESS）去重；fallback `user.last_login_at`；再 fallback 近期 UGC 作者 | ✅ |
| 今日新增用户 | `todayNewUsers` | `user.register_time` 当日计数 | ✅ |
| 今日内容提交量 | `todayContentSubmissions` | 当日新增 comment + photo | ✅ |
| 审核队列积压 | `pendingReviews`、`recheckReviews`、`queueBacklog` | `ReviewQueueService.countPending/Recheck` | ✅ |
| 访问量趋势（日/周/月） | `accessTrendDay/Week/Month` | 按 `login_logs.source_system`（web/app）统计去重登录用户 | ✅ |
| 数据增长趋势 | `growthTrend` | 近 14 天累计：用户注册、UGC 提交、文物 `crawl_date` | ✅ |
| 异常告警 | — | ❌ 未实现 | ❌ |
| 预聚合指标表 | — | ❌ 未建 `dashboard_metrics_daily` | ❌ |

### 7.3 前端展示

`index.html` → 「系统概览」：指标卡片 + ECharts 折线图（访问趋势、数据增长）。  
内容审核员登录时，看板仅显示待审/复审相关卡片。

### 7.4 依赖说明

**访问量趋势**依赖队友写入 `login_logs`：

```sql
INSERT INTO login_logs (user_type, user_id, username, result, ip_address, source_system, login_time)
VALUES ('USER', ?, ?, 'SUCCESS', ?, 'web', NOW());
```

或调用 `POST /api/integration/logins`。

---

## 附录 A：选做项「系统配置管理」（文档第 8 节）

课程文档第（8）节为选做，部分能力已分散实现：

| 选做要求 | 状态 | 位置 |
|----------|------|------|
| 敏感词库在线管理 | ✅ | 内容审核 → 敏感词库 |
| 审核策略配置 | ✅ | 内容审核 → 自动审核策略 |
| 系统公告 | ❌ | |
| 功能开关 | ❌ | |
| Neo4j 开关 | ⚠️ | 仅 `application.yml` 配置项，非在线 UI |

---

## 附录 B：API 路由总览

| 前缀 | 控制器 | 主要权限 |
|------|--------|----------|
| `/api/admin/auth` | AuthController | 公开 login |
| `/api/admin/users` | AdminUserController | ADMIN_MANAGE |
| `/api/admin/rbac` | RolePermissionController | ROLE_* / PERMISSION_* |
| `/api/admin/unified-users` | UnifiedUserController | USER_* |
| `/api/admin/reviews` | ReviewController | REVIEW_* |
| `/api/admin/artifacts` | ArtifactController | ARTIFACT_* |
| `/api/admin/kg` | KgController | ARTIFACT_VIEW（Neo4j 可用时） |
| `/api/admin/backup` | BackupController | BACKUP_MANAGE |
| `/api/admin/logs` | LogController | LOG_VIEW |
| `/api/admin/dashboard` | DashboardController | STATS_VIEW |
| `/api/integration` | IntegrationContentController | X-Integration-Api-Key |

---

## 附录 C：数据库表清单（21 张）

**6 张共用：** `artifact`, `user`, `comment`, `user_favorite`, `user_like`, `user_upload_photo`

**15 张子系统5：**  
`role_definitions`, `permission_definitions`, `role_permission_assignments`, `admin_users`, `admin_role_permission_audit`,  
`user_permission_audit`, `sensitive_words`, `review_strategy_config`,  
`backup_records`, `backup_task_config`, `restore_logs`,  
`operation_logs`, `login_logs`, `system_logs`, `data_change_logs`

完整 DDL：`docs/schema-6plus15.sql`

---

## 附录 D：需求完成度总表

| 章节 | 整体评估 | 主要缺口 |
|------|----------|----------|
| （1）角色与权限 | **基本完成** | 无角色删除；RBAC 审计无专用查询；单角色 |
| （2）用户管理 | **基本完成** | 违规记录选做未做；批量权限靠前端循环 |
| （3）内容审核 | **大部分完成** | 无真实图像 AI；无音视频 |
| （4）数据管理 | **部分完成** | 无 JSON 导入、无图片批量上传；无一致性检查；UGC 仅审核流 |
| （5）备份恢复 | **基本完成** | 仅 MySQL；恢复为全量覆盖 |
| （6）日志管理 | **基本完成** | 安全日志为虚拟合并；无 Excel；无分页 |
| （7）监控看板 | **大部分完成** | 异常告警选做未做 |

---

## 附录 E：答辩演示建议顺序

1. **登录** → 展示 JWT + 不同角色菜单差异（超管 / 审核员 / 数据管理员）
2. **平台用户管理** → 筛选、禁用、禁评论/上传、行为追溯
3. **内容审核** → 敏感词拦截、策略配置、人工通过/拒绝、审核统计
4. **文物数据** → CRUD + CSV 导入导出
5. **知识图谱**（Neo4j 需提前 `docker compose up -d`）→ 实体/三元组编辑
6. **备份** → 手动备份 + 下载（**勿现场演示恢复**）
7. **日志 + 看板** → 操作记录、登录失败、访问趋势

**务必主动说明的缺口：** 图片审核为规则模拟非 AI 识图；音视频 UGC 待接入；Neo4j 与 MySQL 文物数据无自动同步。

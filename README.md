# 后台管理子系统（基础版）

## 1. 技术栈
- Java 17
- Spring Boot 3
- Spring Security + JWT
- Spring Data JPA
- MySQL 8

## 2. 已完成功能（基础开发）
- 登录与鉴权：管理员 JWT 登录、按角色显示菜单
- 角色权限管理：角色列表、权限列表、管理员分配角色、角色分配权限
- 用户管理：列表/详情、启用禁用、评论上传限制、行为记录查看与新增
- 内容审核：待审队列、详情、单条审核、批量审核
- 文物管理：列表/新增/编辑/删除、CSV 导入导出、图谱同步状态字段
- 日志管理：操作日志、登录日志、数据修改日志（筛选）
- 统计看板：总用户、今日新增、待审核数、文物总数、近7天登录趋势
- 集成对接点：提供统一 API 对接配置，不直接修改其他子系统数据库

## 3. 数据库准备
在 MySQL 中执行：

```sql
CREATE DATABASE overseas_artifacts DEFAULT CHARACTER SET utf8mb4;
```

## 4. 配置数据库连接
编辑 `src/main/resources/application.yml`：
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.datasource.url`

## 5. 启动项目
```bash
mvn spring-boot:run
```

## 6. 初始化超级管理员
首次启动后，调用：

```http
POST /api/admin/auth/bootstrap
Content-Type: application/json

{
  "username": "admin",
  "password": "123456",
  "role": "SUPER_ADMIN"
}
```

说明：系统只允许初始化一次，后续不能再调用。

## 7. 登录与访问受保护接口
先调用登录接口获取 JWT：

```http
POST /api/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "123456"
}
```

后续请求在 Header 中带上：

```text
Authorization: Bearer <token>
```

## 8. 主要接口
- `GET /api/admin/users`：管理员列表（SUPER_ADMIN）
- `POST /api/admin/users`：创建管理员（SUPER_ADMIN）
- `PATCH /api/admin/users/{id}/status?status=ENABLED|DISABLED`：修改管理员状态
- `GET /api/admin/platform-users`：前台用户列表
- `POST /api/admin/platform-users`：创建前台用户
- `PATCH /api/admin/platform-users/{id}/status?status=ENABLED|DISABLED`
- `PATCH /api/admin/platform-users/{id}/permissions?commentAllowed=true&uploadAllowed=false`
- `GET /api/admin/logs`：查看操作日志
- `GET /api/admin/logs/login`：查看登录日志
- `GET /api/admin/logs/data-change`：查看数据修改日志
- `POST /api/admin/auth/login`：管理员登录
- `GET /api/admin/auth/me`：当前登录管理员信息
- `GET /api/admin/reviews?status=PENDING|APPROVED|REJECTED`：审核内容列表
- `POST /api/admin/reviews`：新增待审核内容
- `PATCH /api/admin/reviews/{id}/action`：审核动作（通过/拒绝）
- `PATCH /api/admin/reviews/batch/action`：批量审核
- `GET /api/admin/rbac/roles`：角色列表
- `GET /api/admin/rbac/permissions`：权限列表
- `POST /api/admin/rbac/admins/{adminId}/roles`：给管理员分配角色
- `POST /api/admin/rbac/roles/{roleId}/permissions`：给角色分配权限
- `GET /api/admin/artifacts`：文物列表
- `POST /api/admin/artifacts`：新增文物
- `PUT /api/admin/artifacts/{id}`：编辑文物
- `GET /api/admin/artifacts/export`：导出文物 CSV
- `POST /api/admin/artifacts/import`：导入文物 CSV
- `GET /api/admin/dashboard/overview`：统计看板
- `GET /api/admin/integrations/endpoints`：子系统 API 对接点

## 9. 简易前端页面
- 启动后访问：`http://localhost:8080/`
- 页面支持：
  - 登录页 + 后台模块化页面
  - 角色权限管理页面
  - 用户管理页面（含行为记录）
  - 内容审核页面（含批量）
  - 文物管理页面（含导出）
  - 日志与统计页面

# 后台管理子系统（基础版）

## 1. 技术栈
- Java 17
- Spring Boot 3
- Spring Security + JWT
- Spring Data JPA
- MySQL 8

## 2. 已完成功能（基础开发）
- 独立管理员账号体系（与前台用户分离）
- 角色权限基础控制（超级管理员、内容审核员、数据管理员）
- 管理员账号管理（创建、启停、查看）
- 前台用户管理（创建、启停、细粒度权限开关）
- 操作日志记录与查询
- 内容审核最小闭环（待审核内容、新增、通过/拒绝、拒绝原因）

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
- `POST /api/admin/auth/login`：管理员登录
- `GET /api/admin/auth/me`：当前登录管理员信息
- `GET /api/admin/reviews?status=PENDING|APPROVED|REJECTED`：审核内容列表
- `POST /api/admin/reviews`：新增待审核内容
- `PATCH /api/admin/reviews/{id}/action`：审核动作（通过/拒绝）

## 9. 简易前端页面
- 启动后访问：`http://localhost:8080/`
- 页面支持：
  - 管理员登录（JWT）
  - 首次初始化超级管理员
  - 查看/新增管理员
  - 查看/新增平台用户
  - 查看操作日志
  - 内容审核（新增待审、通过、拒绝）

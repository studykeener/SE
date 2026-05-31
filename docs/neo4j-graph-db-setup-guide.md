# Neo4j 图数据库 + graph-db 本地部署指南

> 适用对象：需要在本地运行 **后台管理子系统「知识图谱管理」** 的同学  
> 项目路径示例：`D:\SE\graph-db`（图库） + `D:\SE\admin-backend`（后台）  
> **不需要单独下载 Neo4j 安装包**，用 Docker 一键拉取官方镜像即可。

---

## 一、整体关系（先读这段）

```
graph-db 仓库                admin-backend 仓库
┌─────────────────┐         ┌──────────────────────┐
│ docker-compose  │         │ application.yml      │
│  启动 Neo4j     │◄─Bolt───│  kg.neo4j.uri        │
│  :7687 / :7474  │         │  /api/admin/kg/**    │
└─────────────────┘         └──────────────────────┘
        ▲
        │ 可选：Python 导入 CSV
        │ scripts/import_artifacts.py
        └── data/ 目录（图谱数据）
```

| 组件 | 作用 | 是否必须 |
|------|------|----------|
| Docker Desktop | 运行 Neo4j 容器 | **用图谱时必须** |
| `graph-db` | Neo4j 配置 + 数据导入脚本 | **用图谱时必须** |
| `admin-backend` | 后台 Web + 图谱管理 API | 整个后台都需要 |
| MySQL | 文物业务表 | 后台必须（与 Neo4j 独立） |

**重要：** 不做「知识图谱管理」的同学，可以在 `admin-backend` 里设 `kg.neo4j.enabled: false`，**不用装 Neo4j**，其余功能照常使用。

---

## 二、环境准备

### 2.1 必装软件

| 软件 | 版本建议 | 用途 |
|------|----------|------|
| **Docker Desktop** | 最新稳定版 | 运行 Neo4j 容器 |
| **Git** | 任意 | 克隆仓库 |
| **Java JDK** | 17 | 运行 admin-backend |
| **MySQL** | 8.x | 后台业务库 |
| **Python** | 3.10+ | 仅「导入图谱数据」时需要 |

### 2.2 安装 Docker Desktop（Windows）

1. 打开：https://www.docker.com/products/docker-desktop/
2. 下载 **Docker Desktop for Windows** 并安装
3. 安装完成后重启电脑（如安装程序提示）
4. 打开 Docker Desktop，等待左下角/托盘图标显示 **Running**
5. 验证（PowerShell）：

```powershell
docker --version
docker compose version
```

两条命令都有版本号输出即 OK。

> **Mac 同学：** 同样安装 Docker Desktop for Mac，后续命令在 Terminal 里执行即可。  
> **Linux 同学：** 安装 Docker Engine + Compose 插件，命令相同。

### 2.3 获取 graph-db 代码

**方式 A：Git 克隆（推荐）**

```powershell
cd D:\SE
git clone <你们的 graph-db 仓库地址> graph-db
```

**方式 B：压缩包**

从组长/网盘拿到 `graph-db` 文件夹，解压到例如 `D:\SE\graph-db`。

确认目录里至少有：

```text
graph-db/
  docker-compose.yml
  requirements.txt
  scripts/
    import_artifacts.py
    reset_graph.py
    queries.cypher
  data/          # 图谱 CSV 数据（可能较大）
  README.md
```

---

## 三、启动 Neo4j（核心步骤）

### 3.1 第一次启动

```powershell
cd D:\SE\graph-db
docker compose up -d
```

**第一次会自动下载镜像** `neo4j:5.26-community`（约 500MB+，需联网，耐心等待）。

### 3.2 检查是否启动成功

```powershell
docker ps --filter name=overseas-artifacts-neo4j
```

期望输出类似：

```text
NAMES                      STATUS    PORTS
overseas-artifacts-neo4j   Up ...    0.0.0.0:7474->7474/tcp, 0.0.0.0:7687->7687/tcp
```

### 3.3 浏览器登录 Neo4j Browser

1. 打开：http://localhost:7474  
2. 连接 URL 填：`bolt://localhost:7687`（或默认 neo4j://localhost:7687）  
3. 用户名：`neo4j`  
4. 密码：`password123`  

能进入并执行 Cypher 即表示图库已就绪。

### 3.4 默认连接信息（全队统一）

| 项 | 值 |
|----|-----|
| HTTP / Browser | http://localhost:7474 |
| Bolt（程序连接） | `bolt://localhost:7687` |
| 用户名 | `neo4j` |
| 密码 | `password123` |
| 容器名 | `overseas-artifacts-neo4j` |

以上与 `docker-compose.yml` 中 `NEO4J_AUTH: neo4j/password123` 一致。

### 3.5 常用 Docker 命令

```powershell
# 启动（后台运行）
docker compose up -d

# 停止
docker compose stop

# 停止并删除容器（数据仍在 neo4j-data/ 目录，不会丢）
docker compose down

# 查看日志（启动失败时用）
docker logs overseas-artifacts-neo4j

# 实时跟踪日志
docker logs -f overseas-artifacts-neo4j
```

### 3.6 每次开机后怎么做

Neo4j **不会随 Windows 自动启动**，需要：

1. 先打开 **Docker Desktop**（等 Running）
2. 再执行：

```powershell
cd D:\SE\graph-db
docker compose up -d
```

可选：在 `docker-compose.yml` 的 `neo4j` 服务下增加一行，让 Docker 启动后自动拉起容器：

```yaml
restart: unless-stopped
```

---

## 四、导入图谱数据（可选但推荐）

空 Neo4j 也能连上后台，但「知识图谱管理」里几乎没有数据。需要把 `data/` 里的 CSV 导入。

### 4.1 安装 Python 依赖

**Windows PowerShell：**

```powershell
cd D:\SE\graph-db
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

若 `Activate.ps1` 被策略拦截，先执行（当前用户一次即可）：

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

**Mac / Linux：**

```bash
cd /path/to/graph-db
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 4.2 执行导入

**确保 Neo4j 已在运行**（第三节），然后：

```powershell
cd D:\SE\graph-db
.\.venv\Scripts\Activate.ps1
python scripts/import_artifacts.py --data-dir data
```

脚本结束会打印节点、关系等导入统计。  
在 Neo4j Browser 里可试：

```cypher
MATCH (n) RETURN labels(n)[0] AS label, count(*) AS cnt ORDER BY cnt DESC;
```

### 4.3 环境变量（一般不用改）

导入脚本默认连接：

```text
NEO4J_URI=bolt://localhost:7687
NEO4J_USER=neo4j
NEO4J_PASSWORD=password123
```

若改过密码，可在导入前设置：

```powershell
$env:NEO4J_PASSWORD="你的新密码"
python scripts/import_artifacts.py --data-dir data
```

### 4.4 清空图谱（慎用）

```powershell
python scripts/reset_graph.py
# 跳过确认：
python scripts/reset_graph.py --yes
```

---

## 五、对接 admin-backend 后台

### 5.1 确认配置

打开 `admin-backend/src/main/resources/application.yml`，确认：

```yaml
kg:
  neo4j:
    enabled: true
    uri: bolt://localhost:7687
    username: neo4j
    password: password123   # 必须与 docker-compose 一致
```

**密码改了的话，两边必须一起改。**

### 5.2 启动顺序（推荐）

```text
1. Docker Desktop → Running
2. Neo4j：cd graph-db && docker compose up -d
3. MySQL：确保 overseas_artifacts 库已建好
4. admin-backend：运行 AdminBackendApplication 或 mvn spring-boot:run
5. 浏览器：http://localhost:8080  登录 admin / 123456
6. 左侧菜单 → 「知识图谱管理」
```

### 5.3 验证连接是否成功

**方式 A：后台页面**

登录后进入「知识图谱管理」，能看到概览数字（实体数、三元组数等）即成功。

**方式 B：API（需先登录拿 JWT）**

```http
GET http://localhost:8080/api/admin/kg/status
Authorization: Bearer <token>
```

返回含 `"mode":"neo4j"` 和 `"enabled":true` 即可。

### 5.4 不需要图谱的同学

在 `application.yml` 中设置：

```yaml
kg:
  neo4j:
    enabled: false
```

则：

- 不启动 Neo4j 也能跑后台
- 「知识图谱管理」菜单/API 不可用
- 用户管理、审核、文物（MySQL）、备份、日志、看板 **不受影响**

---

## 六、数据存在哪里

| 路径 | 说明 |
|------|------|
| `graph-db/neo4j-data/` | Neo4j 数据库文件（**重要，别乱删**） |
| `graph-db/neo4j-logs/` | Neo4j 日志 |
| `graph-db/data/` | 待导入的 CSV 源数据 |
| MySQL `artifact` 表 | 文物业务数据（与 Neo4j **不自动同步**） |

换电脑时：拷贝整个 `graph-db` 文件夹（含 `neo4j-data`）或在新机器重新 `docker compose up` + 再导入 `data/`。

---

## 七、常见问题排查

### 7.1 `docker compose up` 报错 / 端口被占用

**现象：** `Bind for 0.0.0.0:7687 failed: port is already allocated`

**处理：**

```powershell
# 看谁占了 7687
netstat -ano | findstr 7687

# 若是旧容器
docker ps -a
docker stop overseas-artifacts-neo4j
docker rm overseas-artifacts-neo4j
cd D:\SE\graph-db
docker compose up -d
```

### 7.2 Docker Desktop 未启动

**现象：** `error during connect: open //./pipe/dockerDesktopLinuxEngine`

**处理：** 打开 Docker Desktop，等 Running 后重试。

### 7.3 后台图谱页报错 / 连接失败

**检查清单：**

1. `docker ps` 里 Neo4j 是否为 `Up`
2. http://localhost:7474 能否登录
3. `application.yml` 密码是否为 `password123`
4. 是否改了 `docker-compose.yml` 却没改 `application.yml`
5. 防火墙是否拦截本机 7687（一般不用改）

### 7.4 第一次 pull 镜像很慢

使用 Docker 镜像加速（Docker Desktop → Settings → Docker Engine），或换网络/热点。  
只需下载一次，之后本地有缓存。

### 7.5 导入脚本报连接错误

1. 确认 Neo4j 已 Up：`docker ps`
2. 等容器完全就绪（刚启动后等 10～30 秒）
3. 看日志：`docker logs overseas-artifacts-neo4j`

### 7.6 重启电脑后图谱「连不上」

正常情况：重启后容器不会自动起来 → 执行 `docker compose up -d`（见 3.6）。

---

## 八、给队友的一页速查（可直接转发）

```text
【只做后台、不要图谱】
  application.yml → kg.neo4j.enabled: false
  不用装 Docker / graph-db

【要用知识图谱管理】
  1. 安装 Docker Desktop，保持 Running
  2. 拿到 graph-db 代码，进入目录
  3. docker compose up -d
  4. 浏览器 http://localhost:7474  账号 neo4j / password123
  5. （可选）python -m venv .venv && pip install -r requirements.txt
     python scripts/import_artifacts.py --data-dir data
  6. admin-backend 的 application.yml 保持 bolt://localhost:7687
  7. 启动 admin-backend → http://localhost:8080 → 知识图谱管理

【每次开机】
  Docker Desktop → cd graph-db → docker compose up -d → 启动后台
```

---

## 九、与课程设计的关系

| 子系统 | 与 graph-db 关系 |
|--------|------------------|
| 知识图谱构建（子系统1） | 产出 CSV → 放入 `graph-db/data/` → `import_artifacts.py` 导入 Neo4j |
| 后台管理（子系统5） | 通过 `/api/admin/kg/**` **在线查看/编辑** Neo4j 中的实体与三元组 |
| MySQL `artifact` 表 | 文物列表/CSV 导入走 MySQL，与 Neo4j **需各自维护或靠构建组脚本同步** |

---

## 十、参考文件

| 文件 | 说明 |
|------|------|
| `graph-db/docker-compose.yml` | Neo4j 容器定义 |
| `graph-db/README.md` | 图库仓库说明 |
| `graph-db/docs/graph-database-design.md` | 图谱建模设计 |
| `graph-db/scripts/queries.cypher` | Browser 查询示例 |
| `admin-backend/application.yml` | 后台 Neo4j 连接配置 |
| `admin-backend/docs/subsystem5-requirements-implementation.md` | 需求与实现对照 |

# Aster · Web Produce

用对话生成页面，再直接点选画布修改。Aster 把「AI 生成」和「所见即所得编辑」放在同一个工作台里：第一版由 AI 快速搭出来，之后的文字、图片、颜色和布局调整不再反复重生成整页。

首版聚焦响应式单页营销官网 / 落地页，采用受控组件库和 `PageSpec` JSON，而不是任意 HTML 自由生成。

## 已实现

- 邮箱 + 密码注册/登录，Session + CSRF 防护
- 对话生成第一版页面（Mock AI 开箱可用，可切换 DeepSeek）
- AI 局部修改：改主题色、字号、图片、标题，或按指令隐藏区块
- 画布直接编辑：点选文字改文案，点选图片换图
- 属性面板：内容、样式、图片、布局四个维度
- 主题模式、强调色、字体、标题/正文字号、圆角、区块间距
- 桌面 / 平板 / 手机预览，画布自动缩放适配
- 撤销 / 重做、自动保存、乐观版本冲突提示
- 图片上传（PNG / JPG / WebP / GIF，最大 8 MB）
- 导出 `index.html + assets/` ZIP，复用同一套 React 渲染组件
- H2 本地文件库开发，PostgreSQL 生产部署
- Flyway 数据库迁移，Docker Compose 一键启动

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | React 19、TypeScript、Vite、Zustand、Immer、Zod、React Router |
| 后端 | Spring Boot 4.1、Java 21、Spring Security、Spring Data JPA、Flyway |
| 数据库 | H2（开发）/ PostgreSQL（生产） |
| AI | DeepSeek Chat Completions 兼容接口，本地 Mock 兜底 |
| 导出 | React DOM Server + JSZip，浏览器端打包 |

## 核心思路

```text
用户对话
   ↓
AI 输出 PageSpec JSON（或局部 JSON Patch）
   ↓
后端校验 / 版本管理 / 保存快照
   ↓
React PageRenderer 渲染组件
   ↓
组件注册表声明「哪些字段可编辑」
   ↓
画布点选 → 属性面板 / contenteditable → 更新同一份 PageSpec
```

- AI 不直接生成 HTML，只生成结构化的 `PageSpec`。
- 可编辑字段由组件注册表声明，不解析 DOM 来猜字段。
- 用户手工修改与 AI 修改共享同一份规格，但不会自动触发整页重生成。
- 静态导出复用 `PageRenderer`，避免编辑器和成品两套渲染逻辑。

## 本地开发

### 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20+
- 可选：Docker / PostgreSQL

### 方式一：前后端分开开发（推荐）

终端 1，启动后端（H2 文件库，端口 8080）：

```bash
mvn -Dskip.frontend=true spring-boot:run
```

Windows PowerShell 下属性参数需要加引号：

```powershell
mvn "-Dskip.frontend=true" spring-boot:run
```

终端 2，启动前端（Vite，端口 5173，已代理 `/api` 到 8080）：

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`。首次注册后会自动生成一个示例项目（`SEED_DEMO=false` 可关闭）。

H2 控制台：`http://localhost:8080/h2-console`
JDBC URL：`jdbc:h2:file:./data/webporduce`，用户名 `sa`，密码为空。

### 方式二：后端直接托管前端构建产物

```bash
cd frontend
npm install
npm run build
cd ..
mvn -Dskip.frontend=true spring-boot:run
```

前端构建输出到 `src/main/resources/static`，打开 `http://localhost:8080` 即可。

也可以直接执行完整 Maven 构建（会自动下载 Node、安装前端依赖并构建）：

```bash
mvn package
java -jar target/WebPorduce-0.0.1-SNAPSHOT.jar
```

### 启用真实 DeepSeek

默认 `app.ai.provider=mock`，无需密钥即可体验完整流程。配置后切换：

```bash
AI_PROVIDER=deepseek \
DEEPSEEK_API_KEY=sk-... \
DEEPSEEK_MODEL=deepseek-chat \
mvn -Dskip.frontend=true spring-boot:run
```

若请求失败，服务会自动回退到 Mock，并在回复中说明本次是本地演示模式。

## Docker + PostgreSQL

```bash
cp .env.example .env
# 编辑 .env，至少修改 POSTGRES_PASSWORD
docker compose up --build
```

打开 `http://localhost:8080`。

- PostgreSQL 数据保存在 `postgres_data` volume。
- 上传图片保存在 `uploads` volume。
- Flyway 会在启动时自动建表。
- 生产环境若走 HTTPS 反代，设置 `SESSION_COOKIE_SECURE=true`。

常用环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | 生产使用 `prod` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/webporduce` | 数据库地址 |
| `DB_USERNAME` / `DB_PASSWORD` | `webporduce` | 数据库账号 |
| `APP_STORAGE_ROOT` | `./data/uploads` | 上传文件目录 |
| `AI_PROVIDER` | `mock` | `mock` 或 `deepseek` |
| `DEEPSEEK_API_KEY` | 空 | DeepSeek 密钥 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | 兼容接口地址 |
| `DEEPSEEK_MODEL` | `deepseek-chat` | 模型名 |
| `SEED_DEMO` | `true` | 注册时是否生成示例项目 |
| `SESSION_COOKIE_SECURE` | `false` | HTTPS 环境设为 `true` |

## API 概览

认证接口需要携带 Session Cookie；所有非 GET 请求还需要请求头 `X-XSRF-TOKEN`。前端 `api.ts` 已自动处理。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查 |
| GET | `/api/auth/csrf` | 获取 CSRF Token / Cookie |
| POST | `/api/auth/register` | 注册并登录 |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/logout` | 退出 |
| GET | `/api/auth/me` | 当前用户 |
| GET | `/api/projects` | 项目列表 |
| POST | `/api/projects` | 新建项目 |
| GET | `/api/projects/{id}` | 项目详情 + 消息 |
| PATCH | `/api/projects/{id}` | 重命名 |
| DELETE | `/api/projects/{id}` | 删除 |
| PUT | `/api/projects/{id}/draft` | 保存草稿（带版本号） |
| POST | `/api/projects/{id}/messages` | 发送对话消息并应用 AI 修改 |
| POST | `/api/assets` | 上传图片 |
| GET | `/api/assets/{id}/content` | 读取图片内容 |

## 目录结构

```text
WebPorduce/
├─ src/main/java/com/xiaoliu/webporduce/
│  ├─ ai/          # Mock / DeepSeek Provider、Patch 应用
│  ├─ asset/       # 图片上传与读取
│  ├─ auth/        # 注册、登录、CSRF
│  ├─ chat/        # 对话、消息持久化、AI 编排
│  ├─ project/     # PageSpec、项目、版本、示例数据
│  ├─ security/    # Spring Security 配置
│  └─ user/        # 用户实体
├─ src/main/resources/
│  ├─ ai/          # 系统提示词与默认 PageSpec
│  ├─ db/migration/# Flyway 迁移
│  └─ application*.yaml
├─ src/test/       # 上下文 + API 冒烟测试
├─ frontend/
│  ├─ public/presets/  # 内置视觉素材
│  └─ src/
│     ├─ editor/       # ChatPanel / CanvasPanel / InspectorPanel
│     ├─ renderer/     # PageRenderer 与导出共用样式
│     ├─ store/        # Zustand 编辑器状态、自动保存、撤销重做
│     ├─ pages/        # 登录、工作台、编辑器
│     └─ lib/          # API 客户端、导出、工具函数
├─ Dockerfile
└─ docker-compose.yml
```

## 测试

```bash
mvn "-Dskip.frontend=true" test
```

`ApiSmokeTest` 会在随机端口启动完整应用，使用测试 H2 内存库，覆盖：

- 静态首页与内置素材可访问
- CSRF → 注册 → Session
- 新建项目 → 首次对话生成页面
- 保存草稿与乐观版本冲突（409）
- 未登录访问受保护接口返回 401

前端构建检查：

```bash
cd frontend
npm run build
```

## 当前边界

- 只支持受控组件库中的 6 类区块：导航、首屏、作品画廊、数据条、CTA、页脚。
- 暂不提供自由拖拽画布、任意 HTML、多人实时协作。
- 会话默认存储在应用内存中，重启后需要重新登录；多实例部署建议接入 Spring Session JDBC/Redis。
- 静态导出是客户端打包，导出 `assets/` 中的图片来自当前页面引用。
- 生产环境还应补充反向代理、HTTPS、备份、限流和对象存储。

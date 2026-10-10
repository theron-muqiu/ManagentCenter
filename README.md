# ManagentCenter 基础后台

前后端分离的基础管理网站，覆盖登录、本人改密、用户资料与启停、管理员重置密码、角色管理、用户角色分配、角色权限分配、操作记录和登录日志。暂不包含业务模块。

首次版本：`v0.1.0`。稳定版本维护在 `main`，日常开发集成在 `develop`。功能开发从 `develop` 创建 `feature/*` 分支，修复使用 `fix/*`，发布使用 `release/*`；生产紧急修复从 `main` 创建 `hotfix/*`，合并后同步回 `develop`。提交说明以中文为主，长期分支不强制推送。

技术栈：Java 21、Spring Boot 3.5.13 / Spring Security 6.5.9、Spring JDBC、Flyway 11.20.2、MySQL 8.4、Redis 7；Vue 3、TypeScript、Element Plus、Vite。25 个接口均以 `/api` 开头。

## 界面设计

控件优先使用 Element Plus：表格、分页、筛选、日期、菜单树、权限树、抽屉、账号下拉和选项卡均使用官方组件；全局中文、默认尺寸配置。表单控件使用官方默认外观，仅保留页面布局；菜单与权限树可展开、收起，权限父子节点独立勾选。

管理控制台采用深海军蓝导航、浅色工作区与蓝色主操作。登录、工作台、用户/角色、授权弹窗、操作/登录日志及错误页面共用统一视觉规范，详见 [界面设计规范](design-system/managent-center/MASTER.md)。

工作台根据独立查询权限展示实际用户/角色总数与最近日志，明确区分加载、读取失败、无权限和空数据。快捷入口来自后端授权菜单。列表支持查询、重置和分页；小屏使用抽屉导航，提供键盘焦点限制、Escape关闭、焦点归还及跳到主内容入口。

本次界面验证通过21项前端组件测试、TypeScript检查及生产构建。主操作、次级文字、侧栏文字与语义状态配色的静态对比度均超过4.5:1；未运行浏览器端到端或实际设备视觉测试。

## 后端目录

当前业务统一属于 `system` 模块，模块内按职责分层。后续业务模块可与 `system` 平级。

```text
com.muqiu.managentcenter/
├── ManagentCenterApplication.java
├── common/                通用响应、错误处理、JSON配置和校验
└── system/                系统管理模块
    ├── controller/        登录、用户、角色、权限、日志接口
    ├── service/           业务规则、事务及独立日志写入
    ├── repository/        数据库查询与修改
    ├── dto/               接口请求参数
    ├── vo/                接口返回数据
    ├── entity/            内部用户、权限数据
    ├── enums/             系统业务的固定整数枚举
    ├── constants/         预置权限定义
    ├── config/            认证、密码及初始化配置
    ├── security/          Token存储、认证过滤和权限判断
    ├── audit/             日志采集、安全上下文和提交信号
    └── init/              初始管理员及预置权限初始化
```

系统模块测试位于 `backend/src/test/java/com/muqiu/managentcenter/system/`。目录调整不改变接口URL、权限代码、数据库表名或前端页面路由。

## 本地启动

需要 Java 21、Maven 3.9、Node.js 22、pnpm 11，以及 MySQL 8.4 / Redis 7。复制 `deploy/.env.example` 为根目录 `.env`，填写独立随机数据库、Redis、初始化管理员密码。不得提交真实凭据。`DB_USERNAME/DB_PASSWORD` 应与 `MYSQL_USER/MYSQL_PASSWORD` 一致。

可用依赖配置创建本地数据库与 Redis：

```sh
docker compose --env-file .env -f deploy/compose.yml up -d
```

依赖只监听本机，示例默认 MySQL 13316 / Redis 13379，可用 `.env` 中的 `MYSQL_PORT` / `REDIS_PORT` 调整；MySQL 端口需同时更新 `DB_URL`。当前生成的本地 `.env` 使用13317/13380，避开已有验证容器。如果端口已有服务，请调整配置，勿停止或覆盖现有数据库。数据放在具名卷内，`down` 保留数据，勿随意执行带卷删除参数的命令。

将 `.env` 的变量注入后端进程。在 Bash/WSL 中，可使用 `set -a; source .env; set +a`；PowerShell 使用进程环境变量或 IDE 环境配置。示例文件适用于 Bash，PowerShell 不应直接执行该文件。敏感值不要放入命令参数或终端输出。

```sh
cd backend
mvn spring-boot:run
```

后端默认监听 127.0.0.1:8080。Flyway 自动创建 7 张表；首次启动初始化 15 个固定权限和超级管理员。初始化账号仅创建一次，再次启动不覆盖密码、资料或状态；首次创建管理员必须由环境提供有效初始化密码。账号区分大小写且创建后不可修改。手机号/邮箱选填、不要求唯一，仅作联系资料。

另开终端：

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

打开终端显示的本地地址，使用配置的管理员账号登录。当前 Vite 将 `/api` 转发到本机 28080；后端默认端口为8080，启动时需将后端端口配置为28080，或修改 `frontend/vite.config.ts` 的代理端口与后端一致。刷新深层页面由开发服务器处理。

## 验证与构建

后端集成测试必须连接专用 `/rbac_test` 数据库和专用 Redis，提供相同 DB、REDIS、AUTH、BOOTSTRAP 环境变量。测试包含事务故障和并发，不可对业务库运行。缺少依赖不会自动跳过。Windows 默认 Java 8 无法构建本项目，需设置 Java 21 或使用 WSL Java 21。

```sh
cd backend
mvn test
mvn -DskipTests package
```

```sh
cd frontend
pnpm test
pnpm build
```

后端产物在 `backend/target/`，前端产物在 `frontend/dist/`。前端构建包含 TypeScript 检查。验证范围为真实数据库/Redis 集成与前端组件测试，不运行浏览器端到端测试。

## 认证、权限与日志

登录返回随机不可解释 Bearer Token；Redis 仅保存 Token 的 SHA-256 键和会话资料，必须配置正数 `AUTH_TOKEN_TTL`，示例为两小时。前端在内存和 sessionStorage 保存凭据，请求只使用 Authorization 头，不使用 JWT、Cookie 或数据库会话表。禁止在 URL 中传递凭据。

改密、重置密码和启用转停用在数据库事务中增加认证版本，使该用户全部旧登录失效；重新启用不会恢复旧 Token。每次请求读取最新用户及角色权限，角色权限并集即时生效；默认拒绝。超级管理员拥有固定全部权限且不可停用。有任意用户关联（含停用用户）的角色不可删除。

两类日志各有独立全平台查看权限。十个关键写接口记录操作历史，登录请求记录登录历史；查询、退出及初始化不采集。事务提交决定成功结果，已提交而响应失败仍记为成功。日志独立事务写入超时为两秒；失败只记安全 `AUDIT_WRITE_FAILED` ERROR，不阻断原业务，不保证零遗漏。历史不依赖当前对象存在，不保存密码、凭据或完整联系方式。示例告警见 `deploy/audit-alerts.example.yml`，需接入实际日志平台及告警路由。

## 生产运行配置

`deploy/nginx.conf.example` 提供同源 HTTPS、SPA 路由与安全请求头模板。替换域名、证书和路径，将 dist 放入静态目录；后端 8080 仅允许代理访问。代理跨主机时用 `SERVER_ADDRESS` 指定后端私网接口并限制入站来源。应用 `TRUSTED_PROXIES` 只填写连接后端的实际代理精确 IP，未配置时忽略来源头。多层代理需同步代理链策略，不能信任任意客户端头。

Redis 应使用独立实例或账号、私网访问和认证；生产建议 ACL 账号仅授权 `rbac:token:*` 及实际所需 GET/SET/DEL 命令，跨主机链路应通过受控加密网络或 TLS 代理。示例本地依赖启用 AOF，但恢复旧备份可能复活退出过的 Token：**恢复后先暂停认证流量、在恢复实例清空 `rbac:token:*` 命名空间，再开放流量，要求全部重新登录**。使用 SCAN 分批匹配删除，不对共享 Redis 执行 FLUSHALL。所有节点同步时钟并使用 UTC，禁止通过增加 TTL 来绕过恢复步骤。

数据库备份恢复同样应清空认证命名空间，避免认证版本倒退。数据库与 Redis 凭据通过环境或平台密钥服务注入，使用最小权限；保留 Flyway 迁移所需权限或由独立迁移流程执行。运行日志禁止采集请求体、Authorization、完整 Token 和联系人字段。Nginx 示例访问日志排除查询串及请求头；独立请求级错误日志已关闭，监控应使用安全访问状态、后端运行日志与审计写失败告警。

需求、设计、任务与实现记录仅保留在本地 `docs/` 目录，不纳入版本管理。代码已完成首次提交及版本标签发布，生产部署需另行配置。

## 项目标识

项目名为ManagentCenter，Java包为com.muqiu.managentcenter，启动类为ManagentCenterApplication，后端产物为managent-center-0.1.0-SNAPSHOT.jar，前端包为managent-center-frontend。IDE项目及模块名已同步。当前工作区物理目录仍使用原路径；Compose资源标识保留management-control-local以复用已有MySQL/Redis容器和数据卷。

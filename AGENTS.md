# 项目协作规范

## 项目概况

- 本仓库是 Java 8 Maven 多模块项目，父 `pom.xml` 统一管理依赖版本和 `dev`、`local` 环境。
- `module/`：Spring Boot 主模块；源码和资源位于 `src/main`，测试位于 `src/test`。
- `leetcode-editor/`：LeetCode 代码；`tool-hub/`：Vue 3 前端；`py/`：FastAPI 后端。
- Java 使用 4 空格缩进；类名用 `PascalCase`，方法和字段用 `camelCase`，常量用 `UPPER_SNAKE_CASE`。
- `module` 已使用 Lombok，优先用 Lombok 和构造器注入，避免手写样板构造器、Getter、Setter。
- 提交信息遵循 Conventional Commits，如 `feat(post): add comment api`。

## 构建与测试

在仓库根目录执行 Maven；若 `mvn` 不在 `PATH`，使用 `D:\app\apache-maven-3.6.3\bin\mvn.cmd`。

```powershell
mvn -Plocal -pl module -am "-Dtest=HttpUtilTest,DeepSeekClientTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -Plocal -pl module -am "-Dtest=DeepSeekApiIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -Plocal -pl module -am -DskipTests package
mvn -pl leetcode-editor -am test
mvn -DskipTests package
```

- `module` 的 `application.yml` 使用 Maven 占位符 `@profile.active@`；执行 `module` 测试或构建时必须启用 `-Plocal`，不要直接运行未指定 Profile 的 Maven 命令。
- 默认只运行本次改动涉及的定向测试，通过 `-Dtest=TestClass1,TestClass2` 指定测试类；除非用户明确要求，否则不要运行 `module` 全量测试。
- PowerShell 会解析 `-Dtest` 中的逗号，指定多个测试类时需将整个 `-Dtest=...` 参数放在引号内。
- 测试使用 JUnit 5，放在对应模块的 `src/test/java`，类名以 `*Test` 结尾。
- 单元测试应快速、聚焦；仅在必要时添加集成测试。
- 测试中可使用 `@Slf4j` 输出关键结果。交付时说明执行过的命令和结果。

## Java 与 MyBatis-Plus

### 基础约束

- 当前基线：Spring Boot `2.7.18`、MyBatis-Plus `3.5.17`、PostgreSQL。
- 保留父 POM 的 BOM 版本和 `mybatis-plus-jsqlparser-4.9` 兼容依赖，不要单独升级其中一个组件。
- Mapper 扫描和拦截器统一维护在 `MybatisPlusConfiguration`，不要重复创建配置 Bean。
- 拦截器顺序：SQL 改写类在前，分页和乐观锁居中，分析或防攻击类在后。
- 持久化统一使用 MyBatis-Plus，不要引入或继续使用 `JdbcTemplate`。

### 分层与模型

- Controller 请求参数使用明确的 DTO/Query/Command 类，不使用 `Map<String, Object>`。
- Service 使用 Lombok 构造器注入；业务规则、事务编排和 DTO 转换放在 Service 或 Repository/Store。
- 通用审计字段抽到基础实体；时间字段使用 `OffsetDateTime`，数值可空字段优先使用包装类型。
- 分页参数和结果复用公共 `PageQuery`、`PageResult`，在 API 边界校验页码并限制每页大小。
- 普通下划线字段依赖 `map-underscore-to-camel-case: true`，不要重复写无意义的 `@TableField`。
- 实体明确声明 `@TableName`、`@TableId`；主键策略必须与数据库真实生成方式一致。
- `@TableField` 只用于特殊映射、非持久化字段、自动填充或类型处理器；结果也需类型处理器时启用 `autoResultMap`。
- 删除继续使用领域 `status`，未经整体迁移和测试不要单独引入 `@TableLogic`。

### Mapper 与查询

- Mapper 接口继承 `BaseMapper<Entity>`，只保留方法声明和必要的 `@Param`。
- 自定义 SQL 统一放在 `module/src/main/resources/mapper/` 的对应 XML；禁止在 Mapper 中使用 SQL 注解、Provider 或注解内 `<script>`。
- 单表常规查询优先使用 `LambdaQueryWrapper`、`LambdaUpdateWrapper`，不要用字符串字段名。
- 联表、聚合、投影、锁、PostgreSQL 特性和原子状态迁移使用 Mapper XML。
- Wrapper 必须在执行方法内创建，不缓存、不共享、不复用，也不暴露给 Controller 或公共服务接口。
- 可选条件使用 Wrapper 的条件重载；更新、删除或权限查询前必须拒绝空 ID、空集合和空归属条件。
- `selectOne` 仅用于数据库约束保证唯一的条件，禁止用任意 `LIMIT 1` 掩盖重复数据。
- 列表查询只取需要的列，分页必须有稳定排序；禁止循环调用 Mapper，避免 N+1。

### SQL 安全、事务与并发

- 不拼接用户输入到 SQL、列名或排序片段；客户端排序键必须经过后端白名单映射。
- 不向 `apply`、`last`、`having`、`inSql`、`setSql`、`orderBy` 等方法传入不可信文本。
- XML 参数使用 `#{}`；`${}` 仅允许固定且经过白名单验证的 SQL 结构。
- 请求 DTO 不得直接传给 `insert`、`updateById` 或 `saveOrUpdate`，只映射允许修改的字段。
- 所有更新和删除都要有明确业务条件，并检查影响行数；零行更新不能默认视为成功。
- 乐观锁实体声明并初始化 `@Version`；状态迁移在 `WHERE` 中校验旧状态或旧版本，并原子更新版本。
- 计数、库存、抢占和状态变更使用单条条件 SQL，不采用先查再改。
- 多表写入和批处理放在公开的 `@Transactional` Service 方法中；不要依赖同类自调用开启事务。
- 大集合使用分批处理并限制批次大小；需要原子性时显式开启事务。


## Tool Hub 前端

- `tool-hub/` 使用 Vue 3、Vite、Vuetify；API 统一通过 `src/utils/request.ts`。
- 页面放在 `src/views` 并按领域分组；路由及导航元数据统一维护在 `src/router/index.ts`。
- 仅需要登录的页面设置 `requiresAuth: true`；公开只读页面保持公开。
- 导航分组在 `AppNavigation.vue` 中维护；新页面补充 `title`、`description`、`icon`、`keywords`、`featured`。
- 优先使用 Vuetify、Material Design Icons 和成熟组件，不手写已有组件能覆盖的基础交互。
- 样式使用 `src/main.css` 的颜色、间距、圆角、阴影变量和公共类，禁止硬编码颜色或创建一次性视觉体系。
- 保持内部工具风格：信息紧凑、层级清楚、边框克制、操作直接，避免营销式首屏和装饰性渐变。
- 工具页优先使用 `ToolPageLayout`；布局已显示标题时不要重复写 `<h1>`，全屏工作区使用 `:card="false"`。
- 避免会影响 Vuetify 的全局 `button`、`input`、`textarea` 样式。
- 股票图表、编辑器、地图、拖拽、虚拟表格等复杂组件优先采用成熟库并遵循领域惯例。
- 前端只做静态检查、单元测试和 `npm run build`；不要运行浏览器自动化、打开 localhost 或截图验收。

## Python 后端

- `py/` 是 FastAPI 应用，本地命令先执行 `conda activate ai`。
- 新功能按领域放在 `py/<domain>/`，通常包含 `schemas.py`、`service.py`、`store.py`、`routes.py`。
- 路由工厂使用 `create_router(container)` 并在 `py/app.py` 注册；鉴权复用现有 `auth.routes` 模式。
- API 响应尽量复用 `auth.schemas.ApiResponse`；配置放在 `py/core/settings.py`，从环境变量或 `py/.env` 读取。
- 部署所需依赖必须加入 `py/requirements.txt`，不能只修改本地环境文件。
- 修改后至少对相关文件运行 `python -m py_compile`；条件允许时执行应用实例化检查。

## Agent 后端边界

- `py/agent_chat/` 负责 `/api/v1/agent/*` 聊天入口、路由、模型回退、Skill 调度、Trace 和响应整形。
- `py/agent_eval/` 负责运行记录、查询、反馈、评测用例、重试/取消和指标持久化。
- `py/skills/` 只保存可复用工作流说明，运行时编排仍放在 `agent_chat`。
- 不要恢复已删除的 `py/agent_runtime/`、`py/project_agent/`、`py/langchain_examples/`，也不要新增 `/api/v1/project-agent/*`。
- Agent 工具保持小而明确，并统一输出 `step_id`、`node`、`status`、输入/输出摘要、耗时、错误和工具名。
- Agent 工作台保持聊天优先，使用真实流式接口，不添加模拟输入或模板式假交互。

## 数据库与迁移

- 新的持久化业务优先使用 PostgreSQL、SQLAlchemy 2.0 ORM 和 Alembic。
- 公共数据库设施放在 `py/db/`，模型归属各领域，迁移放在 `py/alembic/versions/`。
- 业务表使用领域前缀，如 `post_`；系统账户和权限表使用 `sys_`；索引和约束同样使用领域前缀。
- 时间字段使用 `TIMESTAMPTZ`；可变业务表包含 `created_at`、`updated_at`；用户内容优先用状态软删除。
- 数据库连接读取 `POSTGRES_DSN`，密码保留字符需 URL 编码；Compose 内使用容器名和内部端口连接。
- Schema 变化使用 Alembic，不只维护 `py/sql/` 参考脚本；同时更新部署连接和依赖配置。

```powershell
conda activate ai
cd py
python -m alembic upgrade head
python -m alembic current
```

## 部署与协议

- Python Docker 镜像从 `py/requirements.txt` 安装依赖，新增运行时组件时同步更新该文件。
- WebSocket/SSE 等协议需要同步检查依赖、前端请求和容器日志；Uvicorn WebSocket 必须具备对应运行时依赖。

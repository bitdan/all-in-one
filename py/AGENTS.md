# FastAPI Backend Guidelines

本文件适用于 `py/`。同时遵守仓库根目录 `AGENTS.md`。

## 技术栈与组织

- FastAPI 应用入口为 `app.py`，依赖装配集中在现有 bootstrap/container 结构。
- 新功能按领域放在 `py/<domain>/`，通常包含 `schemas.py`、`service.py`、`store.py`、`routes.py`。
- 路由使用 `create_router(container)` 工厂并在 `app.py` 注册；鉴权复用 `auth.routes` 和现有依赖模式。
- API 响应优先复用 `auth.schemas.ApiResponse`，不要为等价响应重复定义包装结构。
- 配置集中在 `core/settings.py`，从环境变量或 `py/.env` 读取；不得在源码中写死环境配置。
- 部署依赖必须加入 `requirements.txt`，并同步检查 Dockerfile、Compose 和启动脚本。

## 分层边界

- Route 负责协议、参数校验和响应整形；Service 负责编排和业务规则；Store 负责持久化和外部存储访问。
- Pydantic Schema 明确表达请求和响应，不使用无约束字典代替稳定业务模型。
- 公共数据库设施放在 `db/`，跨领域通用配置放在 `core/`，通用纯逻辑放在 `common/`。
- 保持依赖从接口层流向业务层和基础设施层；避免 Store 反向依赖 Route。

## Agent 后端边界

- `agent_chat/` 负责 `/api/v1/agent/*` 聊天入口、路由、模型回退、Skill 调度、Trace 和响应整形。
- `agent_eval/` 负责运行记录、查询、反馈、评测用例、重试、取消和指标持久化。
- 可复用工作流说明归 `skills/`；运行时编排仍留在 `agent_chat/`。
- 不恢复 `agent_runtime/`、`project_agent/`、`langchain_examples/`，也不新增 `/api/v1/project-agent/*`。
- Agent 工具保持小而明确，并统一输出 `step_id`、`node`、`status`、输入/输出摘要、耗时、错误和工具名。
- Agent 工作台使用真实流式接口，不添加模拟输入或模板式假交互。

## 数据库与迁移

- 新的持久化业务优先使用 PostgreSQL、SQLAlchemy 2.0 ORM 和 Alembic。
- 模型归属各领域，公共 Session/Engine 设施放 `db/`，迁移放 `alembic/versions/`。
- 业务表使用领域前缀，如 `post_`；系统账户和权限表使用 `sys_`；索引和约束使用相同领域前缀。
- 时间字段使用 `TIMESTAMPTZ`；可变业务表包含 `created_at`、`updated_at`；用户内容优先状态软删除。
- 数据库连接读取 `POSTGRES_DSN`；密码保留字符需要 URL 编码；Compose 内使用容器名和内部端口。
- Schema 变化使用 Alembic，不只维护 `sql/` 参考脚本；同步更新 ORM 模型、部署连接和依赖配置。
- 批量写入先完成整批校验再落库；多表修改使用显式事务，避免部分成功。

## 协议与部署

- WebSocket、SSE 等协议变化要同步检查后端依赖、前端请求、反向代理和容器日志。
- Uvicorn WebSocket 必须具备对应运行时依赖；不能只修改本地环境而遗漏 `requirements.txt`。
- 流式响应保持事件格式和错误语义稳定；变更前使用 `rg` 查找前端及测试的所有消费者。

## 验证

本地命令先激活环境：

```powershell
conda activate ai
cd py
python -m py_compile path\to\changed_file.py
```

- 修改后至少对所有相关 Python 文件运行 `python -m py_compile`。
- 条件允许时执行应用实例化检查，并运行与领域对应的 `pytest` 测试文件；不默认运行整个测试集。
- 数据库迁移任务使用：

```powershell
conda activate ai
cd py
python -m alembic upgrade head
python -m alembic current
```

- 只有任务涉及迁移且数据库环境可用时才执行 Alembic 命令；否则静态核对 migration 的 upgrade/downgrade、模型和约束，并说明未执行原因。

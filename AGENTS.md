# Repository Guidelines

本文件定义仓库级协作规则。各模块的技术细则放在对应目录的 `AGENTS.md` 中；处理某个文件时，只需同时遵守本文件和离该文件最近的模块规则。

## 指令分层

- 根目录 `AGENTS.md`：仓库地图、跨模块边界、通用工作流和交付要求。
- `module/AGENTS.md`：Spring Boot、MyBatis-Plus、PostgreSQL 和 Maven 验证规则。
- `leetcode-editor/AGENTS.md`：算法代码的组织、实现和测试规则。
- `tool-hub/AGENTS.md`：Vue 3、Vite、Vuetify 和前端交互规则。
- `py/AGENTS.md`：FastAPI、Agent、SQLAlchemy、Alembic 和 Python 验证规则。
- 规则冲突时，用户当前明确要求优先，其次是离目标文件最近的 `AGENTS.md`，最后是本文件。
- 默认只读取和需求直接相关的模块规则；跨模块任务再补读相关模块，不要无目的地展开整个仓库。

## 仓库地图

- 根 `pom.xml`：Java 8 Maven 父工程，统一管理 `module`、`leetcode-editor`、依赖版本及 `dev`、`local` Profile。
- `module/`：Spring Boot 2.7 主模块，包含业务接口、持久化、集成能力和测试。
- `leetcode-editor/`：独立的 Java 算法题模块。
- `tool-hub/`：Vue 3 工具前端；目录本身是独立 Git 工作区，检查改动时在该目录内执行 Git 命令。
- `py/`：FastAPI 后端，包含认证、社区、游戏、Agent 聊天与评测等领域。
- `skills/`、`notes/`、`script/`：工作流说明、项目笔记和辅助脚本；除非需求直接涉及，不要顺手整理。

## 模块边界

- 功能放到拥有该业务的模块，不跨模块复制实现。Java 公共能力放 `module` 的公共包，Python 公共设施放 `py/common`、`py/core` 或 `py/db`，前端共享逻辑放现有 `components`、`composables`、`hooks`、`stores`、`utils`。
- `module` 与 `py` 是两个独立后端边界。先确认接口归属，再修改对应 Controller/Route、Service 和持久化层。
- `tool-hub` 只通过既有 API 层访问后端，不在页面组件中散落请求实现。
- 不恢复已删除的 `py/agent_runtime/`、`py/project_agent/`、`py/langchain_examples/`，也不新增 `/api/v1/project-agent/*`。
- 依赖或基础设施升级必须保持兼容组合，不单独升级强耦合组件。

## 工作方式

1. 先定位入口文件、直接调用点和已有测试，只检查与需求有关的范围。
2. 修改前确认目标工作区是否已有用户改动；保留无关变更，不覆盖、不回滚。
3. 优先复用当前模块的模型、组件和基础设施，再新增最小必要实现。
4. 接口或数据结构变化时，同步核对生产者、消费者、类型定义、持久化映射和测试。
5. 默认执行最小范围验证；不要为了“保险”运行全仓构建、全量测试或浏览器自动化。

## API 与数据契约

- 后端 Controller/Route 及其 DTO、Schema 是接口契约源；前端 API 类型、查询参数和展示字段必须与之对齐，不能根据旧页面猜字段。
- 修改接口路径、请求字段、响应字段或流式协议前，使用 `rg` 查找全部调用点，并逐一核对直接调用、封装调用和测试。
- 请求参数使用明确类型，不使用无约束的键值对象代替 DTO/Schema。
- 区间、集合、可空值和文件列表在各层保持一致语义；空值归一化后再发往后端。
- 数据库 Schema 变化必须通过所属技术栈的正式迁移机制完成，并同步模型、部署配置和依赖。

## 通用质量与安全

- Java 使用 4 空格；类名 `PascalCase`，方法和字段 `camelCase`，常量 `UPPER_SNAKE_CASE`。前端和 Python 遵循各模块既有格式。
- 提交信息遵循 Conventional Commits，例如 `feat(post): add comment api`。
- 不在源码、`application-*.yml`、`.env*` 或示例中提交真实密钥、账号、密码和生产地址。
- 不拼接不可信输入到 SQL、命令、文件路径或排序表达式；对动态结构使用后端白名单。
- 新增运行时依赖时同步更新所属模块的清单和部署构建文件。

## 验证与交付

- Java 主模块：遵守 `module/AGENTS.md`，执行带 `-Plocal` 的定向测试。
- LeetCode 模块：遵守 `leetcode-editor/AGENTS.md`，仅验证相关题目或模块。
- 前端：遵守 `tool-hub/AGENTS.md`；默认仅静态核对，不启动本地服务、不做截图或浏览器验收。
- Python：遵守 `py/AGENTS.md`；至少对修改文件执行语法编译检查。
- 交付时说明修改文件、行为变化、执行过的验证及结果；未执行的高价值验证也要明确说明原因。

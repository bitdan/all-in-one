# Repository Guidelines

## Project Structure & Module Organization

This is a Maven multi-module Java repository. The parent `pom.xml` defines shared dependency versions and profiles.

Key paths:

- `module/src/main/java` and `module/src/main/resources`: primary Spring Boot module code and resources.
- `module/src/test/java`: tests for the `module` module.
- `leetcode-editor/src/main/java`: LeetCode solution implementations.
- `script/`, `py/`, `tool-hub/`: helper scripts and tooling (not part of the main build).

## Build, Test, and Development Commands

Use Maven from the repo root. On this machine Maven is installed at `D:\app\apache-maven-3.6.3`; if `mvn` is not on
`PATH`, run `D:\app\apache-maven-3.6.3\bin\mvn.cmd` instead.

1. `mvn -pl module -am clean package` — build the main module and its dependencies.
2. `mvn -pl module -am test` — run tests for the main module.
3. `mvn -pl leetcode-editor -am test` — run tests (if any) for the LeetCode editor module.
4. `mvn -DskipTests package` — full build without tests.

## Coding Style & Naming Conventions

- Language level: Java 8 (`maven.compiler.source/target` set to 8).
- Indentation: 4 spaces, no tabs.
- Packages follow reverse-domain naming (e.g., `com.linger...`).
- Class names: `PascalCase`; methods/fields: `camelCase`; constants: `UPPER_SNAKE_CASE`.
- Lombok is used in the `module` module—prefer Lombok annotations over boilerplate.

## Testing Guidelines

- Testing stack: Spring Boot Test (JUnit 5 via `spring-boot-starter-test`).
- Place tests under `module/src/test/java` and name them `*Test`.
- Keep unit tests fast; add integration tests only when needed.
- Use `@Slf4j` in tests and log key assertions/results so test runs emit useful output.
- No explicit coverage threshold is configured.

## Commit & Pull Request Guidelines

Commit history follows Conventional Commits with scopes, for example:

- `feat(redisson): add like service`
- `refactor(pdf): optimize text removal`

For PRs:

1. Provide a clear description of the change and rationale.
2. Link related issues or requirements.
3. Include test evidence (commands run and results).

## Configuration & Profiles

The parent POM defines `dev` and `local` profiles. Use them for environment-specific overrides when needed (e.g.,
`mvn -Pdev test`).

## MyBatis-Plus Guidelines

These rules apply to persistence work in the `module` module.

### Project Baseline

- The parent POM manages MyBatis-Plus `3.5.17`; the module runs on Java 8, Spring Boot `2.7.18`, and PostgreSQL.
  Preserve the BOM-managed version and the existing `mybatis-plus-jsqlparser-4.9` compatibility dependency. Do not
  upgrade the starter, BOM, or JSqlParser artifact independently.
- Keep shared interceptor and mapper scanning configuration in
  `module/src/main/java/com/linger/module/config/MybatisPlusConfiguration.java`. Add new mapper packages to its
  `@MapperScan` rather than creating competing configuration beans.
- The configured interceptor chain already enables optimistic locking and PostgreSQL pagination. When adding
  interceptors, follow the MyBatis-Plus ordering rule: SQL-transforming interceptors such as tenant or dynamic-table
  handling first, pagination and optimistic locking next, and analysis or attack-blocking interceptors last. Add an
  interceptor only with focused tests for affected queries.

### Entities and Mappers

- Mapper interfaces should extend `BaseMapper<Entity>`. Keep business rules, transaction orchestration, and DTO
  conversion in a repository/store or service; do not pass MyBatis-Plus `Wrapper` objects through controllers, RPC
  boundaries, or public service contracts.
- Keep Mapper interfaces declarative: custom methods may retain `@Param`, but SQL belongs in a matching XML mapper
  under `module/src/main/resources/mapper/`. Do not use `@Select`, `@Insert`, `@Update`, `@Delete`, provider
  annotations, or annotation-embedded `<script>` SQL in Mapper interfaces.
- Declare `@TableName` and `@TableId` explicitly. Match the real schema and its key strategy: use `IdType.INPUT` only
  for application-assigned IDs and `IdType.AUTO` only for database-generated identity columns.
- Rely on the configured underscore-to-camel mapping for ordinary columns. Use `@TableField` for an actual mapping
  difference, a non-persistent property, field fill, or a type handler. When a field type handler must also apply to
  query results, use `@TableName(autoResultMap = true)` as the existing JSON-list mapping does.
- Follow the owning domain's audit convention. Group-buy entities use `OffsetDateTime` in UTC plus `@TableField(fill =
  ...)` and `GroupBuyAuditMetaObjectHandler`; keep field names and Java types aligned with the handler. Remember that
  wrapper-only updates with a null entity do not trigger automatic update fill, so set audit columns explicitly in
  that case.
- This repository currently models deletion with domain `status` values such as `deleted`. Do not introduce global
  logic-delete settings or `@TableLogic` for one entity unless the schema, uniqueness rules, existing custom SQL, and
  all read/write paths are migrated and tested together.

### Queries and SQL Safety

- Prefer `LambdaQueryWrapper` and `LambdaUpdateWrapper` for ordinary entity fields so refactors remain type-safe. Use
  string-column `QueryWrapper` or custom mapper XML only when SQL-specific features such as PostgreSQL `ILIKE`, aliases,
  joins, aggregates, projections, locking, or atomic state transitions make them clearer.
- Build wrappers inside the method that executes them. They are mutable, are not thread-safe, and must not be cached,
  shared, or reused; in particular, a wrapper passed to `update(entity, wrapper)` cannot be reused.
- Use conditional wrapper overloads for optional filters. Validate identifiers and collections before construction:
  MyBatis-Plus omits conditions such as `in` for an empty collection, which must never accidentally broaden an update,
  delete, or authorization query.
- Never concatenate request data into column names or SQL fragments. Map client-provided sort/filter keys through a
  backend allowlist. Do not pass untrusted text to `apply`, `last`, `having`, `inSql`, `notInSql`, `eqSql`, `setSql`,
  `orderBy`, or `groupBy`; use wrapper-bound values, `apply("... {0}", value)`, or MyBatis `#{...}` parameters. In XML,
  reserve `${...}` for fixed, allowlisted structural SQL only.
- `selectOne` is for predicates guaranteed unique by a database constraint. Do not hide duplicate-data bugs with an
  arbitrary limit. If `last(...)` is genuinely required, its SQL must be a compile-time constant and never contain
  user input.
- Select only the columns needed for large rows or list endpoints, use deterministic ordering for pagination, and avoid
  mapper calls inside loops. Use a join, a bounded bulk query, or a batch method to prevent N+1 access patterns.

### Updates, Concurrency, and Transactions

- Every update or delete must have an explicit, validated business condition. Treat missing IDs, empty ID collections,
  and blank ownership/tenant keys as errors before building the wrapper. Check the affected-row count; do not silently
  treat zero-row writes as success.
- Do not pass request-bound entities directly to `insert`, `updateById`, or `saveOrUpdate`. Validate a command/DTO and
  map only allowed fields so clients cannot write IDs, ownership, status, version, or audit fields unintentionally.
- Entities using optimistic locking must declare and initialize `@Version`. For `updateById` or
  `update(entity, wrapper)`, treat an affected-row count of zero as a stale write and handle it explicitly. Custom SQL
  that performs a guarded state transition must include the old version or old status in the `WHERE` clause and update
  the version atomically.
- Prefer a single conditional SQL statement for counters, inventory, claims, and state transitions instead of a
  read-then-write sequence. Preserve the existing group-buy rule that state changes use dedicated SQL with an old-state
  condition.
- Put multi-table writes and MyBatis batch work behind a public Spring `@Transactional` service boundary. Do not assume
  self-invocation starts a transaction. MyBatis batch APIs require explicit transaction handling and their results or
  flush failures must be checked.

### Pagination, Batch Work, and Verification

- Validate `current >= 1` and cap `size` at the API boundary; do not trust arbitrary client page sizes. Use
  `searchCount = false` only when the caller does not need a total. For custom paginated SQL, pass a non-null `IPage`
  parameter and verify joins and aliases produce a correct count query.
- Use batch APIs for substantial collections instead of one mapper call per item. Split very large inputs into bounded
  chunks, keep the operation transactional when atomicity is required, and do not assume batch processing enables a
  transaction automatically.
- For mapper or entity changes, add a focused test that exercises generated SQL against the repository's PostgreSQL
  behavior where practical. Cover mapping/type handlers, empty optional filters, pagination bounds, zero-row guarded
  updates, optimistic-lock conflicts, and soft-deleted rows as applicable. At minimum run
  `mvn -pl module -am test`; use `MybatisPlusConfigurationTest` when changing interceptor configuration.

## Tool Hub Frontend Guidelines

The `tool-hub/` app is a Vue 3 + Vite + Vuetify frontend.

- API wrappers live under `tool-hub/src/api`. Use the existing `tool-hub/src/utils/request.ts` axios wrapper so
  authentication headers, cookies, timeout handling, and global error behavior stay consistent.
- Feature pages live under `tool-hub/src/views`. Group larger product areas by domain, such as
  `tool-hub/src/views/community`.
- Register navigable pages in `tool-hub/src/router/index.ts`. Add `requiresAuth: true` only for pages that actually
  require login; keep read-only public pages public when possible.
- The side navigation is built from grouped router records in `tool-hub/src/components/AppNavigation.vue`. Add new
  top-level product areas as route groups, then include that group in the navigation filter.
- Prefer Vuetify components and Material Design Icons (`mdi-*`) for controls. Keep pages usable as the first screen;
  do not add marketing-style landing pages for internal tools or app features.
- Product style should feel like a polished internal tool hub: quiet, fast to scan, and immediately actionable. Prefer
  dense but orderly layouts, restrained borders, subtle shadows, and clear hierarchy over decorative card-heavy pages.
- Keep the established Tool Hub visual language consistent: use the shared CSS tokens in `tool-hub/src/main.css`,
  8px radii for cards and panels, neutral slate surfaces, blue primary actions, and small tonal accents for status or
  category cues. Avoid one-off palettes, oversized hero typography inside tool pages, and purely ornamental gradients.
- **Design tokens (`tool-hub/src/main.css`)** — all custom CSS must reference these variables instead of hardcoded hex
  colors. The token file also defines shared utility classes for common patterns.

  **Color tokens:**
  | Variable | Value | Usage |
  |---|---|---|
  | `--color-primary` | `#2563eb` | Primary actions, links, focus rings |
  | `--color-primary-light` | `#dbeafe` | Primary tint backgrounds (badges, pills) |
  | `--color-primary-dark` | `#1d4ed8` | Active/hover states |
  | `--color-surface` | `#ffffff` | Card and panel backgrounds |
  | `--color-surface-elevated` | `rgba(255,255,255,0.7)` | Glass card backgrounds |
  | `--color-bg` | `#f8fafc` | Page background |
  | `--color-text` | `#0f172a` | Primary text |
  | `--color-text-muted` | `#64748b` | Secondary text, descriptions |
  | `--color-text-subtle` | `#94a3b8` | Placeholder, disabled text |
  | `--color-border` | `#e2e8f0` | Card/input borders |
  | `--color-success` | `#16a34a` | Success states |
  | `--color-warning` | `#d97706` | Warning states |
  | `--color-error` | `#dc2626` | Error states |
  | `--color-info` | `#0ea5e9` | Info states |

  **Spacing & shape tokens:**
  | Variable | Value |
  |---|---|
  | `--radius-card` | `16px` |
  | `--radius-element` | `8px` |
  | `--radius-pill` | `9999px` |
  | `--shadow-card` | `0 4px 24px rgba(15,23,42,0.06)` |
  | `--shadow-card-hover` | `0 8px 32px rgba(15,23,42,0.10)` |

  **Shared utility classes (defined in `main.css`):**
  | Class | Purpose |
  |---|---|
  | `.glass-card` | Translucent card with backdrop blur — use for elevated content panels |
  | `.solid-card` | Opaque white card with border — use for nested sections inside a glass card |
  | `.page-container` | Max-width centered wrapper for standalone pages (not needed inside `ToolPageLayout`) |
  | `.page-header` | Flex row with `h1` + `p` — use only on pages that do NOT use `ToolPageLayout` |
  | `.section-header` | Flex row with `h2` — for sub-sections within a card |
  | `.empty-state` | Centered placeholder with icon and text — use for zero-data states |
  | `.score-bar` / `.score-text` / `.score-track` / `.score-fill` | Horizontal score meter (market views) |
  | `.chip-row` | Flex wrap container for `v-chip` groups |
  | `.up-text` / `.down-text` / `.flat-text` | Red/green/grey text for financial change indicators |

- **Vuetify theme** is defined in `tool-hub/src/main.ts` and is kept in sync with the CSS tokens above. Use Vuetify
  color props (`color="primary"`, `color="success"`, etc.) on Vuetify components rather than raw hex values.
- **Tailwind theme** in `tailwind.config.js` extends the default palette with matching `primary` and `surface` color
  scales plus `rounded-card` and `shadow-card` utilities. Prefer the shared CSS classes (`glass-card`, `solid-card`)
  over chaining Tailwind utilities for card patterns.
- The home page may keep a distinctive visual background, but it must primarily serve tool discovery. Include searchable
  or scannable entry points such as featured tools, categories, recent tools, or quick actions rather than a passive
  showcase.
- Use route metadata in `tool-hub/src/router/index.ts` as the source of truth for navigable UI. Add meaningful
  `title`, `description`, `icon`, `keywords`, and `featured` metadata when adding public pages so navigation search and
  the home launcher stay useful.
- Prefer `ToolPageLayout` for individual tool pages. Use its compact/workspace variants for full-screen work areas such
  as AI chat or data dashboards, and avoid duplicating the page title when the layout already renders one.
- `ToolPageLayout` reads `title`, `description`, and `icon` from `route.meta` automatically — do not add a second
  `<h1>` or page header inside the slot. Set `:card="false"` for full-bleed layouts (chat, canvas editors, dashboards),
  and use the default `card=true` for standard tool pages that benefit from a contained white card.
- Avoid global element styles that fight Vuetify, especially broad `button`, `input`, or `textarea` rules. If a native
  element is unavoidable, scope its styles to the page/component and make it visually compatible with Vuetify controls.
- For common UI patterns such as tables, forms, dialogs, pagination, tabs, menus, filters, and loading/empty states,
  prefer Vuetify or existing project components first. If the existing component set cannot cover the interaction
  cleanly, evaluate adding a mature, well-maintained dependency. Hand-written low-level HTML/CSS implementations should
  be the last option, used only when component-based approaches are unsuitable.
- For complex frontend interactions or domain-heavy widgets, first evaluate mature, well-maintained components or
  libraries instead of hand-rolling core behavior. Examples include financial charts, rich editors, graph/network
  visualizations, calendars, maps, drag-and-drop builders, and virtualized data grids.
- When implementing domain-specific UI, reference established industry conventions for that domain and match user
  expectations unless the request explicitly calls for a different interaction model. For example, stock charting
  should follow common brokerage/trading UI patterns such as separate time-line and candlestick views, crosshair
  inspection, standard period controls, price/volume panes, and familiar red/green market coloring.
- For frontend verification, use static checks, unit tests, and `npm run build` from `tool-hub/` only. Do not run browser automation, Playwright/E2E browser checks, take browser screenshots, open localhost pages, or otherwise perform browser-based verification.

## Python Backend Guidelines

The `py/` app is a FastAPI backend assembled in `py/app.py` through a dependency container from `py/bootstrap.py`.

- Local Python backend work should run inside the Conda environment `ai`. Start PowerShell sessions with
  `conda activate ai` before running Python commands, Alembic migrations, or backend tests.

- Add new backend features as domain modules under `py/<domain>/`, typically with `schemas.py`, `service.py`,
  `store.py`, and `routes.py`.
- Route factories should expose `create_router(container)` and be registered in `py/app.py`.
- Reuse the existing auth dependency pattern from `auth.routes`: `create_auth_router` stores `get_current_user` on the
  auth router, and other routers can retrieve it through `container._auth_router`.
- Keep API responses aligned with `auth.schemas.ApiResponse` unless there is a strong reason to use a different
  contract.
- Configuration belongs in `py/core/settings.py` and should be loaded from environment variables or `py/.env`.
- Runtime dependencies used by the deployed Python service must be added to `py/requirements.txt`.
- For backend verification, at minimum run `python -m py_compile` on changed Python modules. If dependencies and
  services are available, also instantiate the app with `python -c "from app import create_app; create_app()"`.

## Agent Backend Directory Rules

Keep Agent backend responsibilities concentrated and avoid recreating deleted compatibility layers.

- `py/agent_chat/` is the single entry point for the Agent workbench chat experience. It owns `/api/v1/agent/chat`,
  `/api/v1/agent/chat/stream`, intent routing, model fallback, skill dispatch, trace creation, tool call summaries,
  and response shaping.
- `py/agent_eval/` owns Agent run persistence, trace/query APIs, feedback, eval cases, retry/cancel metadata, and
  aggregate metrics. Do not move evaluation or run-history storage into `agent_chat`.
- `py/skills/` contains local skill instructions and should stay workflow-focused. Add or update a skill when a task has
  a reusable specialized process, but keep runtime orchestration in `agent_chat`.
- Do not recreate `py/agent_runtime/`, `py/project_agent/`, or `py/langchain_examples/`. Those older directories were
  removed to avoid parallel Agent implementations and stale compatibility APIs.
- New Agent tools should be small, explicit functions or classes reachable from `agent_chat` through a registry-like
  boundary. Keep tool metadata normalized with `step_id`, `node`, `status`, `input_summary`, `output_summary`,
  `latency_ms`, `error`, and `tool_name` so the frontend and eval service can consume one trace shape.
- Keep public Agent APIs under `/api/v1/agent/*`. Do not add new `/api/v1/project-agent/*` endpoints.
- For frontend Agent workbench changes, keep the UI chat-first and wire it to the real streaming endpoint instead of
  adding template buttons or fake typing flows.

## Database Guidelines

PostgreSQL is the preferred persistent store for new Tool Hub business features that need durable relational data.

- Use SQLAlchemy 2.0 ORM for Python backend persistence and Alembic for schema migrations.
- Keep shared SQLAlchemy infrastructure under `py/db/`, ORM models under each domain module, and migrations under
  `py/alembic/versions`.
- Prefer Alembic revisions for schema changes. Raw SQL files under `py/sql/` may be kept as bootstrap/reference scripts,
  but deployed schema evolution should go through Alembic.
- Use table names prefixed by the feature domain. For the post feature, use `post_` tables such as `post_posts`,
  `post_comments`, and `post_likes`.
- Use `sys_` table names for authentication, users, roles, permissions, and other system-level account data, such as
  `sys_users`.
- Name indexes and constraints with the same feature prefix, for example `idx_post_posts_status_created` and
  `uq_post_likes_post_user`.
- Use `TIMESTAMPTZ` for persisted timestamps, with `created_at` and `updated_at` columns on mutable business tables.
- Prefer soft delete for user-generated content by using a `status` column such as `published`, `deleted`, or `hidden`.
- The Python backend reads PostgreSQL through `POSTGRES_DSN`. In URL-style DSNs, URL-encode reserved characters in
  passwords; for example `@` must be written as `%40`.
- In Docker Compose, services on `linger-net` should connect to PostgreSQL by container name, for example
  `postgres-db:5432`, not by public IP.
- When adding or changing deployed database requirements, update both the SQL schema file and any Docker/runtime
  configuration needed to reach the database. If Alembic is active for that schema, update or add an Alembic revision
  instead of relying only on a raw SQL file.

## Python Database Migration Commands

Run Alembic migrations from the `py/` directory after adding or changing SQLAlchemy models.

PowerShell local environment:

```powershell
conda activate ai
cd py
$env:POSTGRES_DSN="postgresql+psycopg2://user:password@localhost:5432/database"
python -m alembic upgrade head
```

For the Agent evaluation tables added in revision `20260518_0004`, either migrate to the latest head:

```powershell
conda activate ai
cd py
python -m alembic upgrade head
```

or migrate directly to that revision:

```powershell
conda activate ai
cd py
python -m alembic upgrade 20260518_0004
```

Verify the applied revision:

```powershell
conda activate ai
cd py
python -m alembic current
python -m alembic history --verbose
```

If running inside Docker Compose, execute Alembic in the Python API container with the deployed `POSTGRES_DSN`:

```powershell
docker compose exec langgraph-api python -m alembic upgrade head
docker compose exec langgraph-api python -m alembic current
```

If the backend logs `Agent 评测表不可用，请先运行 Alembic 迁移`, the app can still answer Agent chat requests, but
Agent metrics and feedback will not be persisted until the migration has created `agent_runs`, `agent_tool_calls`,
`agent_feedback`, `agent_eval_cases`, and `agent_eval_results`.

## Dependency & Deployment Notes

- When adding new runtime components or protocols, update the actual deployment dependency files, not only local
  environment exports. For the Python Docker backend, `py/Dockerfile` installs from `py/requirements.txt`; packages
  listed only in `py/ai.yaml` are not included in the deployed image.
- Pay special attention to optional runtime extras required by new components. For example, FastAPI WebSocket routes
  served by Uvicorn require `uvicorn[standard]`, `websockets`, or `wsproto`; otherwise upgrade requests can be logged as
  unsupported and handled as plain HTTP requests.
- After introducing frontend features that depend on backend protocols such as WebSocket or SSE, verify both the
  browser request and the container logs in the deployed environment.

## Skill Routing

Use the local skills under `skills/` when the request clearly matches one of the workflows below. Prefer the most
specific skill that fits the task. If the user explicitly names a skill, use that skill.

- `nl-to-sql-generator`
  Use for natural language plus schema input when the task is to generate read-only SQL only. Do not execute SQL in
  this skill.

- `sql-exporter`
  Use for an existing SQL statement plus database connection details when the task is to validate, execute, or export
  query results. Do not invent SQL from a business question in this skill.

- `java-stacktrace-analyzer`
  Use for Java, Spring Boot, Maven, Gradle, JDBC, or test stack traces when the task is to identify the root cause and
  propose fixes.

## Skill Usage Notes

- Ask for missing schema details before using `nl-to-sql-generator`.
- Ask for missing SQL or connection details before using `sql-exporter`.
- For Java error analysis, prioritize the deepest actionable `Caused by` chain instead of top-level wrapper exceptions.
- Keep skill responsibilities separate. Do not let `sql-exporter` generate SQL, and do not let
  `java-stacktrace-analyzer` modify code unless the user asks for code changes after the diagnosis.

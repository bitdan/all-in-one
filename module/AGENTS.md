# Spring Boot Module Guidelines

本文件适用于 `module/`。同时遵守仓库根目录 `AGENTS.md`。

## 模块结构与基线

- `src/main/java/com/linger/module/`：按业务域组织 Controller、Service、Mapper、模型与集成代码。
- `src/main/resources/mapper/`：MyBatis 自定义 XML。
- `src/main/resources/application.yml`：公共配置，使用 Maven 占位符 `@profile.active@`。
- `src/test/java/`：JUnit 5 单元测试和必要的集成测试。
- 技术基线为 Java 8、Spring Boot `2.7.18`、MyBatis-Plus `3.5.17`、PostgreSQL。
- 保留父 POM 的 BOM 版本和 `mybatis-plus-jsqlparser-4.9` 兼容依赖，不单独升级其中一个组件。

## 分层与模型

- Controller 使用明确的 DTO、Query、Command，不使用 `Map<String, Object>` 承载业务请求。
- 业务规则、事务编排和 DTO 转换放在 Service 或 Repository/Store，不堆在 Controller。
- 使用 Lombok 和构造器注入，避免手写样板构造器、Getter、Setter。
- 通用审计字段抽到基础实体；时间字段使用 `OffsetDateTime`，可空数值优先使用包装类型。
- 分页复用公共 `PageQuery`、`PageResult`，在 API 边界校验页码并限制每页大小。
- 实体明确声明 `@TableName`、`@TableId`；主键策略必须匹配数据库真实生成方式。
- 普通下划线字段依赖 `map-underscore-to-camel-case: true`，不重复添加无意义的 `@TableField`。
- `@TableField` 只用于特殊映射、非持久化字段、自动填充或类型处理器；查询结果也需要类型处理器时启用 `autoResultMap`。
- 删除继续使用领域 `status`；未经完整迁移和测试，不单独引入 `@TableLogic`。

## MyBatis-Plus 与查询

- 持久化统一使用 MyBatis-Plus，不新增或继续使用 `JdbcTemplate`。
- Mapper 接口继承 `BaseMapper<Entity>`，只保留方法声明和必要的 `@Param`。
- 自定义 SQL 放在 `src/main/resources/mapper/` 对应 XML；禁止在 Mapper 中使用 SQL 注解、Provider 或注解内 `<script>`。
- 单表常规查询优先使用 `LambdaQueryWrapper`、`LambdaUpdateWrapper`，不使用字符串字段名。
- 联表、聚合、投影、锁、PostgreSQL 特性和原子状态迁移使用 Mapper XML。
- Wrapper 在执行方法内新建，不缓存、不共享、不复用，也不暴露给 Controller 或公共 Service 接口。
- 可选条件使用 Wrapper 的条件重载；更新、删除或权限查询前拒绝空 ID、空集合和空归属条件。
- `selectOne` 仅用于数据库约束保证唯一的条件，不用任意 `LIMIT 1` 掩盖重复数据。
- 列表查询只取需要的列；分页使用稳定排序；禁止循环调用 Mapper 形成 N+1。

## SQL、事务与并发

- XML 参数使用 `#{}`；`${}` 仅用于固定且经过白名单验证的 SQL 结构。
- 不把不可信文本传入 `apply`、`last`、`having`、`inSql`、`setSql`、`orderBy` 等动态 SQL 方法。
- 客户端排序键必须映射到后端白名单，不能直接拼接列名或排序片段。
- 请求 DTO 不直接传给 `insert`、`updateById` 或 `saveOrUpdate`；显式映射允许修改的字段。
- 更新和删除必须有明确业务条件并检查影响行数；零行更新不默认视为成功。
- 乐观锁实体声明并初始化 `@Version`；状态迁移在 `WHERE` 中校验旧状态或版本，并原子更新版本。
- 计数、库存、抢占和状态变化使用单条条件 SQL，不采用先查再改。
- 多表写入和批处理放在公开的 `@Transactional` Service 方法中，不依赖同类自调用开启事务。
- 大集合分批处理并限制批次大小；需要原子性时显式开启事务。

## MyBatis 配置

- Mapper 扫描和拦截器统一维护在 `MybatisPlusConfiguration`，不重复创建配置 Bean。
- 拦截器顺序保持：SQL 改写类在前，分页和乐观锁居中，分析或防攻击类在后。

## 构建与测试

从仓库根目录运行 Maven。若 `mvn` 不在 `PATH`，使用 `D:\app\apache-maven-3.6.3\bin\mvn.cmd`。

```powershell
mvn -Plocal -pl module -am "-Dtest=HttpUtilTest,DeepSeekClientTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -Plocal -pl module -am "-Dtest=DeepSeekApiIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -Plocal -pl module -am -DskipTests package
```

- 执行本模块测试或构建必须启用 `-Plocal`，不要运行未指定 Profile 的 `module` Maven 命令。
- 默认只运行改动涉及的测试类，通过 `-Dtest=TestClass1,TestClass2` 指定；除非用户明确要求，不运行本模块全量测试。
- PowerShell 会解析 `-Dtest` 中的逗号，多个测试类时要给整个参数加引号。
- 测试使用 JUnit 5，放在对应包的 `src/test/java`，类名以 `*Test` 结尾。
- 单元测试保持快速、聚焦；仅在数据库、外部协议或真实集成行为无法由单元测试覆盖时增加集成测试。
- 集成测试不要混入默认快速测试命令；交付时单独说明环境依赖。

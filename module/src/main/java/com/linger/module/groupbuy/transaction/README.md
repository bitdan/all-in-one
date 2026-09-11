# 高并发拼团交易引擎

## 1. 能力范围

- PostgreSQL 保存活动、团、订单、成员、库存账户、库存预占单、库存流水、Outbox 和延迟任务。
- Redis Lua 原子校验活动、个人限购、库存、团名额和重复参团。
- `(user_id, request_id)` 与 Redis 幂等 key 双重防重。
- 支付回调使用订单状态 CAS 和支付流水唯一索引防止重复入账。
- PostgreSQL Outbox 以至少一次方式推进支付确认、库存释放和退款。
- PostgreSQL 延迟任务表负责可靠存储，内存时间轮负责近期任务调度。
- 定时补偿会回收因进程中断而停留在 `INIT` 或过期 `WAIT_PAY` 的订单。

退款网关当前使用 `LocalPaymentGateway` 模拟成功。接入真实支付渠道时，实现 `PaymentGateway`，并以
`paymentNo` 作为退款幂等键。

## 2. 初始化或升级 PostgreSQL

拼团表结构使用独立的 Flyway 历史表管理，`local` Profile 启动时自动迁移。迁移文件位于：

```text
module/src/main/resources/db/migration/groupbuy/
```

本地启动命令：

```powershell
mvn -pl module -am -Plocal spring-boot:run
```

`local` Profile 使用 `groupbuy_flyway_schema_history`，不会与同库其他业务的迁移记录混用；其他环境默认关闭 Flyway。
迁移不包含测试数据；对旧版拼团表会根据已有库存流水回填库存账户和预占单。

## 3. 配置并启动

应用使用 Spring Boot 标准数据源自动配置，Tool Hub 与拼团业务共享同一个连接池：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://host:5432/tool_hub"
$env:SPRING_DATASOURCE_USERNAME="user"
$env:SPRING_DATASOURCE_PASSWORD="password"
mvn -pl module -am -Plocal spring-boot:run
```

交易引擎和数据库连接均随 Spring Boot 应用启动，不再维护独立的拼团启用开关与连接配置。

## 4. 调用顺序

### 创建活动

```http
POST /api/v1/groupbuy/activities
Content-Type: application/json

{
  "name": "三人拼团",
  "skuId": "SKU-1001",
  "unitPrice": 99.90,
  "totalStock": 1000,
  "perUserLimit": 1,
  "targetCount": 3,
  "payTimeoutSeconds": 300,
  "groupTimeoutSeconds": 3600,
  "startsAt": "2026-09-04T08:00:00Z",
  "endsAt": "2026-09-05T08:00:00Z"
}
```

### 发布活动并开团

```http
POST /api/v1/groupbuy/activities/{activityId}/publish
POST /api/v1/groupbuy/activities/{activityId}/groups

{"creatorUserId": 10001}
```

### 下单

```http
POST /api/v1/groupbuy/orders
Content-Type: application/json

{
  "requestId": "client-request-0001",
  "userId": 10001,
  "activityId": 1,
  "groupId": 1,
  "quantity": 1
}
```

### 支付回调

```http
POST /api/v1/groupbuy/payments/callback
Content-Type: application/json

{
  "orderId": "下单返回的订单ID",
  "paymentNo": "payment-0001",
  "paidAmount": 99.90,
  "paidAt": "2026-09-04T08:10:00Z"
}
```

查询订单：

```http
GET /api/v1/groupbuy/orders/{orderId}
```

## 5. 一致性边界

Redis 是可重建的高并发准入层，PostgreSQL 的库存账户与预占单是最终业务事实。系统不声明跨存储 Exactly Once，而是使用：

```text
Lua 原子预占 + 数据库唯一约束 + 状态 CAS + Outbox 至少一次投递 + 幂等消费 + 定时对账
```

库存应满足：

```text
initialStock = available + reserved + confirmed
```

建议压测时同时校验数据库库存流水、Redis 三段库存、团人数和订单状态，不能只统计 HTTP 成功数。

# 拼团订单与库存模块设计

## 1. 目标与边界

本方案面向高并发拼团下单，目标是同时满足：

- 库存不超卖、订单不重复、支付不重复入账。
- Redis、PostgreSQL、支付渠道之间发生部分失败时可以最终收敛。
- 订单、团、库存各自维护状态，跨域流程由应用服务编排。
- 能从 PostgreSQL 重建 Redis，而不是把缓存当作不可丢失的唯一库存账。

当前活动只允许一个 SKU，因此订单表暂时保存商品快照。扩展到购物车、多 SKU 或优惠分摊时，
再增加 `groupbuy_order_items`，不在本阶段提前扩大交易事务。

## 2. 模块职责

当前包结构：

```text
groupbuy/
├── activity/       活动实体、状态和持久化
├── group/          团实例、成员、团计数、状态和持久化
├── order/          订单、支付/退款状态和持久化
├── inventory/      库存账户、预占单、流水、状态和持久化
├── transaction/    HTTP 契约、下单/支付/取消/成团/退款的 Saga 与本地事务编排
└── infrastructure/ Redis、Outbox、延迟任务和支付渠道适配
```

HTTP 路径及请求、响应契约保持稳定，Java 内部类型已经按业务边界拆包。调用方向为：

```text
Controller -> TransactionApplicationService
              ├── RedisGroupBuyAdmissionService
              └── GroupBuyTransactionStore（PostgreSQL 本地事务边界）
                    ├── activity/group/order/inventory Mapper
                    └── Outbox/DelayTask Mapper

OutboxProcessor/DelayScheduler
              -> GroupBuyTransactionStore + 基础设施适配器
```

业务域包不直接操作 Redis，也不跨域修改其他聚合；跨 Redis 与 PostgreSQL 的流程由
`GroupBuyTransactionApplicationService` 编排，跨表原子写入由 `GroupBuyTransactionStore` 承担。
避免为每个 Mapper 增加只做转发的空壳 Service，后续出现可独立复用的领域规则时再下沉对应领域服务。

## 3. 数据模型

### 3.1 订单

`groupbuy_orders` 保存订单事实及价格快照，`(user_id, request_id)` 防止客户端重复下单，
`payment_no` 唯一索引防止同一支付流水重复绑定：同一订单的同一流水视为幂等回调，绑定到其他订单则返回
`PAYMENT_NO_CONFLICT`。

订单状态机：

```text
INIT -> WAIT_PAY -> PAID -> GROUP_SUCCESS -> COMPLETED
INIT -> REJECTED
WAIT_PAY -> CANCELLED
PAID -> REFUNDING -> REFUNDED
```

状态变化必须使用包含旧状态的条件更新，影响行数为 0 表示并发请求已经抢先完成迁移。

### 3.2 库存账户

`groupbuy_inventory_stock` 以 `(activity_id, sku_id)` 唯一标识活动库存，维护：

```text
total_quantity = available_quantity + reserved_quantity + confirmed_quantity
```

- `available_quantity`：仍可下单的库存。
- `reserved_quantity`：已下单、尚未完成支付确认的库存。
- `confirmed_quantity`：支付已确认的库存；团失败退款后返还可用库存。

所有计数变化使用单条条件 SQL，不使用“先查询再更新”。例如预占库存：

```sql
UPDATE groupbuy_inventory_stock
SET available_quantity = available_quantity - :quantity,
    reserved_quantity = reserved_quantity + :quantity
WHERE activity_id = :activityId
  AND sku_id = :skuId
  AND available_quantity >= :quantity;
```

### 3.3 库存预占单

`groupbuy_inventory_reservations` 以订单号作为预占号，一个订单只能有一条预占记录：

```text
RESERVED -> CONFIRMED
RESERVED -> RELEASED
RESERVED/CONFIRMED -> REFUNDED
```

库存账户解决“还剩多少”，预占单解决“哪一笔订单占了多少以及当前处于什么阶段”。
操作流水解决“为什么发生变化”，三者职责不能互相替代。

### 3.4 库存流水

在当前“一个订单、一个 SKU、每种操作最多一次”的边界内，`groupbuy_inventory_ledger` 使用
`(order_id, operation)` 唯一约束保证重复处理不会重复记账。
操作包括 `RESERVE`、`CONFIRM`、`RELEASE`、`REFUND`。

## 4. 核心流程

### 4.1 下单

```text
用户请求
  -> 用户/活动维度限流
  -> 数据库写 INIT 订单，建立故障恢复锚点
  -> Redis Lua 原子校验并预占库存、团名额、限购数量
  -> PostgreSQL 本地事务
       订单 INIT -> WAIT_PAY
       库存 available -> reserved
       创建库存预占单
       创建团成员
       写库存流水
       写支付超时任务
  -> 返回订单
```

数据库事务失败时立即执行 Redis 幂等释放；进程在释放前宕机时，由陈旧 `INIT` 订单扫描任务补偿。

### 4.2 支付

支付回调先通过订单状态 CAS 写入支付事实和 Outbox。Outbox 消费时：

1. Redis 把预占库存转成确认库存。
2. 数据库把库存预占单 `RESERVED -> CONFIRMED`。
3. 数据库库存 `reserved -> confirmed` 并写 `CONFIRM` 流水。
4. 团记录在行锁保护下增加支付人数。
5. 最后一名支付者将团推进为 `SUCCESS`，批量推进成员和订单状态。

上述步骤采用至少一次处理语义。第 2～5 步处于同一个 PostgreSQL 本地事务，Redis 预占状态、数据库成员与
预占单 CAS、流水唯一键共同防重。

### 4.3 超时取消

支付回调和超时任务竞争订单的 `WAIT_PAY` 状态，只有一个条件更新可以成功。
取消成功后，数据库事务释放团名额、库存账户和预占单，并写 Outbox 异步释放 Redis。

### 4.4 成团失败退款

团超时任务与最后一笔支付通过同一团记录竞争状态迁移：

- 未支付订单：取消并释放预占库存。
- 已支付订单：进入 `REFUNDING`，由退款 Outbox 调用支付渠道。
- 渠道退款、Redis 返还、数据库库存返还都必须幂等。

若支付刚入账但支付 Outbox 尚未确认库存，预占单仍是 `RESERVED`；退款只减少团预占人数。
若预占单已是 `CONFIRMED`，退款同时减少团支付人数和预占人数。不能无条件减少 `paid_count`，
否则可能误减同团其他订单的已支付人数。

## 5. 一致性原则

系统不声明跨 Redis、PostgreSQL、消息系统和支付渠道的 Exactly Once，而是采用：

```text
Redis Lua 原子准入
+ PostgreSQL 条件更新
+ 唯一约束与状态 CAS
+ Outbox 至少一次处理
+ 幂等消费者
+ 延迟任务和定时对账
```

PostgreSQL 是最终业务事实，Redis 是可重建的准入投影。库存至少要满足：

```text
DB: total = available + reserved + confirmed
Redis: initialStock = available + reserved + confirmed
```

对账时还要核对有效预占单数量、库存流水净额、订单状态、团成员数和团计数。
团计数必须始终满足 `0 <= paid_count <= reserved_count <= target_count`。计数回退使用带下界和关联计数条件的
SQL；影响行数为 0 表示账实已漂移，应回滚当前事务并告警，不能用 `GREATEST(..., 0)` 静默掩盖。

## 6. 高并发难点

### 6.1 热点库存

同一个热门 SKU 在 Redis 和 PostgreSQL 都是热点。当前先通过 Lua、前置限流、缩短数据库事务和只让
Redis 准入成功的业务执行库存事务来降低竞争。如果单 SKU 已超过单节点处理能力，再考虑库存分桶；
分桶会显著增加售罄判断、余量聚合、退款归桶和对账复杂度，不应过早引入。

### 6.2 Redis 成功、数据库失败

这是跨存储无法用本地事务覆盖的窗口。处理方式是立即补偿、保留 Redis 预占记录和截止时间索引、
扫描陈旧 `INIT` 订单。由于 INIT 订单先于 Redis 预占落库，正常路径不会出现没有订单锚点的 Redis 预占；
若以后调整顺序，则必须额外增加“孤儿 Redis 预占”扫描。

### 6.3 支付与超时竞争

不能仅比较应用服务器时间，也不能先查状态再决定更新。支付渠道时间需要验签或主动查询确认，
最终由 `WAIT_PAY -> PAID` 与 `WAIT_PAY -> CANCELLED` 两条 CAS 决定胜者。

### 6.4 至少一次消息

消费者可能在完成外部副作用后、标记事件完成前宕机。退款接口必须以支付流水作为幂等键；
数据库操作必须以预占状态和流水唯一约束作为幂等屏障，不能依赖“消息只来一次”。
批量抢占事件时，租约必须覆盖整批最坏处理时间，或按事件续租/领取；否则后半批事件尚未处理，租约就可能
到期并被其他节点重复抢占。消费吞吐和积压量也必须监控，不能只验证消息最终可重试。

### 6.5 Redis 重建

Redis 数据丢失或活动 key 损坏时，应先关闭对应活动准入，从数据库库存账户、有效预占单、订单和团状态
构建新版本 key，核对恒等式后再切换并恢复流量。直接用活动初始库存覆盖现有 key 会导致超卖。

### 6.6 数据库热点行

团计数和库存账户都是热点行。事务中应先完成成员、预占、流水等写入，最后更新热点计数并立即提交。
极端规模下可以分库存桶，但团目标人数通常较小，团记录优先保持单行强约束更容易保证正确性。

### 6.7 发布活动与开团窗口

发布活动采用“Redis 仅首次初始化，再将数据库 READY 推进为 RUNNING”；开团采用“数据库 INIT、Redis 初始化、
数据库 OPEN”。Redis 成功后数据库提交失败不会造成超卖，因为下单仍校验数据库活动和团状态，但会留下不可用投影。
请求重试可以完成状态推进；无人重试时需要扫描数据库 `READY/INIT` 与 Redis 投影差异并清理或续推。

## 7. 故障矩阵

| 故障 | 收敛方式 |
|---|---|
| Redis 预占成功、数据库确认失败 | 立即释放；陈旧 INIT 扫描兜底 |
| 数据库成功、HTTP 响应丢失 | `(user_id, request_id)` 返回原订单 |
| 重复支付回调 | 支付流水唯一键 + 订单状态 CAS |
| 同一支付流水绑定不同订单 | 唯一键拒绝并返回 `PAYMENT_NO_CONFLICT` |
| 支付与取消并发 | 竞争 WAIT_PAY 状态 |
| Outbox 处理后宕机 | 租约超时重投 + 幂等消费 |
| 退款成功但本地未完成 | 支付渠道幂等退款 + Outbox 重试 |
| 调度节点宕机 | 数据库延迟任务租约到期后重新抢占 |
| Redis 数据丢失 | 暂停准入，从数据库事实层重建并对账 |
| 发布/开团的 Redis 初始化成功、数据库推进失败 | 请求幂等重试；投影差异扫描兜底 |
| 团计数回退不满足约束 | 当前事务回滚并告警，交由对账修复 |

## 8. 测试与压测

必须覆盖：

- 库存小于并发请求数时成功数严格等于库存。
- 相同 requestId 并发下单只产生一个有效订单和预占单。
- 相同 paymentNo 并发回调只入账一次。
- 相同 paymentNo 绑定不同订单时返回稳定业务冲突。
- 支付、取消、团超时三方竞争。
- Redis 成功后数据库异常的补偿。
- Outbox 租约到期后的重复处理。
- Redis 清空后的重建和账实核对。
- 团计数异常时回退失败且整个本地事务回滚。
- 发布活动、开团在 Redis 成功而数据库失败后的重试与扫描补偿。

压测报告至少记录吞吐、P50/P95/P99、错误码分布、数据库连接池等待、热点 SQL 锁等待、Redis Lua 耗时、
Outbox 积压和最终账实一致性。没有真实数据前，不在简历中填写 QPS 或延迟数字。

## 9. 落地状态

已落地：业务域拆包、Redis Lua 准入、PostgreSQL 库存账户/预占单/流水、条件状态迁移、Outbox、延迟任务，
以及陈旧 `INIT` 和过期 `WAIT_PAY` 订单补偿。

待补齐后才能宣称完整生产能力：生产支付验签与主动查询、活动发布/开团投影差异扫描、Redis 版本化重建、
库存与团账实自动对账告警、Outbox 按事件续租或并行消费，以及真实数据库/Redis 故障注入和压测报告。

## 10. 面试表达

推荐用一句话概括：

> Redis Lua 负责高并发原子准入，PostgreSQL 库存账户、预占单和流水负责最终账本，跨存储通过状态 CAS、
> Outbox 至少一次投递、幂等消费和定时对账实现最终一致性，不依赖分布式锁或 Exactly Once 假设。

后续简历只写已经实现并验证的数据；个人练习项目不要混入真实工作经历。

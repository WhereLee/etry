# 积分交易中心

一笔交易从下单到结束的流水线，加一本账。

用户浏览商品 → 下单 → 冻结库存与积分 → 生成待支付订单。系统保证：库存不超卖、重复提交不产生多单、库存与积分两处冻结始终对得上。

当前处于 **M0**：只做商品浏览、下单冻结、订单查询、积分流水。支付、超时取消、退款、对账在后续阶段。

---

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.3 · MyBatis（手写 SQL + XML） · Flyway |
| 存储 | MySQL 8 · Redis（JWT 黑名单） |
| 鉴权 | JWT + jti 黑名单 + token_version |
| 前端 | Vue 3 · Vite · Vue Router · Pinia · axios（无 UI 组件库） |
| 测试 | JUnit 5 · Spring Boot Test · AssertJ |

---

## 快速开始

### 1. 建库

```sql
CREATE DATABASE trade_center DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE trade_center_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

表结构由 Flyway 在启动时自动创建，不需要手动建表。

### 2. 启动后端

前置：MySQL 在 `127.0.0.1:3306`（默认 root/root）、Redis 在 `127.0.0.1:6379`。
账号密码在 `server/src/main/resources/application.yml`，可用环境变量 `TRADE_REDIS_HOST` / `TRADE_REDIS_PORT` / `TRADE_JWT_SECRET` 覆盖。

```bash
cd server
mvn spring-boot:run
```

启动后监听 `http://127.0.0.1:8080`。首次启动会自动建表、灌入 6 个商品、创建演示账号。

### 3. 启动前端

```bash
cd web
npm install
npm run dev
```

打开 `http://localhost:5173`。前端通过 Vite 代理把 `/api` 转发到 8080，不需要处理跨域。

### 4. 演示账号

| 账号 | 密码 | 说明 |
|---|---|---|
| `buyer` | `demo123` | 普通用户，初始 50000 积分 |
| `admin` | `demo123` | 管理员，可访问 `/api/admin/**` |

注册的新账号初始积分为 0，充值功能在 M1 才做，暂时无法下单。

---

## 接口

统一响应体：`{ code, message, data, timestamp }`，`code = 0` 表示成功。HTTP 状态码恒为 200，业务结果看 `code`。

### 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册并自动登录 |
| POST | `/api/auth/login` | 账号密码换 token |
| POST | `/api/auth/logout` | 退出当前设备（token 立即失效） |
| PUT | `/api/auth/password` | 修改密码，踢掉所有设备 |
| GET | `/api/auth/me` | 当前用户信息 |

### 商品

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/products?category=&page=&size=` | 分页列表，只返回在售商品 |
| GET | `/api/products/{id}` | 详情 |

### 订单

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/orders` | 下单，**必须带 `Idempotency-Key` 请求头** |
| GET | `/api/orders?status=&page=&size=` | 我的订单 |
| GET | `/api/orders/{id}` | 订单详情 |

### 积分

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/points?flowLimit=20` | 可用余额、冻结额、最近流水 |

---

## 测试

```bash
cd server
mvn test
```

当前 6 个用例，全部使用独立库 `trade_center_test`，不影响开发数据。

| 用例 | 验证内容 |
|---|---|
| `shouldRejectWhenStockNotEnough` | 库存不足抛异常，订单、流水、冻结全部为零 |
| `shouldRejectWhenPointsNotEnough` | 积分不足抛异常，库存冻结被回滚 |
| `shouldBeIdempotentWithSameKey` | 同一幂等键提交三次只产生一张订单 |
| `shouldKeepSnapshotAfterProductPriceChanged` | 商品改价后，历史订单明细的单价与名称不变 |
| `shouldExpireInTenMinutes` | 订单有效期 = 创建时间 + 10 分钟 |
| `concurrentOrdersShouldNotOversell` | 100 并发抢 10 件库存 |

并发用例的实际输出：

```
并发结果: 成功=10 拒绝=90 库存冻结=10 订单数=10 积分冻结=29900 余额=20100 耗时=725ms
```

---

## 关键设计

### 库存与积分的双冻结

下单要同时占用两样资源：库存和积分。任一步失败，两样都要撤销。

实现上全部放在一个本地事务里，靠数据库回滚保证，不写补偿代码。顺序是「先冻积分（单行），再冻库存（多行按 ID 升序）」——单行先做失败代价小，多行固定顺序避免死锁。

### 并发保护用条件更新，不用 version 字段

```sql
UPDATE t_product
SET stock_frozen = stock_frozen + #{quantity}
WHERE id = #{id}
  AND status = 'ON_SALE'
  AND stock_total - stock_frozen >= #{quantity}
```

影响行数为 0 即代表库存不足。这个 WHERE 本身就是原子的 CAS，比「读版本号再比对再更新」少一次失败重试的几率，所以没引入 version 字段。

### 幂等键

下单必须带 `Idempotency-Key` 请求头，唯一索引建在 `(user_id, idempotency_key)` 上——**绑定到用户**，不同用户撞同一个键互不影响。

服务层先查后插，并发穿透时由唯一索引兜底：捕获 `DuplicateKeyException` 后重新查询并返回已有订单，不会报错给前端。

前端在进入确认页时生成一次 UUID，之后重试都复用同一个键。

### JWT 的两种失效方式

JWT 无状态，为了能主动失效，做了两件事：

| 场景 | 机制 | 存储 |
|---|---|---|
| 退出当前设备 | 把 token 的 `jti` 写入黑名单，TTL 等于剩余有效期 | Redis |
| 改密码 / 全部下线 | 用户表 `token_version` +1，旧 token 的 `ver` 对不上即拒绝 | MySQL |

Redis 不可用时黑名单查询降级放行——`token_version` 那道校验不依赖 Redis，改密踢人的能力不受影响。

---

## M0 已知限制

这些是**故意没做**的，不是遗漏：

1. **下单同步处理，没有 MQ**。超时自动取消（10 分钟到点释放库存和积分）还没实现，订单会一直停在待支付。M3 接 RocketMQ 的延时消息来做。
2. **列表查询走全表 + 内存分页**。没有 Redis 缓存，商品多了会慢。M6 处理。
3. **没有支付和退款**。订单只能创建，不能支付、取消、退款。M1 接模拟支付网关，M2 做积分支付。
4. **新用户积分为 0 且没有充值入口**。M1 做充值回调后才可用。
5. **前端分类筛选是写死的三个分类**（数码配件 / 桌面配件 / 音频），来自种子数据，商品分类变化需要同步改前端。
6. **单库事务，没有跨服务一致性**。目前所有资源都在同一个 MySQL 里，所以本地事务够用；引入 MQ 之后才需要补偿设计。

---

## 目录结构

```
trade-center/
  server/                    Spring Boot 后端
    src/main/java/com/wherelee/trade/
      common/                统一响应、错误码、全局异常
      config/                安全配置、拦截器、种子数据
      auth/                  注册登录、JWT、黑名单、当前用户
      user/                  用户实体与 Mapper
      product/               商品查询与库存预占
      order/                 下单（事务）、幂等、订单查询
      point/                 积分账户与流水
      stock/                 库存变动流水
    src/main/resources/
      db/migration/          Flyway 脚本 V1~V3
      mapper/                MyBatis XML
      application.yml
    src/test/java/           并发测试与主流程测试
  web/                       Vue 3 前端
    src/api/                 接口封装
    src/views/               9 个页面
    src/stores/              登录态
  stress/                    冒烟与鉴权验证脚本（PowerShell）
  tools/                     开发辅助脚本
```

---

## 开发备注

- **MyBatis Spring Boot Starter 默认不扫描 XML**，必须显式配置 `mybatis.mapper-locations`，否则报 `Invalid bound statement`。
- **Lombok 需要显式配置 `annotationProcessorPaths`**，只在 IDE 里能编译、命令行 `mvn compile` 报错时，多半是这个原因。
- `tools/fix-tail.ps1` 是开发期写的辅助脚本：批量写文件时出现过「文件末尾被追加空壳类」的情况，它按「第二个顶层类型声明」定位并截断多余内容。
- 数据库表名统一加 `t_` 前缀，避开 `order` 这类 MySQL 关键字。

# 类美团外卖系统

这是一个按真实外卖平台业务复杂度扩展的可运行项目，包含 Spring Boot 后端和 Vue 3 前端。项目默认使用内存模式便于直接演示，也支持 Docker Compose 启动 MySQL、Redis、RabbitMQ 后切换到 MySQL 持久化模式。

项目功能和交互参考主流外卖平台，但不使用美团商标、Logo、真实素材或受保护的界面资产。

## 启动

### 内存模式

后端默认使用内存模式：

```powershell
cd E:\project\takeout\backend
.\mvnw.cmd spring-boot:run
```

前端：

```powershell
cd E:\project\takeout\frontend
npm install
npm run dev
```

访问：

```text
http://127.0.0.1:5173
```

如果 `8080` 已被占用，可以临时改后端端口：

```powershell
cd E:\project\takeout\backend
$env:SERVER_PORT="8081"
.\mvnw.cmd spring-boot:run
```

前端 Vite 要求 Node.js `20.19+` 或 `22.12+`。如果本机是 Node `22.9.0`，需要升级 Node 后再启动前端。

### MySQL 持久化模式

使用 Docker Compose 启动基础设施：

```powershell
cd E:\project\takeout
docker compose up -d mysql redis rabbitmq
```

再启动后端：

```powershell
cd E:\project\takeout\backend
$env:SPRING_PROFILES_ACTIVE="mysql"
.\mvnw.cmd spring-boot:run
```

默认连接信息：

```text
JDBC URL: jdbc:mysql://localhost:13306/takeout
Username: takeout
Password: takeout_dev_123
```

Docker 容器内部 MySQL 仍监听 `3306`，但映射到 Windows 宿主机的 `13306`。这样即使本机已经安装 MySQL 并占用 `3306`，也不会影响这个项目的 Docker MySQL。

MySQL schema 位于 `backend/src/main/resources/db/mysql/001_schema.sql`。后端启动时会自动执行 schema，并把演示账号、门店、商品、频道、运营位种子数据写入数据库；地址、购物车、门店经营配置、订单、支付、退款、配送任务、配送状态日志、库存占用、售后、评价、风控、审计、Outbox 都会随业务动作持久化。旧版本容器启动时会自动补齐新增字段和表。

如果之前用旧版本初始化过 MySQL 容器，需要重建数据卷后再启动：

```powershell
cd E:\project\takeout
docker compose down -v
docker compose up -d mysql redis rabbitmq
```

## 本地开发初始账号

以下账号仅用于本地开发和验收。前端不再自动登录，也不会通过接口返回任何商家账号密码；访问业务页前必须先在登录页输入账号和密码，登录 token 会持久化到浏览器本地存储，可手动退出登录。

| 角色 | 用户名 | 本地初始密码 |
| --- | --- | --- |
| 用户 | customer | 123456 |
| 商家默认账号 | merchant | 123456 |
| 骑手 | rider | 123456 |
| 后台 | admin | 123456 |

平台种子数据包含 168 家门店，每个门店都有独立商家账号；商家账号列表现在只允许后台管理员接口访问，商家端只展示当前登录商家名下门店、商品和订单。

## 已覆盖能力

- 用户端：首页频道、运营活动、验证码弹窗、红包领取、搜索、分类筛选、排序、数字分页附近门店、门店详情、商品分类菜单、满减/券提示、购物车持久化、地址新增/编辑/删除/默认地址、结算、数字分页订单追踪、模拟支付、确认收货、退款原因弹窗、评价弹窗。
- 商家端：单门店经营归属、门店信息维护、营业/打烊、营业时间、配送范围、起送价/配送费配置、经营指标、商品数字分页、商品分类/展示信息维护、订单数字分页、库存和上下架、待接单订单分页、接单、拒单退款原因弹窗、出餐、订单漏斗。
- 骑手端：可接任务池、骑手接单、到店、取餐、送达、配送状态日志、配送任务分页、异常类型/说明/凭证弹窗、履约路线和绩效指标。
- 后台端：订单监控分页、售后工单分页和处理结论弹窗、风控记录分页、审计日志分页、Outbox 与库存占用查看。
- 后端规则：登录页 token 会话、订单状态机、确认收货、服务端金额重算、库存占用、待支付超时关闭、券锁释放、支付回调幂等、多门店资源归属校验、地址归属校验、骑手任务归属校验、RBAC、敏感信息脱敏、审计留痕。
- 数据库：MySQL 表结构、种子数据启动、地址表、购物车表、门店配置字段、订单聚合落库、库存占用表、支付回调流水、退款单、配送单、配送状态日志、售后工单、评价、风险记录、审计日志、Outbox 事件。

## 当前边界

这个版本已经不是简单课设级演示，但仍不能直接宣称为可上线商用系统。真正商用还需要补齐第三方支付渠道、消息队列消费端、优惠券完整持久化表、SKU/包装费/限购的独立表模型、文件与图片存储、后台权限分级、压测、监控告警、数据备份、合规隐私处理、部署流水线和故障演练。

## 三类特性代表实现

| 要求 | 使用技术 | 具体问题 | 实现效果 | 解决的挑战 |
| --- | --- | --- | --- | --- |
| 高并发 | Redis Lua 原子扣减/滑动窗口限流 + MySQL 条件更新 + Hikari 连接池 + Caffeine 分页缓存 | 红包活动、秒杀套餐、附近门店高频查询 | 红包库存和领取记录在 Redis Lua 中原子完成；下单库存执行 `stock >= quantity` 条件更新；门店分页结果短期缓存 | 防止超卖、重复领券和接口刷爆，同时降低海量门店列表反复排序查询压力 |
| 高可靠 | Transactional Outbox + RabbitMQ 发布确认 + 定时重试任务 + 待支付超时关单 | 支付成功、商家接单、骑手送达等事件如果直接发消息，服务异常可能造成消息丢失；未支付订单长期占库存 | 业务操作先写 `outbox_event` 表，定时任务扫描 `PENDING/RETRY` 事件；待支付订单超时自动取消并释放库存和券锁 | 业务数据和通知事件解耦，服务重启后仍能从数据库恢复待处理事件，避免库存被僵尸订单长期占用 |
| 高安全 | BCrypt 密码哈希 + 登录页 token 会话 + RBAC + 敏感接口 ADMIN 鉴权 + 通用异常脱敏 | 明文密码、越权访问、商家账号密码泄露、系统错误泄露内部细节 | 用户密码以 BCrypt 哈希保存，前端不再自动登录，`/demo/snapshot` 和 `/merchant/accounts/page` 仅 ADMIN 可访问，商家账号接口不返回密码 | 降低数据库泄露后的密码风险，阻止未授权用户查看平台快照和账号数据，减少内部信息暴露 |

## 关键接口

```text
GET  /market/home
GET  /stores?keyword=&category=&sort=
GET  /stores/page?keyword=&category=&sort=&page=&size=
GET  /stores/{id}
GET  /stores/{id}/products
POST /auth/login
POST /auth/logout
POST /marketing/coupons/{sceneCode}/claim
POST /orders
POST /user/orders/{id}/pay
POST /user/orders/{id}/confirm
POST /user/orders/{id}/review
POST /user/addresses
PATCH /user/addresses/{id}
DELETE /user/addresses/{id}
POST /user/addresses/{id}/default
GET  /merchant/accounts/page?keyword=&page=&size=  # ADMIN
GET  /merchant/store
PATCH /merchant/store
POST /merchant/store/open
POST /merchant/store/close
GET  /merchant/products/page?page=&size=
GET  /merchant/orders/page?page=&size=
POST /merchant/orders/{id}/accept
POST /merchant/orders/{id}/ready
GET  /rider/tasks/available
POST /rider/tasks/{id}/accept
POST /rider/tasks/{id}/arrive-store
POST /rider/tasks/{id}/pickup
POST /rider/tasks/{id}/deliver
GET  /admin/audit-logs
GET  /demo/snapshot  # ADMIN
```

## 验证命令

```powershell
cd E:\project\takeout\backend
.\mvnw.cmd test
$env:SPRING_PROFILES_ACTIVE="mysql"
.\mvnw.cmd test

cd E:\project\takeout\frontend
npm run build
```

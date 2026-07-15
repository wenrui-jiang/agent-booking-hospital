# Agent Tool Calling、记忆库与部署建议

## 1. 已调整的 Agent 方向

当前 `service_agent` 已从纯 workflow 优先，调整为：

1. 后端向大模型发送可用工具清单。
2. 模型自主选择工具名和参数。
3. Java 只执行白名单工具，并记录 tool call trace。
4. 工具结果回传模型，由模型生成最终自然语言回复。
5. 如果没有 API Key、模型不支持工具调用或远端失败，自动回退到原稳定 workflow。

已暴露的工具：

- `search_hospitals`
- `list_departments`
- `find_schedule_rules`
- `find_schedule_list`
- `list_patients`
- `submit_order`
- `get_order_info`
- `generate_pretriage_report`

`submit_order` 仍保留硬性保护：只有用户明确表达“确认挂号 / 提交订单 / 确认下单”时才允许执行。这个边界不能交给模型自由决定。

## 2. 千问 / 淘宝闪购类业务流程抽象

根据公开资料与通用电商 Agent 设计，淘宝闪购类“下单 Agent”更像平台内嵌交易助手，不是关键词路由。推荐抽象为：

1. 用户表达目标：如“帮我点一杯少糖奶茶”“附近能最快送到的感冒药”。
2. Agent 补齐约束：地址、品类、价格、送达时间、忌口/规格、优惠。
3. Agent 调商品/商家搜索工具：按地理位置、库存、营业状态、配送时效过滤。
4. Agent 调详情/库存/价格工具：校验规格、库存、配送费、预计送达。
5. Agent 生成候选方案：通常给 1 到 3 个可选项。
6. 用户确认商品和规格。
7. Agent 调购物车/预下单工具：返回订单金额、地址、支付前确认页。
8. 用户最终确认。
9. Agent 创建订单，但支付仍由用户在可信支付页完成。

映射到本项目：

- 商品搜索 -> 医院/科室/号源搜索。
- 商品规格 -> 医院、科室、日期、上午/下午、医生职称。
- 收货地址 -> 就诊人和登录态。
- 预下单 -> 挂号确认卡片。
- 最终下单 -> `submit_order`。

## 3. 向量数据库建议

本项目推荐用 PostgreSQL + pgvector，而不是再引入独立 Milvus/Qdrant：

- 项目规模小，Agent 记忆、工具调用、预诊摘要都可以在一个关系型库里建模。
- pgvector 能同时保留结构化字段和向量字段，方便按 `user_id`、`session_id`、时间范围过滤，避免多系统同步。
- 部署成本低：一台 VPS 上 Docker Compose 跑 Postgres 即可。
- 面试叙事清晰：短期上下文用最近 N 轮消息，长期记忆用向量相似检索召回。

不建议把所有聊天记录只丢进向量库。通行做法是分层：

- 完整聊天历史：关系表，便于审计、展示、删除。
- 短期上下文：最近 N 轮消息，直接放进模型上下文。
- 长期记忆：把症状摘要、偏好、历史挂号意图、工具结果摘要做 embedding，写入 pgvector。
- 工具调用轨迹：关系表，保留工具名、参数、结果摘要、耗时和状态。

## 4. 推荐表结构

见 `docs/yygh_agent_memory_pgvector_schema.sql`。

核心表：

- `agent_session`：会话主表。
- `agent_message`：完整消息历史。
- `agent_tool_call`：工具调用轨迹。
- `agent_memory_item`：长期记忆和向量 embedding。

## 5. OneDrive 清理边界

建议在 OneDrive 项目副本中保留：

- `backend/yygh_parent`
- `frontend/yygh-site`
- `docs`
- `scripts`
- `restart-all.bat`、`start-all.bat`、`stop-all.bat`
- 与简历/面试直接相关的文档

建议迁出或删除：

- 未被当前仓库引用的课程资料、压缩包、截图、第三方静态资源。
- `examples/` 下仅用于学习验证的示例工程。
- 所有 `target/`、`.nuxt/`、`node_modules/`、运行日志、crash dump。
- 医院接口模拟系统的旧课程资源副本；当前演示 seed 已迁入 `deploy/local-data/mongodb/yygh_hosp`。

清理前先做一次清单确认，不建议直接在 OneDrive 同步目录里批量删除未知资源。

## 6. VPS 建议

如果 Vultr 付款失败，优先换成腾讯云轻量应用服务器或阿里云轻量应用服务器：

- 大陆身份和支付方式更稳。
- 可选香港、新加坡、日本等区域，适合演示站点。
- 文档、备案/域名、账单和安全组对中文用户更友好。

如果只做海外演示且不依赖国内支付，可以再看 Hetzner、Linode/Akamai、DigitalOcean。但对你现在的项目发布，腾讯云/阿里云的支付成功率和中文运维体验更关键。

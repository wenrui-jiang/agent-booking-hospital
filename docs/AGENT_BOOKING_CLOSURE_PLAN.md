# 尚医通医院数据恢复与 Agent 挂号闭环计划

## Summary

项目使用 MongoDB 保存医院核心业务数据。`Hospital`、`Department`、`Schedule` 是 Mongo 集合，`service_hosp` 通过 `MongoRepository`/`MongoTemplate` 读写；MySQL 主要存平台配置、用户、就诊人、订单、医院签名配置等。当前前端 3000 看不到医院，不是因为没登录，而是 MongoDB 里的医院、科室、排班数据为空或未正确导入。

本地仓库已经包含医院模拟数据，不需要先联网搜索医院：MongoDB seed 位于 `deploy/local-data/mongodb/yygh_hosp`，`hospital-manage` 模拟医院系统位于 `backend/yygh_parent/hospital-manage`。

## Key Changes

- 每次继续任务先读取本文档再动态 replan。
- 启动并校验本机 MongoDB `127.0.0.1:27017/yygh_hosp`，确认 `service_hosp` 和 `service_order` 使用本地 MongoDB 覆盖配置。
- 导入 MySQL 平台基础表和初始化字典：`docs\sql\yygh表结构.sql`、`yygh初始化数据.sql`。
- 若需要模拟医院系统，使用 `backend/yygh_parent/hospital-manage` 模块。
- 优先使用 `deploy/local-data/mongodb/yygh_hosp/import-yygh-hosp-seed.js` 恢复演示医院数据。
- 若需要重新生成演示数据，可用 `scripts/import-yygh-sample-data.js` 把 `Hospital.json`、`Department.json`、`Schedule.json` 转为 `Hospital`、`Department`、`Schedule` 集合文档，并修正排班日期为当前可预约日期。
- 验证前端医院数据接口返回非空：`/api/hosp/hospital/findHospList/1/10`、`/api/hosp/hospital/department/{hoscode}`、`/api/hosp/hospital/auth/getBookingScheduleRule/1/7/{hoscode}/{depcode}`。
- 登录绕过采用开发模式固定短信验证码或 Redis 写入验证码；同时准备一个默认就诊人，保证 Agent 能完成挂号订单创建。
- Agent 挂号闭环暂不接支付：创建订单后直接返回“预约订单已创建，支付环节本地演示已跳过/模拟完成”，展示 `orderId`、医院、科室、时间、取号时间和取号地址。

## Agent Workflow

- 症状收集和科室推荐维持现有 DeepSeek Agent。
- 用户确认科室后，workflow 查询现有医院、科室、排班接口，不再反复只返回“推荐挂某科”。
- 缺少医院、日期、就诊人时，前端展示可选项，用户可点击选择，也可自然语言补充。
- 下单前必须展示确认卡片：医院、科室、医生/号源、日期、午别、费用、就诊人。
- 用户确认后调用现有 `submitOrder`；不调用微信支付接口。
- 预诊报告只在进入科室推荐/确认阶段生成一次，固定在聊天区域内随历史消息滚动，不再每次回复新增一个独立方框。

## Test Plan

- 打开 3000 首页能看到医院列表。
- 不登录时可浏览医院、科室、排班；下单时提示登录或自动使用开发登录。
- Agent 输入“咳嗽嗓子疼三天”，能推荐呼吸内科，并查询到匹配科室/号源。
- 用户说“就北京协和，明天上午，默认就诊人”，workflow 能补齐槽位并进入确认。
- 未确认前不创建订单；确认后创建订单并返回取号信息。
- 不出现微信支付二维码依赖；支付状态显示本地演示已跳过。
- 预诊报告只生成一次，后续聊天不重复堆叠报告卡片。

## Assumptions

- v1 不做真实支付，只做本地演示闭环。
- v1 不联网新增医院数据，优先使用课程自带模拟数据。
- 医院核心业务数据以 MongoDB 为准，不手写 SQL 插入医院列表。
- 如果示例排班日期过旧，会在导入阶段批量平移到当前日期之后，保证前端可以预约演示。

## Execution Notes - 2026-06-09

- 已采用兜底脚本 `scripts/import-yygh-sample-data.js` 导入课程示例数据到 MongoDB `yygh_hosp`：
  - `Hospital`: 1
  - `Department`: 288
  - `Schedule`: 239
- 已补充本地演示账号数据：
  - 用户手机号：`13900000000`
  - 固定验证码：`123456`
  - 默认就诊人：`patientId=1`
  - 默认医院：北京协和医院，`hoscode=1000_0`
- 已在本地 MySQL `yygh_order.order_info` 补充 `schedule_id` 字段，原因是当前 Java 模型默认写入 `schedule_id`，但本地 SQL 表结构只有 `hos_schedule_id`。
- `service_order` 通过启动参数 `--yygh.order.mock-hospital-submit=true` 跳过医院模拟系统真实下单接口，仍会创建平台订单并写入：
  - `hosRecordId=MOCK-{orderId}`
  - `fetchTime=就诊当日开诊前30分钟`
  - `fetchAddress=门诊楼一层挂号/收费窗口（本地演示）`
- Agent workflow 已验证：
  - 症状输入进入 `SYMPTOM_COLLECTING`
  - 补充“北京协和、明天上午、默认就诊人”后进入 `BOOKING_CONFIRMING`
  - 确认后进入 `VISIT_GUIDING`
  - 本地验证订单：`orderId=13`
- 修复了一个急症误判：用户说“没有胸闷和呼吸困难”时，不再因为出现“呼吸困难”关键词而进入急症兜底。
- 科室匹配从“第一个名称匹配”改为“同名候选中优先选择未来 7 天有号源的科室”，避免命中没有排班的国际医疗科室。

## Execution Notes - 登录开发模式与 Agent 工具化

- 前端短信接口已统一为 `/api/msm/send/{mobile}`，匹配 `service_msm` 和网关 `/*/msm/**` 路由。
- 前端手机号校验已从旧号段正则改为 `^1\d{10}$`，避免开发测试手机号被前端提前拦截。
- `service_msm` 新增开发短信模式：
  - `yygh.msm.dev-mode=true`
  - `yygh.msm.dev-code=123456`
  - 开启后不调用阿里云短信，直接把固定验证码写入 Redis 24 小时。
- `service_user` 登录逻辑不绕过，仍读取 Redis 验证码并生成 JWT token。
- Agent 已从“关键词触发接口”为主，升级为“模型 planner 建议 + workflow 安全执行”的混合工具编排：
  - DeepSeek 返回扩展字段：`intent`、`stageSuggestion`、`slots`、`nextAction`、`toolIntent`、`answerDraft`。
  - `toolIntent` 只允许白名单建议：`NONE`、`SEARCH_HOSPITALS`、`LIST_DEPARTMENTS`、`SEARCH_SCHEDULE`、`LIST_PATIENTS`、`SUBMIT_ORDER`、`GET_ORDER_INFO`、`GENERATE_PRETRIAGE_REPORT`。
  - workflow 会合并模型抽取的槽位，并在 `SEARCH_SCHEDULE` 建议下进入查号源流程。
  - `SUBMIT_ORDER` 仍必须由用户显式“确认挂号/提交订单/确认下单”触发，不允许模型单方面下单。
- v2 可继续演进为更完整的模型自主 tool planner，但 v1 保持稳定可演示。

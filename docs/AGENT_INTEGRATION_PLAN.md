# 尚医通智能导诊 Agent 接入方案

状态：v1 已收敛为智能导诊挂号闭环，取消发票/报销 Agent。

## 1. 目标

在不大改原尚医通业务服务的前提下，新增 `service_agent`，让患者可以通过对话完成：

- 描述症状；
- Agent 多轮追问；
- Agent 给出科室建议；
- workflow 查询医院、科室、排班、就诊人；
- 用户确认后提交挂号订单；
- 生成支付引导；
- 自动整理预诊报告，供医生复制参考。

核心原则：

- Agent 负责自然语言理解、追问、科室建议、预诊报告生成。
- Workflow 负责状态机、槽位完整性、登录态、确认保护、工具调用。
- Tools 只封装现有微服务 API，不让模型直接访问数据库。

## 2. v1 范围

### 2.1 智能导诊挂号

复用原项目能力：

- `service_hosp`：医院列表、医院详情、科室树、排班规则、排班列表、排班详情。
- `service_user`：登录态、就诊人列表。
- `service_order`：提交订单、订单详情、支付二维码、支付状态。
- `service_cmn`：字典数据。
- `OrderInfo.fetchTime`、`OrderInfo.fetchAddress`：预约成功后的取号时间和取号地点提示。

workflow 状态：

- `SYMPTOM_COLLECTING`：追问主诉、部位、持续时间、严重程度、伴随症状、既往史等。
- `DEPARTMENT_RECOMMENDING`：给出 1-3 个科室建议和推荐理由。
- `BOOKING_SEARCHING`：调用工具查询医院、科室、排班。
- `BOOKING_CONFIRMING`：展示挂号草稿，等待用户确认。
- `ORDER_SUBMITTING`：确认后调用订单接口。
- `PAYMENT_GUIDING`：生成支付二维码或查询支付状态。
- `VISIT_GUIDING`：返回取号时间、地点和就诊注意事项。
- `EMERGENCY_GUIDING`：急症关键词触发线下急诊/120 兜底。

### 2.2 预诊报告

v1 采用“结构化 JSON + 可复制 Markdown/文本”，不做 PDF 模板。

原因：

- 当前没有可靠医院文书模板；
- JSON 方便接口传递、编辑、后续落库；
- 文本方便医生复制到病历系统。

报告字段：

- `chiefComplaint`：主诉。
- `symptomSummary`：症状摘要。
- `duration`：持续时间。
- `severity`：严重程度。
- `accompanyingSymptoms`：伴随症状。
- `negativeSymptoms`：未提及或否认信息。
- `patientContext`：年龄、性别、就诊人 ID。
- `departmentRecommendation`：主推荐科室、备选科室、理由。
- `bookingDraft`：医院、科室、日期、时间段、排班。
- `doctorCopyText`：医生可复制文本。

当前实现：

- `service_agent` 使用内存存储会话、报告、工具调用记录，便于本地快速演示。
- 文档提供 `yygh_agent` 表结构草稿，后续可替换为 MyBatis 持久化。

## 3. 已实现接口

`service_agent` 端口：`8210`。

通过网关暴露：

```http
POST /api/agent/chat
GET  /api/agent/session/{sessionId}
GET  /api/agent/pretriage-report/{sessionId}
POST /api/agent/pretriage-report/{sessionId}/confirm
GET  /api/agent/tool-calls/{sessionId}
```

聊天请求：

```json
{
  "sessionId": "optional",
  "message": "我最近咳嗽嗓子疼三天了，有点发热"
}
```

聊天响应：

```json
{
  "sessionId": "xxx",
  "intent": "TRIAGE_BOOKING",
  "stage": "DEPARTMENT_RECOMMENDING",
  "answer": "我建议优先挂呼吸内科...",
  "slots": {
    "symptoms": ["咳嗽", "咽痛", "发热"],
    "duration": "三天",
    "severity": "轻",
    "department": "呼吸内科"
  },
  "actions": [
    {
      "type": "CONFIRM_DEPARTMENT",
      "label": "确认科室并查号"
    }
  ],
  "pretriageReportPreview": {
    "chiefComplaint": "咳嗽、咽痛、发热三天",
    "departmentRecommendation": "呼吸内科"
  }
}
```

## 4. 工具白名单

已规划/封装的工具：

- `searchHospitals`
- `getHospitalDetail`
- `listDepartments`
- `findScheduleRules`
- `findScheduleList`
- `getSchedule`
- `listPatients`
- `submitOrder`
- `getOrderInfo`
- `createPaymentQrCode`
- `queryPaymentStatus`
- `generatePretriageReport`
- `savePretriageReport`

当前实现中，工具调用通过 Feign 访问原微服务；调用结果、耗时、失败原因会记录到 tool call 列表。

## 5. 前端入口

患者端 `yygh-site` 新增右下角浮动窗口：

- 标题：智能导诊 Agent。
- 引导语：您直接告诉我哪里不舒服，我来指导您就医。
- 支持多轮聊天、操作按钮、预诊报告预览。
- 登录后会自动携带原项目 `token` 请求头。

## 6. Waiting List

不进入 v1，但作为后续扩展：

- 预约成功后院内导航：取号窗口、楼层、电梯、报到点、候诊区。
- 候诊叫号提醒：对接医院排队叫号系统后触发主动提醒。
- 就诊后指导：读取医生诊断单、处方、检查单后解释下一步。
- 材料打印指导：病历、检查报告、报销材料、发票等位置和优先级说明。
- 院内地图/RAG：维护医院楼宇、科室位置、窗口规则知识库。

## 7. 测试场景

- 输入“我最近咳嗽嗓子疼”，Agent 应追问持续时间、发热、胸闷/呼吸困难等。
- 输入“三天了，有点发热”，Agent 应推荐呼吸内科并生成预诊报告。
- 点击“确认科室并查号”，workflow 应进入 `BOOKING_CONFIRMING` 并记录工具调用。
- 未登录或缺少排班/就诊人时，点击“确认挂号”不能直接下单。
- 输入胸痛、严重呼吸困难、意识不清等，应进入急症兜底。

## 8. 面试表达

可以这样讲：

“我没有把大模型调用散落到原业务 Controller 里，而是新增了 `service_agent`。Agent 负责对话理解和多轮追问，workflow 负责状态机和确认保护，原医院、用户、订单服务被封装成白名单工具。这样既能演示智能导诊、科室推荐、挂号确认和预诊报告，又不会破坏原有预约挂号链路。后续如果接入真实 LLM 或向量库，只需要替换 Agent 的理解和生成层，工具和 workflow 不需要大改。”

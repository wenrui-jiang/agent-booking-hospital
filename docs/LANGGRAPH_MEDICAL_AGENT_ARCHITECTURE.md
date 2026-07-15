# 网页内医疗挂号 Agent LangGraph 方案

## 定位

本项目按“演示版项目、真实业务版能力边界”建设：

- 前端仍通过 `/api/agent/chat` 进入，不让浏览器直接调用 Python Agent。
- Java `service_agent` 保留为业务工具网关，负责登录态、就诊人、号源、订单、确认保护和 tool trace。
- Python `agent-langgraph` 真实接入 LangGraph，负责状态图、规划、RAG 检索、医疗安全节点和回复生成。
- DeepSeek 继续作为大模型，配置沿用 `DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL`。
- pgvector 用于医疗知识、医院规则、医生简介和脱敏长期记忆。
- 支付流程在演示环境跳过，只创建挂号订单；订单创建必须用户二次确认。

## 环境研判

当前服务器资源：

- CPU：2 核。
- 内存：7.6GiB，总体可用约 2.3GiB。
- 磁盘：约 83GiB 可用。
- Docker / Docker Compose 可用。

结论：可以接入 pgvector 和轻量 Python LangGraph 服务。建议：

- pgvector 单独容器运行，不在 MySQL 中硬塞向量能力。
- embedding 不在本机部署大模型，优先用远程 embedding API 或先使用文本检索兜底。
- Python Agent 容器化部署，避免依赖宿主机 pip；当前宿主机没有 `pip3`。

## 状态图

LangGraph 节点：

1. `input_guard`：拦截诊断、处方、用药剂量等越界请求。
2. `intent_router`：识别导诊挂号、订单查询、确认下单等意图。
3. `memory_retriever`：召回用户长期偏好。
4. `medical_knowledge_retriever`：召回医疗/科室/医生/医院规则知识。
5. `symptom_collector`：抽取症状、持续时间、严重程度、医院、日期、上午/下午等槽位。
6. `emergency_checker`：识别急症并拦截普通挂号路径。
7. `department_recommender`：推荐科室，不做疾病诊断。
8. `hospital_selector`：调用 Java 白名单工具检索医院。
9. `doctor_schedule_searcher`：查询科室、可预约日期和号源。
10. `patient_checker`：检查登录态和就诊人。
11. `booking_confirmer`：生成确认卡，等待二次确认。
12. `order_submitter`：用户确认后调用 Java 下单工具。
13. `report_and_memory_writer`：生成预诊报告，后续写入脱敏长期记忆。

## 安全边界

- 可以做：导诊、科室推荐、挂号查询、订单创建、预诊摘要、就诊指引。
- 不可以做：疾病确诊、处方、药物剂量、替代医生判断、急症普通预约。
- 急症信号：胸痛、严重呼吸困难、意识障碍、大出血、抽搐、突发偏瘫、剧烈头痛等。
- 隐私策略：身份证号、手机号、支付信息、完整病历原文不进入向量库；长期记忆只保存脱敏摘要和偏好。
- `submit_order` 永远由 Java 后端强校验：登录态、就诊人、号源、用户明确确认。

## pgvector 内容

向量化：

- 科室知识。
- 常见症状与科室映射。
- 医院就诊须知。
- 挂号规则。
- 医生简介模拟数据或官网抓取后人工确认数据。
- 脱敏会话摘要。
- 用户长期偏好。

不向量化：

- 身份证号。
- 手机号。
- 支付信息。
- 原始完整病历。
- 不必要的敏感原文。

## 部署开关

Java Agent 默认不强依赖 LangGraph：

```properties
agent.langgraph.enabled=${YYGH_LANGGRAPH_ENABLED:false}
agent.langgraph.base-url=${YYGH_LANGGRAPH_BASE_URL:http://127.0.0.1:8211}
```

启用时：

```bash
export YYGH_LANGGRAPH_ENABLED=true
export YYGH_LANGGRAPH_BASE_URL=http://127.0.0.1:8211
export DEEPSEEK_API_KEY=...
```

pgvector 初始化：

```bash
psql "$PGVECTOR_DSN" -f docs/yygh_agent_pgvector_schema.sql
python3 scripts/import-agent-knowledge-pgvector.py \
  --file agent-langgraph/data/medical_knowledge_seed.jsonl \
  --file deploy/local-data/agent-knowledge/doctor_profiles_seed.jsonl
```

网页抓取采用白名单脚本：

```bash
python3 scripts/crawl-agent-knowledge.py \
  --url https://www.pumch.cn/ \
  --hospital-code 1000_0 \
  --hospital-name 北京协和医院 \
  --output deploy/local-data/agent-knowledge/crawled_knowledge.jsonl
```

抓取结果默认带 `requires_review=true`，应人工检查后再导入。

import json
import os
import re
from datetime import date, datetime, timedelta
from typing import Any, Dict, List, Optional, TypedDict

import httpx
import psycopg
from fastapi import FastAPI
from langgraph.graph import END, StateGraph
from pgvector.psycopg import register_vector
from pydantic import BaseModel, Field


DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/")
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-v4-pro")
JAVA_AGENT_BASE_URL = os.getenv("JAVA_AGENT_BASE_URL", "http://127.0.0.1:8210").rstrip("/")
PGVECTOR_DSN = os.getenv("PGVECTOR_DSN", "")
AGENT_INTERNAL_SECRET = os.getenv("YYGH_AGENT_INTERNAL_SECRET", "local-dev-agent-secret")


class ChatMessage(BaseModel):
    role: str = ""
    content: str = ""


class ChatRequest(BaseModel):
    sessionId: str
    userId: Optional[int] = None
    intent: str = "TRIAGE_BOOKING"
    stage: str = "SYMPTOM_COLLECTING"
    slots: Dict[str, Any] = Field(default_factory=dict)
    latestMessage: str = ""
    token: Optional[str] = None
    messages: List[ChatMessage] = Field(default_factory=list)


class AgentState(TypedDict, total=False):
    session_id: str
    user_id: Optional[int]
    intent: str
    stage: str
    slots: Dict[str, Any]
    latest_message: str
    token: Optional[str]
    messages: List[Dict[str, Any]]
    answer: str
    actions: List[Dict[str, Any]]
    agent_steps: List[Dict[str, Any]]
    tool_traces: List[Dict[str, Any]]
    booking_card: Dict[str, Any]
    safety_card: Dict[str, Any]
    pretriage_report_preview: Dict[str, Any]
    medical_context: List[Dict[str, Any]]
    user_memory: List[Dict[str, Any]]
    emergency: bool
    missing_slots: List[str]


app = FastAPI(title="YYGH LangGraph Medical Booking Agent")


def step(state: AgentState, node: str, summary: str, status: str = "done") -> None:
    state.setdefault("agent_steps", []).append({
        "node": node,
        "summary": summary,
        "status": status,
    })


def safety_card() -> Dict[str, Any]:
    return {
        "title": "医疗安全边界",
        "scope": "我只做导诊、挂号辅助、预诊摘要和就诊指引，不做疾病确诊、处方或用药决策。",
        "emergency": "胸痛、严重呼吸困难、意识障碍、大出血、抽搐、突发偏瘫等情况应优先急诊或拨打 120。",
        "privacy": "身份证号、手机号、支付信息、完整病历原文不写入向量库；长期记忆只保存脱敏摘要和偏好。",
    }


def call_deepseek_json(system_prompt: str, user_payload: Dict[str, Any]) -> Dict[str, Any]:
    if not DEEPSEEK_API_KEY:
        return {}
    payload = {
        "model": DEEPSEEK_MODEL,
        "stream": False,
        "temperature": 0.2,
        "max_tokens": 900,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": json.dumps(user_payload, ensure_ascii=False)},
        ],
    }
    with httpx.Client(timeout=20) as client:
        response = client.post(
            f"{DEEPSEEK_BASE_URL}/chat/completions",
            headers={"Authorization": f"Bearer {DEEPSEEK_API_KEY}"},
            json=payload,
        )
        response.raise_for_status()
        content = response.json()["choices"][0]["message"].get("content") or "{}"
        return json.loads(content)


def call_deepseek_chat(messages: List[Dict[str, Any]],
                       tools: Optional[List[Dict[str, Any]]] = None,
                       temperature: float = 0.45,
                       max_tokens: int = 1400) -> Dict[str, Any]:
    if not DEEPSEEK_API_KEY:
        return {}
    payload: Dict[str, Any] = {
        "model": DEEPSEEK_MODEL,
        "stream": False,
        "temperature": temperature,
        "max_tokens": max_tokens,
        "thinking": {"type": "disabled"},
        "messages": messages,
    }
    if tools:
        payload["tools"] = tools
        payload["tool_choice"] = "auto"
    with httpx.Client(timeout=30) as client:
        response = client.post(
            f"{DEEPSEEK_BASE_URL}/chat/completions",
            headers={"Authorization": f"Bearer {DEEPSEEK_API_KEY}"},
            json=payload,
        )
        response.raise_for_status()
        return response.json()["choices"][0].get("message") or {}


def agent_system_prompt() -> str:
    return (
        "你是尚医通项目里的对话式医疗预约 Agent。你的体验要接近 ChatGPT："
        "先自然理解用户，多轮对话中承接上下文；必要时调用工具查询真实医院、科室、号源、就诊人和订单。"
        "不要机械复述固定模板，不要把内部 slot 名称暴露给用户。"
        "你可以做导诊、挂号辅助、预诊摘要和就诊指引，但不能确诊疾病、开处方或给具体用药剂量。"
        "如果用户描述胸痛、严重呼吸困难、意识障碍、大出血、抽搐、突发偏瘫等急症风险，"
        "请直接建议急诊或 120，不要继续普通挂号流程。"
        "当用户想挂号但缺少医院、日期、上午/下午、就诊人等信息时，用一句自然的话追问最关键的缺口；"
        "当信息足够时，你应调用工具查号源。"
        "如果当前账号没有就诊人，但用户提供了实名/就诊人信息，可以调用 create_patient 新增；缺少姓名、证件类型、证件号、性别、出生日期、手机号时先提示补全。"
        "只有用户明确说确认挂号、提交订单、确认下单时，才允许调用 submit_order。"
        "工具结果是什么就说什么，不能编造医生、时间或订单。中文回答，语气专业、温和、简洁。"
    )


def agent_tool_schemas() -> List[Dict[str, Any]]:
    def obj(properties: Dict[str, Any]) -> Dict[str, Any]:
        return {"type": "object", "properties": properties}

    def string(description: str) -> Dict[str, str]:
        return {"type": "string", "description": description}

    def number(description: str) -> Dict[str, str]:
        return {"type": "number", "description": description}

    return [
        {
            "type": "function",
            "function": {
                "name": "getCurrentDateTime",
                "description": "获取当前日期、时间、星期和时区。用户问今天日期或需要解析相对日期时先调用。",
                "parameters": obj({}),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "normalizeDate",
                "description": "把用户输入的日期表达如 2026.7.9、7月9日、明天转成 YYYY-MM-DD。",
                "parameters": obj({"text": string("用户日期表达")}),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "calculateDate",
                "description": "计算明天、后天、下周一、最近几天等相对日期。",
                "parameters": obj({"text": string("相对日期表达")}),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "updateSlots",
                "description": "保存当前会话已知槽位，如 city、hosname、workDate、workTime、depname、patient、symptoms。",
                "parameters": obj({"slots": {"type": "object", "description": "要合并的槽位"}}),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "conversationMemory",
                "description": "读取或写入当前会话槽位和最近消息。",
                "parameters": obj({"slots": {"type": "object", "description": "可选，要合并的槽位"}}),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "summarizeConversation",
                "description": "返回当前 slots 和最近对话摘要，历史较长时使用。",
                "parameters": obj({}),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "confirmBeforeAction",
                "description": "关键动作前要求用户确认，如提交订单或修改资料。",
                "parameters": obj({"action": string("待确认动作")}),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "search_hospitals",
                "description": "按医院名称或地区检索医院。用户指定医院或需要选择医院时调用。",
                "parameters": obj({
                    "hosname": string("医院名称，可为空"),
                    "districtCode": string("地区编码，可为空"),
                }),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "list_departments",
                "description": "查询指定医院的科室树，获取 depcode 后才能查排班。",
                "parameters": obj({"hoscode": string("医院编码，例如 1000_0")}),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "find_schedule_rules",
                "description": "查询某医院某科室未来几天是否有号源。",
                "parameters": obj({
                    "hoscode": string("医院编码"),
                    "depcode": string("科室编码"),
                }),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "find_schedule_list",
                "description": "查询某医院某科室某一天的具体号源列表。",
                "parameters": obj({
                    "hoscode": string("医院编码"),
                    "depcode": string("科室编码"),
                    "workDate": string("日期，格式 yyyy-MM-dd"),
                }),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "list_patients",
                "description": "查询当前登录账号下的就诊人。提交挂号前必须调用。",
                "parameters": obj({}),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "create_patient",
                "description": "根据用户提供的实名/就诊人信息新增就诊人。缺少姓名、证件类型、证件号、性别、出生日期、手机号时先追问补全。",
                "parameters": obj({
                    "name": string("就诊人姓名，必填"),
                    "certificatesType": string("证件类型，身份证填 10，必填"),
                    "certificatesNo": string("证件号码，必填"),
                    "sex": number("性别，1 男，0 女，必填"),
                    "birthdate": string("出生日期，格式 yyyy-MM-dd，必填"),
                    "phone": string("手机号，必填"),
                    "isMarry": number("是否已婚，1 是，0 否，可选"),
                    "isInsure": number("是否有医保，1 是，0 否，可选"),
                    "address": string("详细地址，可选"),
                    "contactsName": string("联系人姓名，可选"),
                    "contactsPhone": string("联系人手机号，可选"),
                }),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "submit_order",
                "description": "创建挂号订单。只有用户明确二次确认挂号后才能调用。",
                "parameters": obj({
                    "scheduleId": string("排班号源 id"),
                    "patientId": number("就诊人 id"),
                }),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "get_order_info",
                "description": "查询已创建挂号订单详情。",
                "parameters": obj({"orderId": number("订单 id")}),
            },
        },
        {
            "type": "function",
            "function": {
                "name": "generate_pretriage_report",
                "description": "生成当前会话的预诊摘要，用于给医生查看。",
                "parameters": obj({
                    "department": string("推荐科室"),
                    "reason": string("推荐理由"),
                }),
            },
        },
    ]


def parse_tool_arguments(raw: Any) -> Dict[str, Any]:
    if isinstance(raw, dict):
        return raw
    if not raw:
        return {}
    try:
        return json.loads(raw)
    except Exception:
        return {}


def current_datetime_payload() -> Dict[str, Any]:
    now = datetime.now().astimezone()
    return {
        "date": now.date().isoformat(),
        "time": now.replace(microsecond=0).time().isoformat(),
        "dateTime": now.replace(microsecond=0).isoformat(),
        "dayOfWeek": now.strftime("%A"),
        "timezone": now.tzinfo.tzname(now) if now.tzinfo else "local",
    }


def normalize_date_text(text: str, base: Optional[date] = None) -> Optional[str]:
    if not text:
        return None
    base = base or date.today()
    match = re.search(r"(20\d{2})[.\-/年](\d{1,2})[.\-/月](\d{1,2})日?", text)
    if match:
        return date(int(match.group(1)), int(match.group(2)), int(match.group(3))).isoformat()
    match = re.search(r"(\d{1,2})月(\d{1,2})日?", text)
    if match:
        return date(base.year, int(match.group(1)), int(match.group(2))).isoformat()
    if "今天" in text:
        return base.isoformat()
    if "明天" in text or "最近" in text:
        return (base + timedelta(days=1)).isoformat()
    if "后天" in text:
        return (base + timedelta(days=2)).isoformat()
    match = re.search(r"下周([一二三四五六日天])", text)
    if match:
        mapping = {"一": 0, "二": 1, "三": 2, "四": 3, "五": 4, "六": 5, "日": 6, "天": 6}
        target = mapping[match.group(1)]
        delta = target - base.weekday()
        if delta <= 0:
            delta += 7
        return (base + timedelta(days=delta)).isoformat()
    return None


STAGE_RANK = {
    "SYMPTOM_COLLECTING": 10,
    "DEPARTMENT_RECOMMENDING": 20,
    "BOOKING_INFO_COLLECTING": 30,
    "BOOKING_SEARCHING": 40,
    "BOOKING_CONFIRMING": 50,
    "VISIT_GUIDING": 60,
    "AGENT_CHATTING": 10,
}


def stage_rank(stage: Optional[str]) -> int:
    return STAGE_RANK.get(stage or "", 0)


def is_restart_request(text: str) -> bool:
    return any(word in (text or "") for word in ["重新开始", "重来", "清空", "从头来"])


def merge_slots_from_text(slots: Dict[str, Any], text: str) -> None:
    if not text:
        return
    if "北京" in text:
        slots.setdefault("city", "北京")
    if "协和" in text:
        slots["hosname"] = "北京协和医院"
        slots.setdefault("hospitalName", "北京协和医院")
        slots.setdefault("hoscode", "1000_0")
        slots.setdefault("city", "北京")

    normalized_date = normalize_date_text(text)
    if normalized_date:
        slots["workDate"] = normalized_date
        slots["workDateText"] = normalized_date

    if "上午" in text:
        slots["workTime"] = "上午"
    elif "下午" in text:
        slots["workTime"] = "下午"
    elif any(word in text for word in ["无所谓", "不限", "都可以", "上午下午都行", "时间不限"]):
        slots["workTime"] = "不限"

    if any(word in text for word in ["我自己", "本人", "就诊人是我", "给我挂"]):
        slots["patient"] = "本人"
        slots["patientRelation"] = "self"

    department_aliases = [
        ("心内科", "心内科"),
        ("心血管内科", "心血管内科"),
        ("心脏内科", "心内科"),
        ("神经内科", "神经内科"),
        ("普通内科", "普通内科"),
        ("内科", "普通内科"),
        ("精神心理科", "精神心理科"),
        ("心理科", "精神心理科"),
        ("睡眠门诊", "睡眠门诊"),
    ]
    for word, department in department_aliases:
        if word in text:
            slots["depname"] = department
            slots["department"] = department
            break

    symptom_keywords = [
        "失眠", "睡不着", "心跳快", "心悸", "压力", "焦虑", "头痛", "头晕",
        "胸闷", "胸痛", "咳嗽", "发热", "腹痛", "腹泻", "胃痛",
    ]
    symptoms = slots.get("symptoms")
    if isinstance(symptoms, str):
        symptoms = [symptoms]
    if not isinstance(symptoms, list):
        symptoms = []
    for keyword in symptom_keywords:
        if keyword in text and keyword not in symptoms:
            symptoms.append(keyword)
    if symptoms:
        slots["symptoms"] = symptoms


def merge_slots_from_messages(slots: Dict[str, Any], messages: List[Dict[str, Any]], latest_message: str) -> None:
    for message in messages[-18:]:
        if message.get("role") == "user":
            merge_slots_from_text(slots, message.get("content") or "")
    merge_slots_from_text(slots, latest_message or "")


def infer_forward_stage(previous_stage: Optional[str], slots: Dict[str, Any], latest_message: str) -> str:
    if is_restart_request(latest_message):
        return "SYMPTOM_COLLECTING"
    if slots.get("orderId"):
        target = "VISIT_GUIDING"
    elif slots.get("scheduleId"):
        target = "BOOKING_CONFIRMING"
    elif slots.get("depname") and slots.get("hosname") and slots.get("workDate"):
        target = "BOOKING_SEARCHING"
    elif slots.get("depname"):
        target = "BOOKING_INFO_COLLECTING"
    elif slots.get("symptoms"):
        target = "DEPARTMENT_RECOMMENDING"
    else:
        target = "SYMPTOM_COLLECTING"
    previous = previous_stage or "SYMPTOM_COLLECTING"
    return target if stage_rank(target) >= stage_rank(previous) else previous


def missing_booking_slots(slots: Dict[str, Any]) -> List[str]:
    missing: List[str] = []
    if not slots.get("depname"):
        missing.append("科室")
    if not slots.get("hosname"):
        missing.append("医院")
    if not slots.get("workDate"):
        missing.append("就诊日期")
    if not slots.get("patient") and not slots.get("patientId") and not slots.get("patientName"):
        missing.append("就诊人")
    return missing


def known_slots_sentence(slots: Dict[str, Any]) -> str:
    known: List[str] = []
    for key, label in [
        ("city", "城市"),
        ("hosname", "医院"),
        ("workDate", "日期"),
        ("workTime", "时间"),
        ("depname", "科室"),
        ("patient", "就诊人"),
        ("patientName", "就诊人"),
    ]:
        value = slots.get(key)
        if value:
            known.append(f"{label}{value}")
    symptoms = slots.get("symptoms")
    if symptoms:
        symptom_text = "、".join(symptoms) if isinstance(symptoms, list) else str(symptoms)
        known.append(f"症状{symptom_text}")
    return "、".join(known) if known else "暂无完整挂号信息"


def progress_instruction(state: AgentState) -> str:
    slots = state.get("slots", {})
    missing = missing_booking_slots(slots)
    return (
        "【会话推进约束】"
        f"当前阶段：{state.get('stage')}。已知信息：{known_slots_sentence(slots)}。"
        f"仍缺信息：{'、'.join(missing) if missing else '无'}。"
        "本轮必须沿当前阶段向前推进，不要因为用户再次提到医院、科室、日期等关键词而回到上一阶段。"
        "禁止重复询问已知信息；如果缺信息，只问缺口。"
        "如果科室、医院和日期都已知，应查询号源或说明正在查询/没有查到号源。"
    )


def answer_repeats_known_questions(answer: str, slots: Dict[str, Any]) -> bool:
    if not answer:
        return False
    hospital_known = bool(slots.get("hosname"))
    department_known = bool(slots.get("depname"))
    date_known = bool(slots.get("workDate"))
    if hospital_known and any(text in answer for text in ["哪个医院", "哪家医院", "指定的医院", "想去的医院", "医院吗"]):
        return True
    if department_known and any(text in answer for text in ["哪个科室", "想看哪个科室", "挂哪个科", "科室吗"]):
        return True
    if date_known and any(text in answer for text in ["哪天", "就诊日期", "什么时候看", "日期吗"]):
        return True
    return False


def forward_progress_answer(state: AgentState) -> str:
    slots = state.get("slots", {})
    missing = missing_booking_slots(slots)
    prefix = f"我已记录：{known_slots_sentence(slots)}。"
    if missing:
        return f"{prefix}现在还缺{'、'.join(missing)}，补齐后我就继续查询可预约号源。"
    if slots.get("scheduleId"):
        return f"{prefix}已找到可预约号源，请核对确认卡片；只有你再次确认后我才会提交挂号。"
    return f"{prefix}信息已经基本齐了，我现在继续帮你查询可预约号源。"


def normalize_messages(request: ChatRequest) -> List[Dict[str, Any]]:
    history: List[Dict[str, Any]] = [{"role": "system", "content": agent_system_prompt()}]
    recent = request.messages[-18:] if request.messages else []
    for message in recent:
        role = "assistant" if message.role == "assistant" else "user"
        content = (message.content or "").strip()
        if content:
            history.append({"role": role, "content": content})
    latest = (request.latestMessage or "").strip()
    if latest and not (history and history[-1]["role"] == "user" and history[-1]["content"] == latest):
        history.append({"role": "user", "content": latest})
    return history


def unwrap_tool_data(value: Any) -> Any:
    if isinstance(value, dict) and "data" in value:
        return value.get("data")
    return value


def first_object(value: Any) -> Optional[Dict[str, Any]]:
    value = unwrap_tool_data(value)
    if isinstance(value, dict) and isinstance(value.get("content"), list) and value["content"]:
        item = value["content"][0]
        return item if isinstance(item, dict) else None
    if isinstance(value, list) and value:
        item = value[0]
        return item if isinstance(item, dict) else None
    if isinstance(value, dict):
        return value
    return None


def result_list(value: Any) -> List[Dict[str, Any]]:
    value = unwrap_tool_data(value)
    if isinstance(value, dict) and isinstance(value.get("content"), list):
        value = value.get("content")
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, dict)]


def update_slots_from_tool_result(state: AgentState, tool_name: str, result: Any) -> None:
    slots = state.setdefault("slots", {})
    if tool_name == "search_hospitals":
        hospital = first_object(result)
        if hospital:
            slots["hoscode"] = hospital.get("hoscode") or slots.get("hoscode")
            slots["hosname"] = hospital.get("hosname") or slots.get("hosname")
    elif tool_name == "list_departments":
        preferred = slots.get("depname") or slots.get("department") or ""
        selected = None
        for group in result_list(result):
            children = group.get("children") if isinstance(group.get("children"), list) else []
            for child in children:
                if not isinstance(child, dict):
                    continue
                name = child.get("depname") or ""
                if preferred and (preferred in name or name in preferred):
                    selected = child
                    break
            if selected:
                break
        if not selected:
            for group in result_list(result):
                children = group.get("children") if isinstance(group.get("children"), list) else []
                selected = next((child for child in children if isinstance(child, dict)), None)
                if selected:
                    break
        if selected:
            slots["depcode"] = selected.get("depcode") or slots.get("depcode")
            slots["depname"] = selected.get("depname") or slots.get("depname")
            slots["department"] = selected.get("depname") or slots.get("department")
    elif tool_name == "find_schedule_rules":
        data = unwrap_tool_data(result)
        days = data.get("bookingScheduleList") if isinstance(data, dict) else []
        if isinstance(days, list) and days:
            first = next((item for item in days if isinstance(item, dict)), None)
            if first:
                slots["workDate"] = slots.get("workDate") or first.get("workDate")
    elif tool_name == "find_schedule_list":
        schedules = result_list(result)
        schedule = next((item for item in schedules if item.get("availableNumber", 1) != 0), None)
        if schedule:
            slots["scheduleId"] = schedule.get("id") or slots.get("scheduleId")
            slots["doctorName"] = schedule.get("docname") or slots.get("doctorName")
            slots["title"] = schedule.get("title") or slots.get("title")
            slots["amount"] = schedule.get("amount") or slots.get("amount")
            work_time = schedule.get("workTime")
            if work_time is not None:
                slots["workTime"] = "下午" if str(work_time) == "1" else "上午"
            state["stage"] = "BOOKING_CONFIRMING"
    elif tool_name == "list_patients":
        patient = first_object(result)
        if patient:
            slots["patientId"] = patient.get("id") or slots.get("patientId")
            slots["patientName"] = patient.get("name") or slots.get("patientName")
    elif tool_name == "create_patient":
        data = unwrap_tool_data(result)
        patient = data.get("patient") if isinstance(data, dict) else None
        if isinstance(patient, dict):
            slots["patientId"] = patient.get("id") or slots.get("patientId")
            slots["patientName"] = patient.get("name") or slots.get("patientName")
    elif tool_name == "submit_order":
        data = unwrap_tool_data(result)
        if isinstance(data, dict):
            slots["orderId"] = data.get("id") or data.get("orderId") or slots.get("orderId")
        elif isinstance(data, (int, float, str)):
            slots["orderId"] = data
        state["stage"] = "VISIT_GUIDING"
    elif tool_name == "generate_pretriage_report":
        data = unwrap_tool_data(result)
        if isinstance(data, dict):
            state["pretriage_report_preview"] = data


def infer_actions(state: AgentState) -> List[Dict[str, Any]]:
    slots = state.get("slots", {})
    stage = state.get("stage", "")
    if stage == "VISIT_GUIDING":
        return [{"type": "VIEW_ORDER", "label": "查看订单", "payload": slots.get("orderId")}]
    if stage == "BOOKING_CONFIRMING" and slots.get("scheduleId"):
        return [{"type": "CONFIRM_SUBMIT_ORDER", "label": "确认挂号"}]
    return []


def build_agentic_response(request: ChatRequest) -> Dict[str, Any]:
    request_messages = [m.model_dump() for m in request.messages]
    state: AgentState = {
        "session_id": request.sessionId,
        "user_id": request.userId,
        "intent": request.intent,
        "stage": request.stage or "AGENT_CHATTING",
        "slots": request.slots or {},
        "latest_message": request.latestMessage,
        "token": request.token,
        "messages": request_messages,
        "agent_steps": [],
        "tool_traces": [],
        "actions": [],
        "booking_card": {},
        "pretriage_report_preview": {},
        "safety_card": safety_card(),
    }
    merge_slots_from_messages(state["slots"], request_messages, request.latestMessage)
    state["stage"] = infer_forward_stage(request.stage or "SYMPTOM_COLLECTING", state["slots"], request.latestMessage)
    llm_messages = normalize_messages(request)
    llm_messages.append({"role": "system", "content": progress_instruction(state)})
    tools = agent_tool_schemas()
    final_message: Dict[str, Any] = {}

    for turn in range(6):
        step(state, "llm_turn", f"模型推理第 {turn + 1} 轮，按需选择工具。")
        final_message = call_deepseek_chat(llm_messages, tools=tools)
        tool_calls = final_message.get("tool_calls") or []
        if not tool_calls:
            break
        llm_messages.append({
            "role": "assistant",
            "content": final_message.get("content") or "",
            "tool_calls": tool_calls,
        })
        for raw_call in tool_calls:
            function = raw_call.get("function") or {}
            tool_name = function.get("name")
            if not tool_name:
                continue
            arguments = parse_tool_arguments(function.get("arguments"))
            step(state, "tool_call", f"调用本地工具：{tool_name}。")
            result = execute_tool(state, tool_name, arguments)
            update_slots_from_tool_result(state, tool_name, result)
            state["stage"] = infer_forward_stage(state.get("stage"), state.get("slots", {}), state.get("latest_message", ""))
            llm_messages.append({
                "role": "tool",
                "tool_call_id": raw_call.get("id") or tool_name,
                "name": tool_name,
                "content": text_limit(result, 3000),
            })
    else:
        step(state, "finalizer", "工具循环达到上限，要求模型基于已有结果收束回复。")
        llm_messages.append({
            "role": "user",
            "content": "请基于以上对话和工具结果，用自然中文给出本轮最终回复；不要继续调用工具。",
        })
        final_message = call_deepseek_chat(llm_messages, tools=None, temperature=0.35, max_tokens=1000)

    answer = (final_message.get("content") or "").strip()
    if not answer:
        answer = "我已经看到了你的信息。你可以继续补充症状、想去的医院或就诊时间，我会结合本地号源继续帮你处理。"
    state["stage"] = infer_forward_stage(state.get("stage"), state.get("slots", {}), state.get("latest_message", ""))
    if answer_repeats_known_questions(answer, state.get("slots", {})):
        answer = forward_progress_answer(state)
        step(state, "progress_guard", "模型回复重复询问已知槽位，已改写为前向推进回复。")
    state["answer"] = answer
    state["actions"] = infer_actions(state)
    step(state, "assistant_response", "已生成自然语言回复。")
    return {
        "sessionId": request.sessionId,
        "intent": state.get("intent", request.intent),
        "stage": state.get("stage", request.stage),
        "answer": state.get("answer"),
        "slots": state.get("slots", {}),
        "actions": state.get("actions", []),
        "agentSteps": state.get("agent_steps", []),
        "toolTraces": state.get("tool_traces", []),
        "bookingCard": state.get("booking_card", {}),
        "safetyCard": state.get("safety_card", safety_card()),
        "pretriageReportPreview": state.get("pretriage_report_preview", {}),
    }


def execute_tool(state: AgentState, tool_name: str, arguments: Dict[str, Any]) -> Any:
    if tool_name == "getCurrentDateTime":
        result = current_datetime_payload()
        state.setdefault("tool_traces", []).append({"toolName": tool_name, "arguments": {}, "status": "SUCCESS", "resultSummary": text_limit(result)})
        return result
    if tool_name in ["normalizeDate", "calculateDate"]:
        text = arguments.get("text") or state.get("latest_message", "")
        normalized = normalize_date_text(text)
        if normalized:
            state.setdefault("slots", {})["workDate"] = normalized
        result = {"input": text, "date": normalized, "currentDateTime": current_datetime_payload()}
        state.setdefault("tool_traces", []).append({"toolName": tool_name, "arguments": arguments or {}, "status": "SUCCESS", "resultSummary": text_limit(result)})
        return result
    if tool_name in ["updateSlots", "conversationMemory"]:
        slots = arguments.get("slots") if isinstance(arguments.get("slots"), dict) else arguments
        for key, value in (slots or {}).items():
            if value not in [None, ""]:
                state.setdefault("slots", {})[key] = value
        result = {"slots": state.get("slots", {}), "recentMessages": state.get("messages", [])[-8:]}
        state.setdefault("tool_traces", []).append({"toolName": tool_name, "arguments": arguments or {}, "status": "SUCCESS", "resultSummary": text_limit(result)})
        return result
    if tool_name == "summarizeConversation":
        result = {"slots": state.get("slots", {}), "recentMessages": state.get("messages", [])[-8:]}
        state.setdefault("tool_traces", []).append({"toolName": tool_name, "arguments": {}, "status": "SUCCESS", "resultSummary": text_limit(result)})
        return result
    if tool_name == "confirmBeforeAction":
        result = {"requiresConfirmation": True, "action": arguments.get("action") or "关键动作"}
        state.setdefault("tool_traces", []).append({"toolName": tool_name, "arguments": arguments or {}, "status": "SUCCESS", "resultSummary": text_limit(result)})
        return result
    body = {
        "sessionId": state["session_id"],
        "toolName": tool_name,
        "arguments": arguments or {},
        "latestMessage": state.get("latest_message", ""),
        "token": state.get("token"),
    }
    trace = {"toolName": tool_name, "arguments": arguments or {}, "status": "RUNNING"}
    state.setdefault("tool_traces", []).append(trace)
    try:
        with httpx.Client(timeout=20) as client:
            response = client.post(
                f"{JAVA_AGENT_BASE_URL}/api/agent/internal/tool/execute",
                headers={"X-Agent-Internal-Secret": AGENT_INTERNAL_SECRET},
                json=body,
            )
            response.raise_for_status()
            data = response.json().get("data")
            trace["status"] = "SUCCESS"
            trace["resultSummary"] = text_limit(data)
            return data
    except Exception as exc:
        trace["status"] = "FAILED"
        trace["resultSummary"] = str(exc)
        return {"error": str(exc)}


def text_limit(value: Any, limit: int = 500) -> str:
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, default=str)
    return text if len(text) <= limit else text[:limit] + "..."


def pgvector_search(query: str, memory_type: Optional[str] = None, user_id: Optional[int] = None, top_k: int = 5) -> List[Dict[str, Any]]:
    # The service is ready for real embeddings. Until an embedding provider is configured,
    # it falls back to keyword-style retrieval so the RAG path remains demonstrable.
    if not PGVECTOR_DSN:
        if memory_type == "USER_PREFERENCE":
            return []
        return local_knowledge_search(query, top_k)
    try:
        with psycopg.connect(PGVECTOR_DSN) as conn:
            register_vector(conn)
            with conn.cursor() as cur:
                like = f"%{search_keyword(query)}%"
                if memory_type == "USER_PREFERENCE":
                    cur.execute(
                        """
                        select content, metadata_json
                        from agent_memory_item
                        where memory_type = %s
                          and (%s is null or user_id = %s)
                          and content ilike %s
                        order by updated_at desc
                        limit %s
                        """,
                        (memory_type, user_id, user_id, like, top_k),
                    )
                else:
                    cur.execute(
                        """
                        select content, metadata_json
                        from medical_knowledge_chunk
                        where (%s = 'MEDICAL_KNOWLEDGE' or department = %s or source_type = %s)
                          and content ilike %s
                        order by updated_at desc
                        limit %s
                        """,
                        (memory_type or "MEDICAL_KNOWLEDGE", memory_type, memory_type, like, top_k),
                    )
                    rows = cur.fetchall()
                    if not rows:
                        cur.execute(
                            """
                            select content, metadata_json
                            from medical_knowledge_chunk
                            order by updated_at desc
                            limit %s
                            """,
                            (top_k,),
                        )
                        rows = cur.fetchall()
                    return [{"content": row[0], "metadata": row[1] or {}} for row in rows]
                return [{"content": row[0], "metadata": row[1] or {}} for row in cur.fetchall()]
    except Exception:
        if memory_type == "USER_PREFERENCE":
            return []
        return local_knowledge_search(query, top_k)


def search_keyword(query: str) -> str:
    text = query or ""
    keywords = [
        "胸痛", "呼吸困难", "喘不上气", "咳嗽", "咽痛", "嗓子疼", "发热", "发烧",
        "腹痛", "腹泻", "胃痛", "胸闷", "心悸", "头痛", "头晕", "皮疹", "瘙痒",
        "关节", "腰疼", "协和", "挂号",
    ]
    for keyword in keywords:
        if keyword in text:
            return keyword
    return text[:8] if text else ""


def local_knowledge_search(query: str, top_k: int = 5) -> List[Dict[str, Any]]:
    path = os.path.join(os.path.dirname(__file__), "..", "data", "medical_knowledge_seed.jsonl")
    if not os.path.exists(path):
        return []
    terms = set(query or "")
    rows: List[Dict[str, Any]] = []
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            item = json.loads(line)
            score = sum(1 for ch in terms if ch in item.get("content", ""))
            if score > 0:
                item["score"] = score
                rows.append(item)
    rows.sort(key=lambda item: item.get("score", 0), reverse=True)
    return rows[:top_k]


def input_guard(state: AgentState) -> AgentState:
    state["safety_card"] = safety_card()
    banned = ["开药", "处方", "诊断我是什么病", "替我诊断", "用药剂量"]
    message = state.get("latest_message", "")
    if any(word in message for word in banned):
        state["answer"] = "这个请求超出了导诊挂号助手的范围。我可以帮你判断应优先就诊的科室、查询号源并整理预诊信息，但不能替代医生诊断或开具处方。"
        state["stage"] = "SAFETY_BLOCKED"
        state["actions"] = [{"type": "ASK_USER", "label": "改为导诊挂号"}]
        step(state, "input_guard", "请求触及诊断/处方边界，已拦截。")
    else:
        step(state, "input_guard", "输入在导诊挂号范围内。")
    return state


def intent_router(state: AgentState) -> AgentState:
    message = state.get("latest_message", "")
    if any(word in message for word in ["订单", "预约记录", "挂号记录"]):
        state["intent"] = "ORDER_QUERY"
    elif any(word in message for word in ["确认挂号", "提交订单", "确认下单", "下单"]):
        state["intent"] = "BOOKING_SUBMIT"
    else:
        state["intent"] = "TRIAGE_BOOKING"
    step(state, "intent_router", f"识别意图：{state['intent']}。")
    return state


def memory_retriever(state: AgentState) -> AgentState:
    state["user_memory"] = pgvector_search(state.get("latest_message", ""), "USER_PREFERENCE", state.get("user_id"), 3)
    step(state, "memory_retriever", f"召回用户长期记忆 {len(state['user_memory'])} 条。")
    return state


def medical_knowledge_retriever(state: AgentState) -> AgentState:
    state["medical_context"] = pgvector_search(state.get("latest_message", ""), "MEDICAL_KNOWLEDGE", None, 5)
    step(state, "medical_knowledge_retriever", f"召回医疗/科室知识 {len(state['medical_context'])} 条。")
    return state


def symptom_collector(state: AgentState) -> AgentState:
    prompt = (
        "你是医疗挂号导诊助手，只抽取槽位，不做诊断。返回 JSON："
        "symptoms 数组、duration、severity、department、hospitalName、workDateText、workTime、missingSlots 数组。"
    )
    result = call_deepseek_json(prompt, {
        "today": date.today().isoformat(),
        "slots": state.get("slots", {}),
        "latestMessage": state.get("latest_message", ""),
        "medicalContext": state.get("medical_context", []),
    })
    slots = state.setdefault("slots", {})
    for key in ["symptoms", "duration", "severity", "department", "hospitalName", "workDateText", "workTime"]:
        value = result.get(key)
        if value:
            slots[key] = value
            if key == "department":
                slots["depname"] = value
            if key == "hospitalName" and not slots.get("hosname"):
                slots["hosname"] = value
            if key == "workDateText" and not slots.get("workDate"):
                normalized_date = normalize_date_text(str(value))
                if normalized_date:
                    slots["workDate"] = normalized_date
    merge_slots_from_messages(slots, state.get("messages", []), state.get("latest_message", ""))
    state["stage"] = infer_forward_stage(state.get("stage"), slots, state.get("latest_message", ""))
    state["missing_slots"] = result.get("missingSlots") or []
    step(state, "symptom_collector", "已抽取症状、时间、医院/科室偏好等槽位。")
    return state


def emergency_checker(state: AgentState) -> AgentState:
    message = state.get("latest_message", "")
    emergency_words = ["胸痛", "严重呼吸困难", "喘不上气", "意识不清", "昏迷", "大出血", "抽搐", "突发偏瘫", "剧烈头痛"]
    negations = ["没有", "无", "否认", "不"]
    emergency = False
    for word in emergency_words:
        idx = message.find(word)
        if idx >= 0 and not any(neg in message[max(0, idx - 6):idx] for neg in negations):
            emergency = True
            break
    state["emergency"] = emergency
    if emergency:
        state["stage"] = "EMERGENCY_GUIDING"
        state["answer"] = "你描述的信息可能存在急症风险。请优先线下急诊或拨打 120；在排除急症前，不建议继续普通预约挂号流程。"
        state["actions"] = [{"type": "EMERGENCY_GUIDE", "label": "优先急诊/120"}]
        step(state, "emergency_checker", "命中急症风险，停止普通挂号流程。")
    else:
        step(state, "emergency_checker", "未发现需要立即拦截的急症信号。")
    return state


def department_recommender(state: AgentState) -> AgentState:
    slots = state.setdefault("slots", {})
    if not slots.get("depname"):
        prompt = "根据症状和科室知识推荐一个首选科室和一句理由，只返回 JSON：department, reason。不要诊断疾病。"
        result = call_deepseek_json(prompt, {
            "slots": slots,
            "medicalContext": state.get("medical_context", []),
        })
        department = result.get("department") or "普通内科"
        slots["department"] = department
        slots["depname"] = department
        slots["departmentReason"] = result.get("reason") or "根据当前症状信息，建议先由对应专科评估。"
    step(state, "department_recommender", f"推荐科室：{slots.get('depname')}。")
    return state


def hospital_selector(state: AgentState) -> AgentState:
    slots = state.setdefault("slots", {})
    hosname = slots.get("hospitalName") or slots.get("hosname")
    result = execute_tool(state, "search_hospitals", {"hosname": hosname or ""})
    if isinstance(result, dict):
        data = result.get("data") or result
        content = data.get("content") if isinstance(data, dict) else None
        hospital = content[0] if isinstance(content, list) and content else data
        if isinstance(hospital, dict) and hospital.get("hoscode"):
            slots["hoscode"] = hospital.get("hoscode")
            slots["hosname"] = hospital.get("hosname")
    step(state, "hospital_selector", f"已选择医院：{slots.get('hosname', '待补充')}。")
    return state


def doctor_schedule_searcher(state: AgentState) -> AgentState:
    slots = state.setdefault("slots", {})
    if slots.get("hoscode"):
        execute_tool(state, "list_departments", {"hoscode": slots.get("hoscode")})
    if slots.get("hoscode") and slots.get("depcode"):
        execute_tool(state, "find_schedule_rules", {"hoscode": slots.get("hoscode"), "depcode": slots.get("depcode")})
    if slots.get("hoscode") and slots.get("depcode") and slots.get("workDate"):
        execute_tool(state, "find_schedule_list", {
            "hoscode": slots.get("hoscode"),
            "depcode": slots.get("depcode"),
            "workDate": slots.get("workDate"),
        })
    state["stage"] = "BOOKING_CONFIRMING" if slots.get("scheduleId") else "BOOKING_SEARCHING"
    step(state, "doctor_schedule_searcher", "已查询医院科室、可预约日期和具体号源。")
    return state


def patient_checker(state: AgentState) -> AgentState:
    if state.get("token"):
        execute_tool(state, "list_patients", {})
    step(state, "patient_checker", "已检查登录态和就诊人。")
    return state


def booking_confirmer(state: AgentState) -> AgentState:
    slots = state.setdefault("slots", {})
    if not slots.get("scheduleId"):
        missing = missing_booking_slots(slots)
        if missing:
            state["answer"] = f"我已记录：{known_slots_sentence(slots)}。现在还缺{'、'.join(missing)}，补齐后我就继续查询可预约号源。"
            state["actions"] = [{"type": "ASK_BOOKING_SLOT", "label": "补充挂号信息"}]
            step(state, "booking_confirmer", "缺少必要挂号信息，只追问缺口。")
        else:
            state["answer"] = f"我已记录：{known_slots_sentence(slots)}。信息已经基本齐了，但暂时没有查到可直接预约的号源；你可以换一天，或让我继续查看同院相近科室。"
            state["actions"] = [{"type": "ASK_BOOKING_SLOT", "label": "调整日期或科室"}]
            step(state, "booking_confirmer", "挂号信息完整但未查到号源，未回退追问已知信息。")
        return state
    state["booking_card"] = {
        "hosname": slots.get("hosname"),
        "depname": slots.get("depname"),
        "workDate": slots.get("workDate"),
        "workTime": slots.get("workTime"),
        "doctorName": slots.get("doctorName"),
        "title": slots.get("title"),
        "amount": slots.get("amount"),
        "patientName": slots.get("patientName"),
        "requiresSecondConfirmation": True,
    }
    state["answer"] = "已找到可预约号源。请核对确认卡片；只有你再次点击或输入“确认挂号”后，我才会创建订单。"
    state["actions"] = [{"type": "CONFIRM_SUBMIT_ORDER", "label": "确认挂号"}]
    state["stage"] = "BOOKING_CONFIRMING"
    step(state, "booking_confirmer", "已生成挂号确认卡，等待二次确认。")
    return state


def order_submitter(state: AgentState) -> AgentState:
    if state.get("intent") != "BOOKING_SUBMIT":
        return state
    result = execute_tool(state, "submit_order", {
        "scheduleId": state.get("slots", {}).get("scheduleId"),
        "patientId": state.get("slots", {}).get("patientId"),
    })
    state["stage"] = "VISIT_GUIDING"
    state["answer"] = "预约订单已创建，本演示环境跳过支付。你可以到订单页查看预约记录和取号信息。"
    state["actions"] = [{"type": "VIEW_ORDER", "label": "查看订单"}]
    step(state, "order_submitter", "用户已二次确认，Java 后端已执行订单创建保护。")
    return state


def report_and_memory_writer(state: AgentState) -> AgentState:
    execute_tool(state, "generate_pretriage_report", {
        "department": state.get("slots", {}).get("depname"),
        "reason": state.get("slots", {}).get("departmentReason", "根据当前症状和知识库生成预诊摘要。"),
    })
    state["pretriage_report_preview"] = {
        "departmentRecommendation": state.get("slots", {}).get("depname"),
        "doctorCopyText": "预诊摘要已由后端生成，可在报告预览中查看。",
    }
    step(state, "memory_writer", "已生成预诊报告；长期记忆写入将在 pgvector embedding 配置完成后启用。")
    return state


def should_stop(state: AgentState) -> str:
    if state.get("answer") and state.get("stage") in ["SAFETY_BLOCKED", "EMERGENCY_GUIDING"]:
        return "stop"
    return "continue"


def should_submit(state: AgentState) -> str:
    return "submit" if state.get("intent") == "BOOKING_SUBMIT" else "confirm"


graph = StateGraph(AgentState)
graph.add_node("input_guard", input_guard)
graph.add_node("intent_router", intent_router)
graph.add_node("memory_retriever", memory_retriever)
graph.add_node("medical_knowledge_retriever", medical_knowledge_retriever)
graph.add_node("symptom_collector", symptom_collector)
graph.add_node("emergency_checker", emergency_checker)
graph.add_node("department_recommender", department_recommender)
graph.add_node("hospital_selector", hospital_selector)
graph.add_node("doctor_schedule_searcher", doctor_schedule_searcher)
graph.add_node("patient_checker", patient_checker)
graph.add_node("booking_confirmer", booking_confirmer)
graph.add_node("order_submitter", order_submitter)
graph.add_node("report_and_memory_writer", report_and_memory_writer)
graph.set_entry_point("input_guard")
graph.add_conditional_edges("input_guard", should_stop, {"stop": END, "continue": "intent_router"})
graph.add_edge("intent_router", "memory_retriever")
graph.add_edge("memory_retriever", "medical_knowledge_retriever")
graph.add_edge("medical_knowledge_retriever", "symptom_collector")
graph.add_edge("symptom_collector", "emergency_checker")
graph.add_conditional_edges("emergency_checker", should_stop, {"stop": END, "continue": "department_recommender"})
graph.add_conditional_edges("department_recommender", should_submit, {"submit": "patient_checker", "confirm": "hospital_selector"})
graph.add_edge("hospital_selector", "doctor_schedule_searcher")
graph.add_edge("doctor_schedule_searcher", "patient_checker")
graph.add_conditional_edges("patient_checker", should_submit, {"submit": "order_submitter", "confirm": "booking_confirmer"})
graph.add_edge("booking_confirmer", "report_and_memory_writer")
graph.add_edge("order_submitter", "report_and_memory_writer")
graph.add_edge("report_and_memory_writer", END)
compiled_graph = graph.compile()


@app.get("/health")
def health() -> Dict[str, Any]:
    return {"status": "ok", "langgraph": True, "pgvectorConfigured": bool(PGVECTOR_DSN)}


@app.post("/agent/chat")
def chat(request: ChatRequest) -> Dict[str, Any]:
    if DEEPSEEK_API_KEY:
        return build_agentic_response(request)

    request_messages = [m.model_dump() for m in request.messages]
    slots = request.slots or {}
    merge_slots_from_messages(slots, request_messages, request.latestMessage)
    initial: AgentState = {
        "session_id": request.sessionId,
        "user_id": request.userId,
        "intent": request.intent,
        "stage": infer_forward_stage(request.stage, slots, request.latestMessage),
        "slots": slots,
        "latest_message": request.latestMessage,
        "token": request.token,
        "messages": request_messages,
        "agent_steps": [],
        "tool_traces": [],
        "actions": [],
        "booking_card": {},
        "pretriage_report_preview": {},
        "safety_card": safety_card(),
    }
    result = compiled_graph.invoke(initial)
    return {
        "sessionId": request.sessionId,
        "intent": result.get("intent", request.intent),
        "stage": result.get("stage", request.stage),
        "answer": result.get("answer") or "我已完成本轮导诊规划，请继续补充医院、时间或就诊人信息。",
        "slots": result.get("slots", {}),
        "actions": result.get("actions", []),
        "agentSteps": result.get("agent_steps", []),
        "toolTraces": result.get("tool_traces", []),
        "bookingCard": result.get("booking_card", {}),
        "safetyCard": result.get("safety_card", safety_card()),
        "pretriageReportPreview": result.get("pretriage_report_preview", {}),
    }

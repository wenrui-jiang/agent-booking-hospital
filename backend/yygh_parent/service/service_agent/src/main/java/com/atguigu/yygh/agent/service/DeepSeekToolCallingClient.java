package com.atguigu.yygh.agent.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.yygh.agent.model.AgentSession;
import com.atguigu.yygh.agent.model.AgentToolCall;
import com.atguigu.yygh.agent.model.AgentToolTurn;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class DeepSeekToolCallingClient {

    @Value("${deepseek.enabled:true}")
    private boolean enabled;

    @Value("${deepseek.api-key:}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${deepseek.model:deepseek-v4-pro}")
    private String model;

    @Value("${deepseek.timeout-ms:20000}")
    private int timeoutMs;

    public boolean isAvailable() {
        return enabled && StringUtils.hasText(apiKey);
    }

    public AgentToolTurn nextTurn(AgentSession session, JSONArray conversation) throws Exception {
        if (!isAvailable()) {
            return null;
        }

        JSONObject request = new JSONObject();
        request.put("model", model);
        request.put("stream", false);
        request.put("temperature", 0.2);
        request.put("max_tokens", 900);
        request.put("tool_choice", "auto");
        request.put("tools", toolSchemas());

        JSONArray messages = new JSONArray();
        messages.add(message("system", systemPrompt()));
        messages.addAll(conversation);
        request.put("messages", messages);

        JSONObject raw = postJson("/chat/completions", request);
        JSONObject message = raw.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message");

        AgentToolTurn turn = new AgentToolTurn();
        turn.setAssistantMessage(message.getString("content"));

        JSONArray rawToolCalls = message.getJSONArray("tool_calls");
        List<AgentToolCall> toolCalls = new ArrayList<>();
        if (rawToolCalls != null) {
            for (int i = 0; i < rawToolCalls.size(); i++) {
                JSONObject rawToolCall = rawToolCalls.getJSONObject(i);
                JSONObject function = rawToolCall.getJSONObject("function");
                if (function == null) {
                    continue;
                }
                AgentToolCall call = new AgentToolCall();
                call.setId(rawToolCall.getString("id"));
                call.setName(function.getString("name"));
                String argumentsText = function.getString("arguments");
                call.setArguments(StringUtils.hasText(argumentsText)
                        ? JSON.parseObject(argumentsText)
                        : new JSONObject());
                toolCalls.add(call);
            }
        }
        turn.setToolCalls(toolCalls);
        return turn;
    }

    public JSONObject assistantToolCallMessage(AgentToolTurn turn) {
        JSONObject message = message("assistant", turn.getAssistantMessage());
        JSONArray toolCalls = new JSONArray();
        for (AgentToolCall call : turn.getToolCalls()) {
            JSONObject item = new JSONObject();
            item.put("id", StringUtils.hasText(call.getId()) ? call.getId() : call.getName());
            item.put("type", "function");
            item.put("function", new JSONObject()
                    .fluentPut("name", call.getName())
                    .fluentPut("arguments", call.getArguments().toJSONString()));
            toolCalls.add(item);
        }
        if (!toolCalls.isEmpty()) {
            message.put("tool_calls", toolCalls);
        }
        return message;
    }

    public JSONObject toolResultMessage(String toolCallId, String toolName, Object result) {
        JSONObject message = message("tool", stringLimit(result));
        message.put("tool_call_id", StringUtils.hasText(toolCallId) ? toolCallId : toolName);
        message.put("name", toolName);
        return message;
    }

    public JSONObject userMessage(String content) {
        return message("user", content);
    }

    private JSONArray toolSchemas() {
        JSONArray tools = new JSONArray();
        tools.add(tool("getCurrentDateTime", "获取当前日期、时间、星期和时区。用户问今天日期或需要解析相对日期时先调用。",
                props()));
        tools.add(tool("normalizeDate", "把用户输入的日期表达如 2026.7.9、7月9日、明天转为 yyyy-MM-dd。",
                props().fluentPut("text", str("用户日期表达"))));
        tools.add(tool("calculateDate", "计算相对日期，如明天、后天、下周一、最近几天。",
                props().fluentPut("text", str("相对日期表达"))));
        tools.add(tool("conversationMemory", "读取或写入当前会话已知槽位。用于记住城市、医院、日期、时间偏好、科室、就诊人和症状。",
                props().fluentPut("slots", obj("要合并进当前会话的槽位对象"))));
        tools.add(tool("updateSlots", "更新当前会话槽位，不要覆盖用户已经明确提供且仍有效的信息。",
                props().fluentPut("slots", obj("要保存的槽位对象"))));
        tools.add(tool("summarizeConversation", "当历史较长时读取当前 slots 和最近消息摘要。",
                props()));
        tools.add(tool("confirmBeforeAction", "挂号、提交订单、修改资料等关键动作前要求用户确认。",
                props().fluentPut("action", str("待确认动作"))));
        tools.add(tool("search_hospitals", "按医院名称或地区检索医院。用户指定医院时优先调用。",
                props()
                        .fluentPut("hosname", str("医院名称，可为空"))
                        .fluentPut("districtCode", str("地区编码，可为空"))));
        tools.add(tool("list_departments", "查询指定医院的科室树，拿到 depcode 后才能查排班。",
                props().fluentPut("hoscode", str("医院编码，例如 1000_0"))));
        tools.add(tool("find_schedule_rules", "查询某医院某科室未来几天是否有号源。",
                props()
                        .fluentPut("hoscode", str("医院编码"))
                        .fluentPut("depcode", str("科室编码"))));
        tools.add(tool("find_schedule_list", "查询某医院某科室某一天的具体号源列表。",
                props()
                        .fluentPut("hoscode", str("医院编码"))
                        .fluentPut("depcode", str("科室编码"))
                        .fluentPut("workDate", str("日期，格式 yyyy-MM-dd"))));
        tools.add(tool("list_patients", "查询当前登录账号下的就诊人。提交挂号前必须调用。",
                props()));
        tools.add(tool("create_patient", "根据用户提供的实名/就诊人信息新增就诊人。挂号缺少就诊人时可调用；信息不全时先追问缺失项。",
                props()
                        .fluentPut("name", str("就诊人姓名，必填"))
                        .fluentPut("certificatesType", str("证件类型，身份证填 10，必填"))
                        .fluentPut("certificatesNo", str("证件号码，必填"))
                        .fluentPut("sex", num("性别，1 男，0 女，必填"))
                        .fluentPut("birthdate", str("出生日期，格式 yyyy-MM-dd，必填"))
                        .fluentPut("phone", str("手机号，必填"))
                        .fluentPut("isMarry", num("是否已婚，1 是，0 否，可选"))
                        .fluentPut("isInsure", num("是否有医保，1 是，0 否，可选"))
                        .fluentPut("address", str("详细地址，可选"))
                        .fluentPut("contactsName", str("联系人姓名，可选"))
                        .fluentPut("contactsPhone", str("联系人手机号，可选"))));
        tools.add(tool("submit_order", "创建挂号订单。只有用户明确确认挂号后才能调用。",
                props()
                        .fluentPut("scheduleId", str("排班号源 id"))
                        .fluentPut("patientId", num("就诊人 id"))));
        tools.add(tool("get_order_info", "查询已创建挂号订单详情。",
                props().fluentPut("orderId", num("订单 id"))));
        tools.add(tool("generate_pretriage_report", "生成当前会话的预诊摘要，用于给医生查看。",
                props()
                        .fluentPut("department", str("推荐科室"))
                        .fluentPut("reason", str("推荐理由"))));
        return tools;
    }

    private JSONObject tool(String name, String description, JSONObject properties) {
        return new JSONObject()
                .fluentPut("type", "function")
                .fluentPut("function", new JSONObject()
                        .fluentPut("name", name)
                        .fluentPut("description", description)
                        .fluentPut("parameters", new JSONObject()
                                .fluentPut("type", "object")
                                .fluentPut("properties", properties == null ? new JSONObject() : properties)));
    }

    private JSONObject props() {
        return new JSONObject();
    }

    private JSONObject str(String description) {
        return new JSONObject().fluentPut("type", "string").fluentPut("description", description);
    }

    private JSONObject num(String description) {
        return new JSONObject().fluentPut("type", "number").fluentPut("description", description);
    }

    private JSONObject obj(String description) {
        return new JSONObject().fluentPut("type", "object").fluentPut("description", description);
    }

    private String systemPrompt() {
        return "你是医疗预约 Agent，工作方式是自主选择工具完成导诊、查号和挂号。"
                + "你必须像连续对话一样承接上下文，优先使用当前 slots 和 recentMessages，不要重复询问已经提供的信息。"
                + "你必须先理解患者症状，必要时追问；信息足够时推荐科室。"
                + "涉及今天、明天、具体日期时调用 getCurrentDateTime/normalizeDate/calculateDate，不要猜日期。"
                + "用户提供城市、医院、日期、时间偏好、科室、就诊人、症状后，调用 updateSlots 保存。"
                + "当用户想挂号时，按顺序调用 search_hospitals、list_departments、find_schedule_rules、find_schedule_list、list_patients。"
                + "如果当前账号没有就诊人，但用户提供了实名信息，可以调用 create_patient 新增；缺少姓名、证件类型、证件号、性别、出生日期、手机号时必须追问补全。"
                + "只有用户明确说确认挂号、提交订单、确认下单时，才允许调用 submit_order。"
                + "挂号、提交订单、修改资料等关键动作前必须先确认。"
                + "不要确诊疾病，不要编造工具结果；工具结果缺失时直接说明需要补充的信息。"
                + "回复要简洁，中文输出。";
    }

    private JSONObject postJson(String path, JSONObject request) throws Exception {
        URL url = new URL(trimRightSlash(baseUrl) + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);

        byte[] payload = request.toJSONString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int status = conn.getResponseCode();
        InputStream body = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(body);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("DeepSeek HTTP " + status + ": " + text);
        }
        return JSONObject.parseObject(text);
    }

    private JSONObject message(String role, String content) {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content == null ? "" : content);
        return message;
    }

    private String readAll(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String trimRightSlash(String value) {
        if (value == null) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String stringLimit(Object value) {
        String text = value instanceof String ? (String) value : JSON.toJSONString(value);
        if (text == null) {
            return "";
        }
        return text.length() > 2000 ? text.substring(0, 2000) + "..." : text;
    }
}

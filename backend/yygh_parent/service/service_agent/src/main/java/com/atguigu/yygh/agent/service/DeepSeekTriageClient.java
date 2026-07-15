package com.atguigu.yygh.agent.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.yygh.agent.model.AgentSession;
import com.atguigu.yygh.agent.model.DeepSeekTriageResult;
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
public class DeepSeekTriageClient {

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

    public DeepSeekTriageResult analyze(AgentSession session, String latestMessage) throws Exception {
        if (!isAvailable()) {
            return null;
        }

        JSONObject request = new JSONObject();
        request.put("model", model);
        request.put("stream", false);
        request.put("temperature", 0.2);
        request.put("max_tokens", 800);
        request.put("thinking", new JSONObject().fluentPut("type", "disabled"));
        request.put("response_format", new JSONObject().fluentPut("type", "json_object"));

        JSONArray messages = new JSONArray();
        messages.add(message("system", systemPrompt()));
        messages.add(message("user", buildUserPrompt(session, latestMessage)));
        request.put("messages", messages);

        JSONObject raw = postJson("/chat/completions", request);
        String content = raw.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
        if (!StringUtils.hasText(content)) {
            return null;
        }
        return parseResult(JSONObject.parseObject(content));
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

    private DeepSeekTriageResult parseResult(JSONObject json) {
        DeepSeekTriageResult result = new DeepSeekTriageResult();
        result.setEmergency(json.getBooleanValue("emergency"));
        result.setSymptoms(toStringList(json.getJSONArray("symptoms")));
        result.setDuration(json.getString("duration"));
        result.setSeverity(json.getString("severity"));
        result.setDepartment(json.getString("department"));
        result.setAlternatives(toStringList(json.getJSONArray("alternatives")));
        result.setReason(json.getString("reason"));
        result.setFollowUpQuestion(json.getString("followUpQuestion"));
        return result;
    }

    private List<String> toStringList(JSONArray array) {
        List<String> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.size(); i++) {
            String value = array.getString(i);
            if (StringUtils.hasText(value) && !list.contains(value)) {
                list.add(value);
            }
        }
        return list;
    }

    private JSONObject message(String role, String content) {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String buildUserPrompt(AgentSession session, String latestMessage) {
        JSONObject context = new JSONObject();
        context.put("slots", session.getSlots());
        context.put("messages", session.getMessages());
        context.put("latestMessage", latestMessage);
        return context.toJSONString();
    }

    private String systemPrompt() {
        return "你是尚医通项目中的智能导诊 Agent。只做分诊和挂号辅助，不做确诊。"
                + "请根据患者多轮对话抽取症状槽位、判断是否急症、给出建议科室和追问问题。"
                + "如果胸痛、严重呼吸困难、意识障碍、大出血、抽搐等急症风险明显，emergency=true。"
                + "只返回 JSON，不要返回 Markdown。字段固定为："
                + "{"
                + "\"emergency\":false,"
                + "\"symptoms\":[\"咳嗽\"],"
                + "\"duration\":\"3天或null\","
                + "\"severity\":\"轻/中等/重或null\","
                + "\"department\":\"呼吸内科或null\","
                + "\"alternatives\":[\"耳鼻喉科\"],"
                + "\"reason\":\"推荐理由\","
                + "\"followUpQuestion\":\"仍需追问的问题；信息足够时可为null\""
                + "}。"
                + "不要编造用户未说过的病史；科室建议可以给，但不要写具体疾病诊断。";
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
}

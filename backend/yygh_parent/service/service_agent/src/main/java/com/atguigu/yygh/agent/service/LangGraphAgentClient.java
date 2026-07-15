package com.atguigu.yygh.agent.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.yygh.agent.model.AgentMessage;
import com.atguigu.yygh.agent.model.AgentSession;
import com.atguigu.yygh.agent.model.ChatAction;
import com.atguigu.yygh.agent.model.ChatResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LangGraphAgentClient {

    @Value("${agent.langgraph.enabled:false}")
    private boolean enabled;

    @Value("${agent.langgraph.base-url:http://127.0.0.1:8211}")
    private String baseUrl;

    @Value("${agent.langgraph.timeout-ms:30000}")
    private int timeoutMs;

    public boolean isAvailable() {
        return enabled && StringUtils.hasText(baseUrl);
    }

    public ChatResponse chat(AgentSession session, String latestMessage, String token, List<AgentMessage> messages) throws Exception {
        if (!isAvailable()) {
            return null;
        }

        JSONObject request = new JSONObject();
        request.put("sessionId", session.getSessionId());
        request.put("userId", session.getUserId());
        request.put("intent", session.getIntent());
        request.put("stage", session.getStage());
        request.put("slots", session.getSlots());
        request.put("latestMessage", latestMessage);
        request.put("token", token);
        request.put("messages", messages == null ? new ArrayList<>() : messages);

        JSONObject raw = postJson("/agent/chat", request);
        ChatResponse response = new ChatResponse();
        response.setSessionId(firstText(raw.getString("sessionId"), session.getSessionId()));
        response.setIntent(firstText(raw.getString("intent"), session.getIntent()));
        response.setStage(firstText(raw.getString("stage"), session.getStage()));
        response.setAnswer(raw.getString("answer"));
        response.setSlots(parseMap(raw.getJSONObject("slots"), session.getSlots()));
        response.setPretriageReportPreview(parseMap(raw.getJSONObject("pretriageReportPreview"), new LinkedHashMap<>()));
        response.setAgentSteps(parseList(raw.getJSONArray("agentSteps")));
        response.setToolTraces(parseList(raw.getJSONArray("toolTraces")));
        response.setBookingCard(parseMap(raw.getJSONObject("bookingCard"), new LinkedHashMap<>()));
        response.setSafetyCard(parseMap(raw.getJSONObject("safetyCard"), new LinkedHashMap<>()));
        response.setActions(parseActions(raw));
        return response;
    }

    private JSONObject postJson(String path, JSONObject request) throws Exception {
        URL url = new URL(trimRightSlash(baseUrl) + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        byte[] payload = request.toJSONString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int status = conn.getResponseCode();
        InputStream body = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(body);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("LangGraph Agent HTTP " + status + ": " + text);
        }
        return JSONObject.parseObject(text);
    }

    private List<ChatAction> parseActions(JSONObject raw) {
        List<ChatAction> actions = new ArrayList<>();
        if (raw.getJSONArray("actions") == null) {
            return actions;
        }
        for (int i = 0; i < raw.getJSONArray("actions").size(); i++) {
            JSONObject item = raw.getJSONArray("actions").getJSONObject(i);
            actions.add(new ChatAction(item.getString("type"), item.getString("label"), item.get("payload")));
        }
        return actions;
    }

    private List<Map<String, Object>> parseList(com.alibaba.fastjson.JSONArray array) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.size(); i++) {
            list.add(JSON.parseObject(array.getJSONObject(i).toJSONString(), LinkedHashMap.class));
        }
        return list;
    }

    private Map<String, Object> parseMap(JSONObject json, Map<String, Object> fallback) {
        if (json == null) {
            return fallback == null ? new LinkedHashMap<>() : fallback;
        }
        return JSON.parseObject(json.toJSONString(), LinkedHashMap.class);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
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
}

package com.atguigu.yygh.agent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentSession {
    private String sessionId;
    private String intent = AgentIntent.TRIAGE_BOOKING.name();
    private String stage = AgentStage.SYMPTOM_COLLECTING.name();
    private Map<String, Object> slots = new LinkedHashMap<>();
    private List<String> messages = new ArrayList<>();
    private PretriageReport report;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Map<String, Object> getSlots() {
        return slots;
    }

    public void setSlots(Map<String, Object> slots) {
        this.slots = slots;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    public PretriageReport getReport() {
        return report;
    }

    public void setReport(PretriageReport report) {
        this.report = report;
    }
}

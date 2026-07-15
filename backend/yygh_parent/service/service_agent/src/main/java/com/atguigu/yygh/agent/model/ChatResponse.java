package com.atguigu.yygh.agent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatResponse {
    private String sessionId;
    private String intent;
    private String stage;
    private String answer;
    private Map<String, Object> slots = new LinkedHashMap<>();
    private List<ChatAction> actions = new ArrayList<>();
    private Map<String, Object> pretriageReportPreview = new LinkedHashMap<>();
    private List<Map<String, Object>> agentSteps = new ArrayList<>();
    private List<Map<String, Object>> toolTraces = new ArrayList<>();
    private Map<String, Object> bookingCard = new LinkedHashMap<>();
    private Map<String, Object> safetyCard = new LinkedHashMap<>();

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

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Map<String, Object> getSlots() {
        return slots;
    }

    public void setSlots(Map<String, Object> slots) {
        this.slots = slots;
    }

    public List<ChatAction> getActions() {
        return actions;
    }

    public void setActions(List<ChatAction> actions) {
        this.actions = actions;
    }

    public Map<String, Object> getPretriageReportPreview() {
        return pretriageReportPreview;
    }

    public void setPretriageReportPreview(Map<String, Object> pretriageReportPreview) {
        this.pretriageReportPreview = pretriageReportPreview;
    }

    public List<Map<String, Object>> getAgentSteps() {
        return agentSteps;
    }

    public void setAgentSteps(List<Map<String, Object>> agentSteps) {
        this.agentSteps = agentSteps;
    }

    public List<Map<String, Object>> getToolTraces() {
        return toolTraces;
    }

    public void setToolTraces(List<Map<String, Object>> toolTraces) {
        this.toolTraces = toolTraces;
    }

    public Map<String, Object> getBookingCard() {
        return bookingCard;
    }

    public void setBookingCard(Map<String, Object> bookingCard) {
        this.bookingCard = bookingCard;
    }

    public Map<String, Object> getSafetyCard() {
        return safetyCard;
    }

    public void setSafetyCard(Map<String, Object> safetyCard) {
        this.safetyCard = safetyCard;
    }
}

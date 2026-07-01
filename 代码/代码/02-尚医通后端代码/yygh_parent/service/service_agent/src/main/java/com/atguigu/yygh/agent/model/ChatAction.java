package com.atguigu.yygh.agent.model;

public class ChatAction {
    private String type;
    private String label;
    private Object payload;

    public ChatAction() {
    }

    public ChatAction(String type, String label) {
        this.type = type;
        this.label = label;
    }

    public ChatAction(String type, String label, Object payload) {
        this.type = type;
        this.label = label;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}

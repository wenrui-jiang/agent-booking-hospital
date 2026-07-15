package com.atguigu.yygh.agent.model;

import com.alibaba.fastjson.JSONObject;

public class AgentToolCall {
    private String id;
    private String name;
    private JSONObject arguments = new JSONObject();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JSONObject getArguments() {
        return arguments;
    }

    public void setArguments(JSONObject arguments) {
        this.arguments = arguments == null ? new JSONObject() : arguments;
    }
}

package com.atguigu.yygh.agent.model;

import java.util.ArrayList;
import java.util.List;

public class AgentToolTurn {
    private String assistantMessage;
    private List<AgentToolCall> toolCalls = new ArrayList<>();

    public String getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(String assistantMessage) {
        this.assistantMessage = assistantMessage;
    }

    public List<AgentToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<AgentToolCall> toolCalls) {
        this.toolCalls = toolCalls == null ? new ArrayList<>() : toolCalls;
    }
}

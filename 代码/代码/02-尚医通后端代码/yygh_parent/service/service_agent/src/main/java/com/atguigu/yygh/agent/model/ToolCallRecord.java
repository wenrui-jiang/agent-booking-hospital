package com.atguigu.yygh.agent.model;

import java.util.Date;

public class ToolCallRecord {
    private String sessionId;
    private String toolName;
    private String argumentsText;
    private String status;
    private String resultSummary;
    private Long costMs;
    private Date createTime = new Date();

    public ToolCallRecord() {
    }

    public ToolCallRecord(String sessionId, String toolName, String argumentsText, String status, String resultSummary, Long costMs) {
        this.sessionId = sessionId;
        this.toolName = toolName;
        this.argumentsText = argumentsText;
        this.status = status;
        this.resultSummary = resultSummary;
        this.costMs = costMs;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getArgumentsText() {
        return argumentsText;
    }

    public void setArgumentsText(String argumentsText) {
        this.argumentsText = argumentsText;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public Long getCostMs() {
        return costMs;
    }

    public void setCostMs(Long costMs) {
        this.costMs = costMs;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}

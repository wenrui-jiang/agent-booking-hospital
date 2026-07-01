package com.atguigu.yygh.agent.store;

import com.atguigu.yygh.agent.model.AgentSession;
import com.atguigu.yygh.agent.model.PretriageReport;
import com.atguigu.yygh.agent.model.ToolCallRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentMemoryStore {
    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, PretriageReport> reports = new ConcurrentHashMap<>();
    private final Map<String, List<ToolCallRecord>> toolCalls = new ConcurrentHashMap<>();

    public AgentSession getOrCreate(String sessionId) {
        String id = sessionId;
        if (id == null || id.trim().length() == 0) {
            id = UUID.randomUUID().toString().replace("-", "");
        }
        final String finalId = id;
        return sessions.computeIfAbsent(finalId, key -> {
            AgentSession session = new AgentSession();
            session.setSessionId(key);
            return session;
        });
    }

    public AgentSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void saveReport(PretriageReport report) {
        reports.put(report.getSessionId(), report);
        AgentSession session = sessions.get(report.getSessionId());
        if (session != null) {
            session.setReport(report);
        }
    }

    public PretriageReport getReport(String sessionId) {
        return reports.get(sessionId);
    }

    public void addToolCall(ToolCallRecord record) {
        toolCalls.computeIfAbsent(record.getSessionId(), key -> new ArrayList<>()).add(record);
    }

    public List<ToolCallRecord> getToolCalls(String sessionId) {
        return toolCalls.getOrDefault(sessionId, Collections.emptyList());
    }
}

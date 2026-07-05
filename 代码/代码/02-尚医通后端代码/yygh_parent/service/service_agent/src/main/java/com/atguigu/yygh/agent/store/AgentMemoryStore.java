package com.atguigu.yygh.agent.store;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.yygh.agent.model.AgentIntent;
import com.atguigu.yygh.agent.model.AgentMessage;
import com.atguigu.yygh.agent.model.AgentSession;
import com.atguigu.yygh.agent.model.AgentSessionSummary;
import com.atguigu.yygh.agent.model.AgentStage;
import com.atguigu.yygh.agent.model.DepartmentRecommendation;
import com.atguigu.yygh.agent.model.PretriageReport;
import com.atguigu.yygh.agent.model.ToolCallRecord;
import com.atguigu.yygh.common.exception.YyghException;
import com.atguigu.yygh.common.result.ResultCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AgentMemoryStore {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public AgentSession getOrCreate(String sessionId, Long userId) {
        String id = StringUtils.hasText(sessionId) ? sessionId : newSessionId();
        AgentSession session = getSession(id);
        if (session == null) {
            createSession(id, userId);
            session = getSession(id);
        } else {
            assertSessionOwner(session, userId);
            if (session.getUserId() == null && userId != null) {
                jdbcTemplate.update("update agent_session set user_id = ?, update_time = now() where session_id = ?",
                        userId, id);
                session.setUserId(userId);
            }
        }
        return session;
    }

    public AgentSession createUserSession(Long userId) {
        String sessionId = newSessionId();
        createSession(sessionId, userId);
        return getSession(sessionId);
    }

    public AgentSession getSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        try {
            AgentSession session = jdbcTemplate.queryForObject(
                    "select session_id, user_id, intent, stage, slots_json, create_time, update_time "
                            + "from agent_session where session_id = ?",
                    this::mapSession,
                    sessionId);
            if (session != null) {
                session.setMessages(loadMessageContext(sessionId));
                session.setReport(getReport(sessionId));
            }
            return session;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public AgentSession getSessionForUser(String sessionId, Long userId) {
        AgentSession session = getSession(sessionId);
        if (session != null) {
            assertSessionOwnedByUser(session, userId);
        }
        return session;
    }

    public void saveSession(AgentSession session) {
        if (session == null || !StringUtils.hasText(session.getSessionId())) {
            return;
        }
        jdbcTemplate.update("update agent_session set intent = ?, stage = ?, slots_json = ?, update_time = now() "
                        + "where session_id = ?",
                session.getIntent(),
                session.getStage(),
                JSON.toJSONString(session.getSlots() == null ? Collections.emptyMap() : session.getSlots()),
                session.getSessionId());
    }

    public void addMessage(String sessionId, Long userId, String role, String content, Map<String, Object> metadata) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(role) || content == null) {
            return;
        }
        jdbcTemplate.update("insert into agent_message(session_id, user_id, role, content, metadata_json) "
                        + "values (?, ?, ?, ?, ?)",
                sessionId,
                userId,
                role,
                content,
                JSON.toJSONString(metadata == null ? Collections.emptyMap() : metadata));
        String title = buildTitle(content);
        jdbcTemplate.update("update agent_session set title = if(title is null or title = '', ?, title), "
                        + "update_time = now() where session_id = ?",
                title,
                sessionId);
    }

    public List<AgentMessage> listMessages(String sessionId, Long userId) {
        AgentSession session = getSessionForUser(sessionId, userId);
        if (session == null) {
            return Collections.emptyList();
        }
        return jdbcTemplate.query("select id, session_id, user_id, role, content, metadata_json, create_time "
                        + "from agent_message where session_id = ? order by create_time asc, id asc",
                this::mapMessage,
                sessionId);
    }

    public List<AgentSessionSummary> listSessions(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return jdbcTemplate.query("select s.session_id, s.title, s.intent, s.stage, s.create_time, s.update_time, "
                        + "(select m.content from agent_message m where m.session_id = s.session_id "
                        + "order by m.create_time desc, m.id desc limit 1) as last_message "
                        + "from agent_session s where s.user_id = ? order by s.update_time desc, s.id desc",
                this::mapSessionSummary,
                userId);
    }

    public void saveReport(PretriageReport report) {
        if (report == null || !StringUtils.hasText(report.getSessionId())) {
            return;
        }
        jdbcTemplate.update("insert into agent_pretriage_report(session_id, order_id, chief_complaint, "
                        + "symptom_summary, duration, severity, accompanying_symptoms, negative_symptoms, "
                        + "patient_context_json, department_recommendation_json, booking_draft_json, "
                        + "doctor_copy_text, confirmed) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on duplicate key update order_id = values(order_id), chief_complaint = values(chief_complaint), "
                        + "symptom_summary = values(symptom_summary), duration = values(duration), severity = values(severity), "
                        + "accompanying_symptoms = values(accompanying_symptoms), negative_symptoms = values(negative_symptoms), "
                        + "patient_context_json = values(patient_context_json), "
                        + "department_recommendation_json = values(department_recommendation_json), "
                        + "booking_draft_json = values(booking_draft_json), doctor_copy_text = values(doctor_copy_text), "
                        + "confirmed = values(confirmed), update_time = now()",
                report.getSessionId(),
                report.getOrderId(),
                report.getChiefComplaint(),
                JSON.toJSONString(report.getSymptomSummary()),
                report.getDuration(),
                report.getSeverity(),
                JSON.toJSONString(report.getAccompanyingSymptoms()),
                JSON.toJSONString(report.getNegativeSymptoms()),
                JSON.toJSONString(report.getPatientContext()),
                JSON.toJSONString(report.getDepartmentRecommendation()),
                JSON.toJSONString(report.getBookingDraft()),
                report.getDoctorCopyText(),
                Boolean.TRUE.equals(report.getConfirmed()) ? 1 : 0);
    }

    public PretriageReport getReport(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject("select session_id, order_id, chief_complaint, symptom_summary, "
                            + "duration, severity, accompanying_symptoms, negative_symptoms, patient_context_json, "
                            + "department_recommendation_json, booking_draft_json, doctor_copy_text, confirmed "
                            + "from agent_pretriage_report where session_id = ?",
                    this::mapReport,
                    sessionId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void addToolCall(ToolCallRecord record) {
        if (record == null || !StringUtils.hasText(record.getSessionId())) {
            return;
        }
        jdbcTemplate.update("insert into agent_tool_call(session_id, tool_name, arguments_text, status, result_summary, cost_ms) "
                        + "values (?, ?, ?, ?, ?, ?)",
                record.getSessionId(),
                record.getToolName(),
                record.getArgumentsText(),
                record.getStatus(),
                record.getResultSummary(),
                record.getCostMs());
    }

    public List<ToolCallRecord> getToolCalls(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Collections.emptyList();
        }
        return jdbcTemplate.query("select session_id, tool_name, arguments_text, status, result_summary, cost_ms, create_time "
                        + "from agent_tool_call where session_id = ? order by create_time asc, id asc",
                this::mapToolCall,
                sessionId);
    }

    public List<ToolCallRecord> getToolCallsForUser(String sessionId, Long userId) {
        AgentSession session = getSessionForUser(sessionId, userId);
        return session == null ? Collections.emptyList() : getToolCalls(sessionId);
    }

    private void createSession(String sessionId, Long userId) {
        jdbcTemplate.update("insert into agent_session(session_id, user_id, intent, stage, slots_json) "
                        + "values (?, ?, ?, ?, '{}')",
                sessionId,
                userId,
                AgentIntent.TRIAGE_BOOKING.name(),
                AgentStage.SYMPTOM_COLLECTING.name());
    }

    private AgentSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        AgentSession session = new AgentSession();
        session.setSessionId(rs.getString("session_id"));
        Object userId = rs.getObject("user_id");
        session.setUserId(userId == null ? null : ((Number) userId).longValue());
        session.setIntent(rs.getString("intent"));
        session.setStage(rs.getString("stage"));
        session.setSlots(parseMap(rs.getString("slots_json")));
        session.setCreateTime(rs.getTimestamp("create_time"));
        session.setUpdateTime(rs.getTimestamp("update_time"));
        return session;
    }

    private AgentMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        AgentMessage message = new AgentMessage();
        message.setId(rs.getLong("id"));
        message.setSessionId(rs.getString("session_id"));
        Object userId = rs.getObject("user_id");
        message.setUserId(userId == null ? null : ((Number) userId).longValue());
        message.setRole(rs.getString("role"));
        message.setContent(rs.getString("content"));
        message.setMetadata(parseMap(rs.getString("metadata_json")));
        message.setCreateTime(rs.getTimestamp("create_time"));
        return message;
    }

    private AgentSessionSummary mapSessionSummary(ResultSet rs, int rowNum) throws SQLException {
        AgentSessionSummary summary = new AgentSessionSummary();
        summary.setSessionId(rs.getString("session_id"));
        summary.setTitle(rs.getString("title"));
        summary.setIntent(rs.getString("intent"));
        summary.setStage(rs.getString("stage"));
        summary.setLastMessage(rs.getString("last_message"));
        summary.setCreateTime(rs.getTimestamp("create_time"));
        summary.setUpdateTime(rs.getTimestamp("update_time"));
        return summary;
    }

    private PretriageReport mapReport(ResultSet rs, int rowNum) throws SQLException {
        PretriageReport report = new PretriageReport();
        report.setSessionId(rs.getString("session_id"));
        Object orderId = rs.getObject("order_id");
        report.setOrderId(orderId == null ? null : ((Number) orderId).longValue());
        report.setChiefComplaint(rs.getString("chief_complaint"));
        report.setSymptomSummary(parseStringList(rs.getString("symptom_summary")));
        report.setDuration(rs.getString("duration"));
        report.setSeverity(rs.getString("severity"));
        report.setAccompanyingSymptoms(parseStringList(rs.getString("accompanying_symptoms")));
        report.setNegativeSymptoms(parseStringList(rs.getString("negative_symptoms")));
        report.setPatientContext(parseMap(rs.getString("patient_context_json")));
        report.setDepartmentRecommendation(parseObject(rs.getString("department_recommendation_json"), DepartmentRecommendation.class));
        report.setBookingDraft(parseMap(rs.getString("booking_draft_json")));
        report.setDoctorCopyText(rs.getString("doctor_copy_text"));
        report.setConfirmed(rs.getInt("confirmed") == 1);
        return report;
    }

    private ToolCallRecord mapToolCall(ResultSet rs, int rowNum) throws SQLException {
        ToolCallRecord record = new ToolCallRecord();
        record.setSessionId(rs.getString("session_id"));
        record.setToolName(rs.getString("tool_name"));
        record.setArgumentsText(rs.getString("arguments_text"));
        record.setStatus(rs.getString("status"));
        record.setResultSummary(rs.getString("result_summary"));
        Object costMs = rs.getObject("cost_ms");
        record.setCostMs(costMs == null ? null : ((Number) costMs).longValue());
        record.setCreateTime(rs.getTimestamp("create_time"));
        return record;
    }

    private List<String> loadMessageContext(String sessionId) {
        return jdbcTemplate.query("select role, content from agent_message where session_id = ? order by create_time asc, id asc",
                (RowMapper<String>) (rs, rowNum) -> rs.getString("role").toUpperCase() + ": " + rs.getString("content"),
                sessionId);
    }

    private Map<String, Object> parseMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        return JSON.parseObject(json, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, String.class);
    }

    private <T> T parseObject(String json, Class<T> clazz) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        return JSON.parseObject(json, clazz);
    }

    private String buildTitle(String content) {
        if (!StringUtils.hasText(content)) {
            return "New chat";
        }
        String title = content.trim().replace('\n', ' ');
        return title.length() > 40 ? title.substring(0, 40) : title;
    }

    private String newSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void assertSessionOwner(AgentSession session, Long userId) {
        if (session == null || session.getUserId() == null || userId == null) {
            return;
        }
        if (!session.getUserId().equals(userId)) {
            throw new YyghException(ResultCodeEnum.PERMISSION);
        }
    }

    private void assertSessionOwnedByUser(AgentSession session, Long userId) {
        if (session == null || session.getUserId() == null || userId == null || !session.getUserId().equals(userId)) {
            throw new YyghException(ResultCodeEnum.PERMISSION);
        }
    }
}

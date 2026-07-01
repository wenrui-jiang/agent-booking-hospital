package com.atguigu.yygh.agent.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.yygh.agent.client.HospToolClient;
import com.atguigu.yygh.agent.client.OrderToolClient;
import com.atguigu.yygh.agent.client.UserToolClient;
import com.atguigu.yygh.agent.model.AgentSession;
import com.atguigu.yygh.agent.model.AgentStage;
import com.atguigu.yygh.agent.model.ChatAction;
import com.atguigu.yygh.agent.model.ChatRequest;
import com.atguigu.yygh.agent.model.ChatResponse;
import com.atguigu.yygh.agent.model.DeepSeekTriageResult;
import com.atguigu.yygh.agent.model.DepartmentRecommendation;
import com.atguigu.yygh.agent.model.PretriageReport;
import com.atguigu.yygh.agent.model.ToolCallRecord;
import com.atguigu.yygh.agent.store.AgentMemoryStore;
import com.atguigu.yygh.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class AgentWorkflowService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    @Autowired
    private AgentMemoryStore store;

    @Autowired
    private TriageRuleEngine triageRuleEngine;

    @Autowired
    private PretriageReportService pretriageReportService;

    @Autowired
    private DeepSeekTriageClient deepSeekTriageClient;

    @Autowired(required = false)
    private HospToolClient hospToolClient;

    @Autowired(required = false)
    private UserToolClient userToolClient;

    @Autowired(required = false)
    private OrderToolClient orderToolClient;

    public ChatResponse chat(ChatRequest request, String token) {
        AgentSession session = store.getOrCreate(request.getSessionId());
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        session.getMessages().add("USER: " + message);

        mergeRuleBasedSlots(session, message);
        DeepSeekTriageResult aiResult = analyzeByDeepSeek(session, message);
        mergeDeepSeekSlots(session, aiResult);

        if (isEmergencyRisk(message, aiResult)) {
            session.setStage(AgentStage.EMERGENCY_GUIDING.name());
            ChatResponse response = baseResponse(session);
            response.setAnswer("你描述的情况可能存在急症风险。请优先线下急诊或拨打 120，不建议继续普通预约挂号流程。");
            response.getActions().add(new ChatAction("EMERGENCY_GUIDE", "优先急诊/120"));
            return response;
        }

        if (isSubmitConfirmation(message)) {
            return handleSubmitConfirmation(session, token);
        }
        if (isBookingConfirmation(message) || shouldContinueBooking(session, message)) {
            return handleBookingSearch(session, token, aiResult);
        }

        if (needsMoreSymptomInfo(session, aiResult)) {
            session.setStage(AgentStage.SYMPTOM_COLLECTING.name());
            return askSymptomFollowUp(session, aiResult);
        }

        DepartmentRecommendation recommendation = buildRecommendation(session, aiResult);
        session.getSlots().put("department", recommendation.getPrimary());
        session.getSlots().put("depname", recommendation.getPrimary());
        session.setStage(AgentStage.DEPARTMENT_RECOMMENDING.name());

        PretriageReport report = store.getReport(session.getSessionId());
        if (report == null) {
            report = pretriageReportService.generate(session, recommendation);
            store.saveReport(report);
        }

        ChatResponse response = baseResponse(session);
        response.setAnswer("我建议优先挂 " + recommendation.getPrimary() + "。"
                + recommendation.getReason()
                + " 如果认可这个方向，我可以继续帮你查询医院、科室和可预约时间。");
        response.getActions().add(new ChatAction("CONFIRM_DEPARTMENT", "确认科室并查号"));
        response.getActions().add(new ChatAction("VIEW_PRETRIAGE_REPORT", "查看预诊报告"));
        fillReportPreview(response, report);
        return response;
    }

    private boolean isEmergencyRisk(String message, DeepSeekTriageResult aiResult) {
        boolean modelEmergency = Boolean.TRUE.equals(aiResult == null ? null : aiResult.getEmergency());
        boolean ruleEmergency = triageRuleEngine.containsEmergency(message);
        if (!modelEmergency && !ruleEmergency) {
            return false;
        }
        return !containsNegatedEmergency(message);
    }

    private boolean containsNegatedEmergency(String message) {
        String text = message == null ? "" : message.trim();
        String[] emergencyKeywords = {"胸痛", "胸闷", "呼吸困难", "喘不上气", "昏迷", "意识不清", "大出血", "剧烈头痛", "抽搐"};
        for (String keyword : emergencyKeywords) {
            int index = text.indexOf(keyword);
            while (index >= 0) {
                int start = Math.max(0, index - 6);
                String prefix = text.substring(start, index);
                if (prefix.contains("无") || prefix.contains("没有") || prefix.contains("不") || prefix.contains("否认")) {
                    return true;
                }
                index = text.indexOf(keyword, index + keyword.length());
            }
        }
        return false;
    }

    public AgentSession getSession(String sessionId) {
        return store.getSession(sessionId);
    }

    public PretriageReport getReport(String sessionId) {
        return store.getReport(sessionId);
    }

    public PretriageReport confirmReport(String sessionId) {
        PretriageReport report = store.getReport(sessionId);
        if (report != null) {
            report.setConfirmed(true);
            store.saveReport(report);
        }
        return report;
    }

    public List<ToolCallRecord> getToolCalls(String sessionId) {
        return store.getToolCalls(sessionId);
    }

    private ChatResponse handleBookingSearch(AgentSession session, String token, DeepSeekTriageResult aiResult) {
        session.setStage(AgentStage.BOOKING_SEARCHING.name());
        DepartmentRecommendation recommendation = buildRecommendation(session, aiResult);
        session.getSlots().put("department", recommendation.getPrimary());
        session.getSlots().put("depname", recommendation.getPrimary());

        ensureHospital(session);
        ensureDepartment(session, recommendation);
        ensurePatient(session, token);
        ensureSchedule(session);

        PretriageReport report = store.getReport(session.getSessionId());
        if (report == null) {
            report = pretriageReportService.generate(session, recommendation);
            store.saveReport(report);
        }

        session.setStage(AgentStage.BOOKING_CONFIRMING.name());
        ChatResponse response = baseResponse(session);
        if (!StringUtils.hasText(stringSlot(session, "scheduleId"))) {
            response.setAnswer("已经进入挂号信息确认阶段，但还缺少可用号源。请补充医院、日期或上午/下午。");
            response.getActions().add(new ChatAction("ASK_BOOKING_SLOT", "补充挂号信息"));
        } else if (!StringUtils.hasText(token)) {
            response.setAnswer(buildBookingConfirmationText(session)
                    + "\n\n提交挂号前需要先登录。请登录后再次点击“确认挂号”，我会继续使用当前已选的医院、科室和号源。");
            response.getActions().add(new ChatAction("NEED_LOGIN", "登录后挂号"));
        } else if (longSlot(session, "patientId") == null) {
            response.setAnswer("已经找到可预约号源，但当前账号还没有就诊人。请先添加就诊人，再回到这里继续确认挂号。");
            response.getActions().add(new ChatAction("MANAGE_PATIENT", "添加就诊人"));
        } else {
            response.setAnswer(buildBookingConfirmationText(session));
            response.getActions().add(new ChatAction("CONFIRM_SUBMIT_ORDER", "确认挂号"));
        }
        fillReportPreview(response, report);
        return response;
    }

    private ChatResponse handleSubmitConfirmation(AgentSession session, String token) {
        if (!StringUtils.hasText(token)) {
            session.setStage(AgentStage.BOOKING_CONFIRMING.name());
            ChatResponse response = baseResponse(session);
            response.setAnswer("提交挂号前需要先登录。请登录后再次点击“确认挂号”，我会继续使用当前已选的医院、科室和号源。");
            response.getActions().add(new ChatAction("NEED_LOGIN", "登录后挂号"));
            fillReportPreview(response, store.getReport(session.getSessionId()));
            return response;
        }

        ensurePatient(session, token);
        if (longSlot(session, "patientId") == null) {
            session.setStage(AgentStage.BOOKING_CONFIRMING.name());
            ChatResponse response = baseResponse(session);
            response.setAnswer("当前账号还没有就诊人，暂时不能提交挂号。请先添加就诊人，再回到这里继续确认挂号。");
            response.getActions().add(new ChatAction("MANAGE_PATIENT", "添加就诊人"));
            fillReportPreview(response, store.getReport(session.getSessionId()));
            return response;
        }
        if (!hasBookingCandidate(session)) {
            return handleBookingSearch(session, token, null);
        }

        session.setStage(AgentStage.ORDER_SUBMITTING.name());
        String scheduleId = stringSlot(session, "scheduleId");
        Long patientId = longSlot(session, "patientId");

        Object orderResult = safeCall(session.getSessionId(), "submitOrder",
                "scheduleId=" + scheduleId + ",patientId=" + patientId, () -> {
                    if (orderToolClient == null) {
                        return "service-order client unavailable";
                    }
                    return orderToolClient.submitOrder(scheduleId, patientId, token);
                });
        Long orderId = extractOrderId(orderResult);
        if (orderId != null) {
            session.getSlots().put("orderId", orderId);
        }

        if (orderId != null && orderToolClient != null) {
            safeCall(session.getSessionId(), "getOrderInfo", "orderId=" + orderId,
                    () -> orderToolClient.getOrderInfo(orderId, token));
        }

        session.setStage(AgentStage.VISIT_GUIDING.name());
        ChatResponse response = baseResponse(session);
        response.setAnswer("预约订单已创建，本地演示已跳过真实支付。"
                + "\n订单号：" + (orderId == null ? "请在订单列表查看" : orderId)
                + "\n医院：" + stringSlot(session, "hosname")
                + "\n科室：" + stringSlot(session, "depname")
                + "\n时间：" + stringSlot(session, "workDate") + " " + stringSlot(session, "workTime")
                + "\n就诊人：" + stringSlot(session, "patientName")
                + "\n取号提示：就诊当天开诊前30分钟，到门诊楼一层挂号/收费窗口取号。");
        response.getActions().add(new ChatAction("VIEW_ORDER", "查看订单", orderId));
        fillReportPreview(response, store.getReport(session.getSessionId()));
        return response;
    }
    private ChatResponse askSymptomFollowUp(AgentSession session, DeepSeekTriageResult aiResult) {
        ChatResponse response = baseResponse(session);
        String question = aiResult == null ? null : aiResult.getFollowUpQuestion();
        if (!StringUtils.hasText(question)) {
            question = "我先帮你做导诊。请继续补充：不舒服持续多久了？有没有发热、胸闷、呼吸困难或明显疼痛？症状严重程度大概是轻、中、重？";
        }
        response.setAnswer(question);
        response.getActions().add(new ChatAction("ASK_USER", "补充症状信息"));
        return response;
    }

    private void ensureHospital(AgentSession session) {
        if (StringUtils.hasText(stringSlot(session, "hoscode"))) {
            return;
        }
        Object result = safeCall(session.getSessionId(), "searchHospitals", "page=1,limit=5", () -> {
            if (hospToolClient == null) {
                return "service-hosp client unavailable";
            }
            return hospToolClient.searchHospitals(1, 5, null, null, null);
        });
        JSONObject data = resultData(result);
        JSONArray content = data == null ? null : data.getJSONArray("content");
        JSONObject hospital = firstObject(content);
        if (hospital != null) {
            session.getSlots().put("hoscode", hospital.getString("hoscode"));
            session.getSlots().put("hosname", hospital.getString("hosname"));
        }
    }

    private void ensureDepartment(AgentSession session, DepartmentRecommendation recommendation) {
        if (StringUtils.hasText(stringSlot(session, "depcode"))) {
            return;
        }
        String hoscode = stringSlot(session, "hoscode");
        if (!StringUtils.hasText(hoscode) || hospToolClient == null) {
            return;
        }
        Object result = safeCall(session.getSessionId(), "listDepartments", "hoscode=" + hoscode,
                () -> hospToolClient.listDepartments(hoscode));
        JSONArray groups = resultArray(result);
        JSONObject match = findDepartmentMatch(groups, recommendation.getPrimary(), hoscode, session.getSessionId());
        if (match == null && groups != null && !groups.isEmpty()) {
            JSONObject firstGroup = firstObject(groups);
            JSONArray children = firstGroup == null ? null : firstGroup.getJSONArray("children");
            match = firstObject(children);
        }
        if (match != null) {
            session.getSlots().put("depcode", match.getString("depcode"));
            session.getSlots().put("depname", match.getString("depname"));
        }
    }

    private void ensurePatient(AgentSession session, String token) {
        if (longSlot(session, "patientId") != null) {
            return;
        }
        if (!StringUtils.hasText(token) || userToolClient == null) {
            return;
        }
        Object result = safeCall(session.getSessionId(), "listPatients", "currentUser",
                () -> userToolClient.listPatients(token));
        JSONArray patients = resultArray(result);
        if (patients != null && !patients.isEmpty()) {
            JSONObject patient = patients.getJSONObject(0);
            session.getSlots().put("patientId", patient.getLong("id"));
            session.getSlots().put("patientName", patient.getString("name"));
        }
    }
    private void ensureSchedule(AgentSession session) {
        if (StringUtils.hasText(stringSlot(session, "scheduleId"))) {
            return;
        }
        String hoscode = stringSlot(session, "hoscode");
        String depcode = stringSlot(session, "depcode");
        if (!StringUtils.hasText(hoscode) || !StringUtils.hasText(depcode) || hospToolClient == null) {
            return;
        }
        Object rulesResult = safeCall(session.getSessionId(), "findScheduleRules",
                "hoscode=" + hoscode + ",depcode=" + depcode,
                () -> hospToolClient.findScheduleRules(1, 7, hoscode, depcode));
        JSONObject rulesData = resultData(rulesResult);
        JSONArray days = rulesData == null ? null : rulesData.getJSONArray("bookingScheduleList");
        String workDate = chooseWorkDate(session, days);
        if (!StringUtils.hasText(workDate)) {
            return;
        }
        session.getSlots().put("workDate", workDate);

        Object listResult = safeCall(session.getSessionId(), "findScheduleList",
                "hoscode=" + hoscode + ",depcode=" + depcode + ",workDate=" + workDate,
                () -> hospToolClient.findScheduleList(hoscode, depcode, workDate));
        JSONArray schedules = resultArray(listResult);
        JSONObject schedule = chooseSchedule(session, schedules);
        if (schedule != null) {
            session.getSlots().put("scheduleId", schedule.getString("id"));
            session.getSlots().put("doctorName", stringValue(schedule, "docname", "未知医生"));
            session.getSlots().put("title", stringValue(schedule, "title", "普通号"));
            session.getSlots().put("amount", firstNonNull(schedule.get("amount"), "0"));
            session.getSlots().put("availableNumber", intValue(schedule, "availableNumber"));
            Integer workTime = intValue(schedule, "workTime");
            session.getSlots().put("workTime", workTime != null && workTime == 1 ? "下午" : "上午");
        }
    }

    private String chooseWorkDate(AgentSession session, JSONArray days) {
        String requested = stringSlot(session, "workDate");
        if (StringUtils.hasText(requested)) {
            return requested;
        }
        if (days == null) {
            return null;
        }
        for (int i = 0; i < days.size(); i++) {
            JSONObject day = objectAt(days, i);
            if (day == null) {
                continue;
            }
            Integer available = intValue(day, "availableNumber");
            Integer docCount = intValue(day, "docCount");
            if (available != null && available > 0 && docCount != null && docCount > 0) {
                return day.getString("workDate");
            }
        }
        return null;
    }

    private JSONObject chooseSchedule(AgentSession session, JSONArray schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return null;
        }
        Integer targetWorkTime = null;
        String workTimeSlot = stringSlot(session, "workTime");
        if (StringUtils.hasText(workTimeSlot)) {
            if (workTimeSlot.contains("下") || "1".equals(workTimeSlot)) {
                targetWorkTime = 1;
            } else if (workTimeSlot.contains("上") || "0".equals(workTimeSlot)) {
                targetWorkTime = 0;
            }
        }
        JSONObject fallback = null;
        for (int i = 0; i < schedules.size(); i++) {
            JSONObject schedule = objectAt(schedules, i);
            if (schedule == null) {
                continue;
            }
            if (fallback == null) {
                fallback = schedule;
            }
            Integer available = intValue(schedule, "availableNumber");
            Integer workTime = intValue(schedule, "workTime");
            if (available != null && available > 0 && (targetWorkTime == null || targetWorkTime.equals(workTime))) {
                return schedule;
            }
        }
        return fallback;
    }

    private JSONObject findDepartmentMatch(JSONArray groups, String department, String hoscode, String sessionId) {
        if (groups == null || !StringUtils.hasText(department)) {
            return null;
        }
        String normalized = normalizeDepartment(department);
        JSONObject fallback = null;
        for (int i = 0; i < groups.size(); i++) {
            JSONObject group = objectAt(groups, i);
            JSONArray children = group == null ? null : group.getJSONArray("children");
            if (children == null) {
                continue;
            }
            for (int j = 0; j < children.size(); j++) {
                JSONObject child = objectAt(children, j);
                if (child == null) {
                    continue;
                }
                String depname = child.getString("depname");
                if (depname != null && depname.contains(normalized)) {
                    if (fallback == null) {
                        fallback = child;
                    }
                    if (hasAvailableSchedule(sessionId, hoscode, child.getString("depcode"))) {
                        return child;
                    }
                }
            }
        }
        return fallback;
    }

    private boolean hasAvailableSchedule(String sessionId, String hoscode, String depcode) {
        if (!StringUtils.hasText(hoscode) || !StringUtils.hasText(depcode) || hospToolClient == null) {
            return false;
        }
        Object rulesResult = safeCall(sessionId, "findScheduleRules",
                "hoscode=" + hoscode + ",depcode=" + depcode,
                () -> hospToolClient.findScheduleRules(1, 7, hoscode, depcode));
        JSONObject rulesData = resultData(rulesResult);
        JSONArray days = rulesData == null ? null : rulesData.getJSONArray("bookingScheduleList");
        if (days == null) {
            return false;
        }
        for (int i = 0; i < days.size(); i++) {
            JSONObject day = objectAt(days, i);
            if (day == null) {
                continue;
            }
            Integer available = intValue(day, "availableNumber");
            Integer docCount = intValue(day, "docCount");
            if (available != null && available > 0 && docCount != null && docCount > 0) {
                return true;
            }
        }
        return false;
    }

    private String normalizeDepartment(String department) {
        if (department.contains("呼吸")) {
            return "呼吸内科";
        }
        if (department.contains("耳鼻") || department.contains("咽喉")) {
            return "耳鼻喉";
        }
        if (department.contains("消化") || department.contains("胃")) {
            return "消化内科";
        }
        if (department.contains("心")) {
            return "心内科";
        }
        if (department.contains("神经")) {
            return "神经";
        }
        return department.replace("科", "");
    }

    private String buildBookingConfirmationText(AgentSession session) {
        return "已找到可预约号源，请确认是否提交预约。"
                + "\n医院：" + stringSlot(session, "hosname")
                + "\n科室：" + stringSlot(session, "depname")
                + "\n日期：" + stringSlot(session, "workDate")
                + "\n时间段：" + stringSlot(session, "workTime")
                + "\n医生：" + stringSlot(session, "doctorName") + "（" + stringSlot(session, "title") + "）"
                + "\n费用：" + stringSlot(session, "amount") + " 元"
                + "\n就诊人：" + stringSlot(session, "patientName")
                + "\n确认后将创建挂号订单；本地演示不调用真实支付。";
    }

    private boolean hasBookingCandidate(AgentSession session) {
        return StringUtils.hasText(stringSlot(session, "scheduleId")) && longSlot(session, "patientId") != null;
    }

    private boolean shouldContinueBooking(AgentSession session, String message) {
        String stage = session.getStage();
        if (AgentStage.BOOKING_SEARCHING.name().equals(stage) || AgentStage.BOOKING_CONFIRMING.name().equals(stage)) {
            return true;
        }
        return StringUtils.hasText(stringSlot(session, "department"))
                && (message.contains("协和") || message.contains("医院") || message.contains("明天")
                || message.contains("后天") || message.contains("上午") || message.contains("下午")
                || message.contains("默认就诊人"));
    }

    private boolean needsMoreSymptomInfo(AgentSession session, DeepSeekTriageResult aiResult) {
        List<String> symptoms = getSymptoms(session);
        if (symptoms.isEmpty()) {
            return true;
        }
        if (session.getSlots().get("duration") == null) {
            return true;
        }
        return session.getMessages().size() < 2 && StringUtils.hasText(aiResult == null ? null : aiResult.getFollowUpQuestion());
    }

    private void mergeRuleBasedSlots(AgentSession session, String message) {
        List<String> oldSymptoms = getSymptoms(session);
        for (String symptom : triageRuleEngine.extractSymptoms(message)) {
            if (!oldSymptoms.contains(symptom)) {
                oldSymptoms.add(symptom);
            }
        }
        session.getSlots().put("symptoms", oldSymptoms);

        String duration = triageRuleEngine.extractDuration(message);
        if (duration != null) {
            session.getSlots().put("duration", duration);
        }
        String severity = triageRuleEngine.extractSeverity(message);
        if (severity != null) {
            session.getSlots().put("severity", severity);
        }
        if (message.contains("协和") || message.contains("北京协和")) {
            session.getSlots().put("hoscode", "1000_0");
            session.getSlots().put("hosname", "北京协和医院");
        }
        if (message.contains("上午")) {
            session.getSlots().put("workTime", "上午");
        } else if (message.contains("下午")) {
            session.getSlots().put("workTime", "下午");
        }
        if (message.contains("明天")) {
            session.getSlots().put("workDate", LocalDate.now().plusDays(1).format(DATE_FORMATTER));
        } else if (message.contains("后天")) {
            session.getSlots().put("workDate", LocalDate.now().plusDays(2).format(DATE_FORMATTER));
        }
        String explicitDate = extractDate(message);
        if (explicitDate != null) {
            session.getSlots().put("workDate", explicitDate);
        }
        String scheduleId = extractValueAfter(message, "scheduleId=");
        if (scheduleId != null) {
            session.getSlots().put("scheduleId", scheduleId);
        }
    }

    private DeepSeekTriageResult analyzeByDeepSeek(AgentSession session, String message) {
        Object result = safeCall(session.getSessionId(), "deepSeekTriageAnalyze", "model=deepseek-v4-pro",
                () -> deepSeekTriageClient.analyze(session, message));
        return result instanceof DeepSeekTriageResult ? (DeepSeekTriageResult) result : null;
    }

    private void mergeDeepSeekSlots(AgentSession session, DeepSeekTriageResult result) {
        if (result == null) {
            return;
        }
        List<String> symptoms = getSymptoms(session);
        if (result.getSymptoms() != null) {
            for (String symptom : result.getSymptoms()) {
                if (StringUtils.hasText(symptom) && !symptoms.contains(symptom)) {
                    symptoms.add(symptom);
                }
            }
        }
        session.getSlots().put("symptoms", symptoms);
        if (StringUtils.hasText(result.getDuration())) {
            session.getSlots().put("duration", result.getDuration());
        }
        if (StringUtils.hasText(result.getSeverity())) {
            session.getSlots().put("severity", result.getSeverity());
        }
        if (StringUtils.hasText(result.getDepartment())) {
            session.getSlots().put("department", result.getDepartment());
            session.getSlots().put("depname", result.getDepartment());
        }
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private DepartmentRecommendation buildRecommendation(AgentSession session, DeepSeekTriageResult aiResult) {
        if (aiResult != null && StringUtils.hasText(aiResult.getDepartment())) {
            DepartmentRecommendation recommendation = new DepartmentRecommendation();
            recommendation.setPrimary(aiResult.getDepartment());
            recommendation.setAlternatives(aiResult.getAlternatives());
            recommendation.setReason(StringUtils.hasText(aiResult.getReason()) ? aiResult.getReason() : "根据当前症状信息进行导诊推荐。");
            return recommendation;
        }
        return triageRuleEngine.recommend(getSymptoms(session));
    }

    private boolean isBookingConfirmation(String message) {
        return message.contains("确认科室")
                || message.contains("查号")
                || message.contains("帮我挂")
                || message.contains("继续查")
                || message.contains("可以")
                || message.contains("认可");
    }

    private boolean isSubmitConfirmation(String message) {
        return message.contains("确认挂号")
                || message.contains("提交订单")
                || message.contains("确认下单")
                || message.contains("下单");
    }

    @SuppressWarnings("unchecked")
    private List<String> getSymptoms(AgentSession session) {
        Object value = session.getSlots().get("symptoms");
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    private ChatResponse baseResponse(AgentSession session) {
        ChatResponse response = new ChatResponse();
        response.setSessionId(session.getSessionId());
        response.setIntent(session.getIntent());
        response.setStage(session.getStage());
        response.setSlots(new LinkedHashMap<>(session.getSlots()));
        return response;
    }

    private void fillReportPreview(ChatResponse response, PretriageReport report) {
        if (report == null) {
            return;
        }
        response.getPretriageReportPreview().put("chiefComplaint", report.getChiefComplaint());
        if (report.getDepartmentRecommendation() != null) {
            response.getPretriageReportPreview().put("departmentRecommendation", report.getDepartmentRecommendation().getPrimary());
        }
        response.getPretriageReportPreview().put("doctorCopyText", report.getDoctorCopyText());
    }

    private Object safeCall(String sessionId, String toolName, String argumentsText, ToolExecutor executor) {
        long start = System.currentTimeMillis();
        try {
            Object result = executor.execute();
            store.addToolCall(new ToolCallRecord(sessionId, toolName, argumentsText, "SUCCESS", stringLimit(result), System.currentTimeMillis() - start));
            return result;
        } catch (Exception e) {
            store.addToolCall(new ToolCallRecord(sessionId, toolName, argumentsText, "FAILED", e.getMessage(), System.currentTimeMillis() - start));
            return "工具调用失败：" + e.getMessage();
        }
    }

    private JSONObject resultData(Object result) {
        if (result instanceof Result) {
            Object data = ((Result<?>) result).getData();
            return data == null ? null : JSON.parseObject(JSON.toJSONString(data));
        }
        if (result instanceof String) {
            return null;
        }
        return result == null ? null : JSON.parseObject(JSON.toJSONString(result));
    }

    private JSONArray resultArray(Object result) {
        if (result instanceof Result) {
            Object data = ((Result<?>) result).getData();
            return data == null ? null : JSON.parseArray(JSON.toJSONString(data));
        }
        if (result instanceof String) {
            return null;
        }
        return result == null ? null : JSON.parseArray(JSON.toJSONString(result));
    }

    private Long extractOrderId(Object result) {
        if (result instanceof Result) {
            Object data = ((Result<?>) result).getData();
            if (data instanceof Number) {
                return ((Number) data).longValue();
            }
            if (data != null) {
                return Long.valueOf(String.valueOf(data));
            }
        }
        return null;
    }

    private String summarizeResult(Result<Object> result) {
        if (result == null) {
            return "no result";
        }
        return "code=" + result.getCode() + ", data=" + stringLimit(result.getData());
    }

    private String stringLimit(Object value) {
        String text = value instanceof String ? (String) value : JSONObject.toJSONString(value);
        if (text == null) {
            return "";
        }
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }

    private String stringSlot(AgentSession session, String key) {
        Object value = session.getSlots().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long longSlot(AgentSession session, String key) {
        Object value = session.getSlots().get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && ((String) value).length() > 0) {
            return Long.valueOf((String) value);
        }
        return null;
    }

    private JSONObject firstObject(JSONArray array) {
        if (array == null) {
            return null;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject object = objectAt(array, i);
            if (object != null) {
                return object;
            }
        }
        return null;
    }

    private JSONObject objectAt(JSONArray array, int index) {
        Object value = array == null ? null : array.get(index);
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        return JSON.parseObject(JSON.toJSONString(value));
    }

    private Integer intValue(JSONObject object, String key) {
        if (object == null) {
            return null;
        }
        Integer value = object.getInteger(key);
        if (value != null) {
            return value;
        }
        JSONObject param = object.getJSONObject("param");
        return param == null ? null : param.getInteger(key);
    }

    private String stringValue(JSONObject object, String key, String defaultValue) {
        if (object == null) {
            return defaultValue;
        }
        String value = object.getString(key);
        if (StringUtils.hasText(value)) {
            return value;
        }
        JSONObject param = object.getJSONObject("param");
        value = param == null ? null : param.getString(key);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String extractNumberAfter(String message, String marker) {
        String value = extractValueAfter(message, marker);
        if (value == null) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isDigit(ch)) {
                digits.append(ch);
            } else {
                break;
            }
        }
        return digits.length() == 0 ? null : digits.toString();
    }

    private String extractValueAfter(String message, String marker) {
        int index = message.indexOf(marker);
        if (index < 0) {
            return null;
        }
        String tail = message.substring(index + marker.length()).trim();
        int end = tail.length();
        for (int i = 0; i < tail.length(); i++) {
            char ch = tail.charAt(i);
            if (Character.isWhitespace(ch) || ch == ',' || ch == '，') {
                end = i;
                break;
            }
        }
        return tail.substring(0, end);
    }

    private String extractDate(String message) {
        for (int i = 0; i + 10 <= message.length(); i++) {
            String candidate = message.substring(i, i + 10);
            if (candidate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return candidate;
            }
        }
        return null;
    }

    private interface ToolExecutor {
        Object execute() throws Exception;
    }
}

package com.atguigu.yygh.agent.service;

import com.atguigu.yygh.agent.model.AgentSession;
import com.atguigu.yygh.agent.model.DepartmentRecommendation;
import com.atguigu.yygh.agent.model.PretriageReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PretriageReportService {

    public PretriageReport generate(AgentSession session, DepartmentRecommendation recommendation) {
        PretriageReport report = new PretriageReport();
        report.setSessionId(session.getSessionId());
        report.setDepartmentRecommendation(recommendation);

        List<String> symptoms = asStringList(session.getSlots().get("symptoms"));
        String duration = stringValue(session.getSlots().get("duration"));
        String severity = stringValue(session.getSlots().get("severity"));
        report.setSymptomSummary(symptoms);
        report.setDuration(duration == null ? "待确认" : duration);
        report.setSeverity(severity == null ? "待确认" : severity);
        report.setChiefComplaint(buildChiefComplaint(symptoms, duration));
        report.setAccompanyingSymptoms(symptoms);
        report.getNegativeSymptoms().add("未提及胸痛");
        report.getNegativeSymptoms().add("未提及严重呼吸困难");
        report.getNegativeSymptoms().add("未提及意识障碍");
        report.getPatientContext().put("patientId", session.getSlots().get("patientId"));
        report.getPatientContext().put("age", session.getSlots().get("age"));
        report.getPatientContext().put("sex", session.getSlots().get("sex"));
        copyBookingDraft(session.getSlots(), report.getBookingDraft());
        report.setDoctorCopyText(buildDoctorCopyText(report));
        return report;
    }

    private String buildChiefComplaint(List<String> symptoms, String duration) {
        String symptomText = symptoms.isEmpty() ? "身体不适" : String.join("、", symptoms);
        if (duration == null || duration.length() == 0) {
            return symptomText + "，持续时间待确认";
        }
        return symptomText + duration;
    }

    private String buildDoctorCopyText(PretriageReport report) {
        DepartmentRecommendation recommendation = report.getDepartmentRecommendation();
        String dep = recommendation == null ? "待确认" : recommendation.getPrimary();
        String reason = recommendation == null ? "待确认" : recommendation.getReason();
        return "主诉：" + report.getChiefComplaint() + "\n"
                + "现病史摘要：患者自述" + String.join("、", report.getSymptomSummary())
                + "，持续时间：" + report.getDuration()
                + "，严重程度：" + report.getSeverity() + "。\n"
                + "阴性/未提及信息：" + String.join("、", report.getNegativeSymptoms()) + "。\n"
                + "导诊建议：" + dep + "。推荐理由：" + reason + "\n"
                + "说明：本预诊报告由患者对话自动整理，供医生接诊前参考，不能替代医生诊断。";
    }

    private void copyBookingDraft(Map<String, Object> slots, Map<String, Object> bookingDraft) {
        copy(slots, bookingDraft, "hoscode");
        copy(slots, bookingDraft, "hosname");
        copy(slots, bookingDraft, "depcode");
        copy(slots, bookingDraft, "depname");
        copy(slots, bookingDraft, "workDate");
        copy(slots, bookingDraft, "workTime");
        copy(slots, bookingDraft, "scheduleId");
    }

    private void copy(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (value instanceof List) {
            return new ArrayList<>((List<String>) value);
        }
        return new ArrayList<>();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

package com.atguigu.yygh.agent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PretriageReport {
    private String sessionId;
    private Long orderId;
    private String chiefComplaint;
    private List<String> symptomSummary = new ArrayList<>();
    private String duration;
    private String severity;
    private List<String> accompanyingSymptoms = new ArrayList<>();
    private List<String> negativeSymptoms = new ArrayList<>();
    private Map<String, Object> patientContext = new LinkedHashMap<>();
    private DepartmentRecommendation departmentRecommendation;
    private Map<String, Object> bookingDraft = new LinkedHashMap<>();
    private String doctorCopyText;
    private Boolean confirmed = false;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public void setChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
    }

    public List<String> getSymptomSummary() {
        return symptomSummary;
    }

    public void setSymptomSummary(List<String> symptomSummary) {
        this.symptomSummary = symptomSummary;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public List<String> getAccompanyingSymptoms() {
        return accompanyingSymptoms;
    }

    public void setAccompanyingSymptoms(List<String> accompanyingSymptoms) {
        this.accompanyingSymptoms = accompanyingSymptoms;
    }

    public List<String> getNegativeSymptoms() {
        return negativeSymptoms;
    }

    public void setNegativeSymptoms(List<String> negativeSymptoms) {
        this.negativeSymptoms = negativeSymptoms;
    }

    public Map<String, Object> getPatientContext() {
        return patientContext;
    }

    public void setPatientContext(Map<String, Object> patientContext) {
        this.patientContext = patientContext;
    }

    public DepartmentRecommendation getDepartmentRecommendation() {
        return departmentRecommendation;
    }

    public void setDepartmentRecommendation(DepartmentRecommendation departmentRecommendation) {
        this.departmentRecommendation = departmentRecommendation;
    }

    public Map<String, Object> getBookingDraft() {
        return bookingDraft;
    }

    public void setBookingDraft(Map<String, Object> bookingDraft) {
        this.bookingDraft = bookingDraft;
    }

    public String getDoctorCopyText() {
        return doctorCopyText;
    }

    public void setDoctorCopyText(String doctorCopyText) {
        this.doctorCopyText = doctorCopyText;
    }

    public Boolean getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
    }
}

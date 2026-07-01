package com.atguigu.yygh.agent.model;

import java.util.ArrayList;
import java.util.List;

public class DepartmentRecommendation {
    private String primary;
    private List<String> alternatives = new ArrayList<>();
    private String reason;

    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String primary) {
        this.primary = primary;
    }

    public List<String> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(List<String> alternatives) {
        this.alternatives = alternatives;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

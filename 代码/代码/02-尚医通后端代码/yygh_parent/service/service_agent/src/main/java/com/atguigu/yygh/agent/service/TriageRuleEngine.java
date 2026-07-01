package com.atguigu.yygh.agent.service;

import com.atguigu.yygh.agent.model.DepartmentRecommendation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class TriageRuleEngine {

    public boolean containsEmergency(String message) {
        String text = safe(message);
        return containsAny(text, "胸痛", "胸闷很严重", "呼吸困难", "喘不上气", "昏迷", "意识不清", "大出血", "剧烈头痛", "抽搐");
    }

    public List<String> extractSymptoms(String message) {
        String text = safe(message);
        List<String> symptoms = new ArrayList<>();
        addIf(symptoms, text, "咳嗽", "咳嗽", "咳");
        addIf(symptoms, text, "咽痛", "嗓子疼", "喉咙痛", "咽痛");
        addIf(symptoms, text, "发热", "发烧", "发热", "体温");
        addIf(symptoms, text, "头痛", "头疼", "头痛");
        addIf(symptoms, text, "腹痛", "肚子疼", "腹痛", "胃疼");
        addIf(symptoms, text, "腹泻", "拉肚子", "腹泻");
        addIf(symptoms, text, "皮疹/瘙痒", "皮疹", "痒", "过敏", "红疹");
        addIf(symptoms, text, "鼻塞/流涕", "鼻塞", "流鼻涕", "鼻涕");
        addIf(symptoms, text, "心悸/胸闷", "心慌", "心悸", "胸闷");
        addIf(symptoms, text, "关节疼痛", "关节", "膝盖疼", "腰疼");
        return symptoms;
    }

    public String extractDuration(String message) {
        String text = safe(message);
        String[] units = {"天", "周", "个月", "小时", "年"};
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch) || isChineseNumber(ch)) {
                int end = Math.min(text.length(), i + 8);
                String fragment = text.substring(i, end);
                for (String unit : units) {
                    int pos = fragment.indexOf(unit);
                    if (pos > 0) {
                        return fragment.substring(0, pos + unit.length());
                    }
                }
            }
        }
        if (containsAny(text, "最近", "这几天")) {
            return "近期";
        }
        return null;
    }

    public String extractSeverity(String message) {
        String text = safe(message);
        if (containsAny(text, "很严重", "特别疼", "受不了", "剧烈")) {
            return "重";
        }
        if (containsAny(text, "有点", "轻微", "不太严重")) {
            return "轻";
        }
        if (containsAny(text, "不舒服", "疼", "痛", "难受")) {
            return "中等";
        }
        return null;
    }

    public DepartmentRecommendation recommend(List<String> symptoms) {
        DepartmentRecommendation recommendation = new DepartmentRecommendation();
        String joined = String.join(",", symptoms);
        if (containsAny(joined, "咳嗽", "咽痛", "发热", "鼻塞/流涕")) {
            recommendation.setPrimary("呼吸内科");
            recommendation.setAlternatives(Arrays.asList("耳鼻喉科", "发热门诊"));
            recommendation.setReason("症状集中在咳嗽、咽痛、发热或鼻部不适，优先按呼吸系统方向就诊。");
            return recommendation;
        }
        if (containsAny(joined, "腹痛", "腹泻")) {
            recommendation.setPrimary("消化内科");
            recommendation.setAlternatives(Arrays.asList("普通内科", "急诊科"));
            recommendation.setReason("以腹痛、腹泻等消化道症状为主，优先考虑消化内科。");
            return recommendation;
        }
        if (containsAny(joined, "皮疹/瘙痒")) {
            recommendation.setPrimary("皮肤科");
            recommendation.setAlternatives(Arrays.asList("变态反应科"));
            recommendation.setReason("出现皮疹、瘙痒或疑似过敏表现，优先考虑皮肤科。");
            return recommendation;
        }
        if (containsAny(joined, "心悸/胸闷")) {
            recommendation.setPrimary("心血管内科");
            recommendation.setAlternatives(Arrays.asList("普通内科", "急诊科"));
            recommendation.setReason("存在心悸、胸闷等表现，优先考虑心血管内科；如症状严重应及时急诊。");
            return recommendation;
        }
        if (containsAny(joined, "头痛")) {
            recommendation.setPrimary("神经内科");
            recommendation.setAlternatives(Arrays.asList("普通内科"));
            recommendation.setReason("以头痛为主诉，优先考虑神经内科评估。");
            return recommendation;
        }
        if (containsAny(joined, "关节疼痛")) {
            recommendation.setPrimary("骨科");
            recommendation.setAlternatives(Arrays.asList("风湿免疫科"));
            recommendation.setReason("以关节、腰腿等运动系统疼痛为主，优先考虑骨科或风湿免疫科。");
            return recommendation;
        }
        recommendation.setPrimary("普通内科");
        recommendation.setAlternatives(Arrays.asList("全科医学科"));
        recommendation.setReason("当前症状信息还不够特异，普通内科适合作为首诊分诊入口。");
        return recommendation;
    }

    private void addIf(List<String> symptoms, String text, String normalized, String... keywords) {
        if (containsAnyPositive(text, keywords) && !symptoms.contains(normalized)) {
            symptoms.add(normalized);
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyPositive(String text, String... keywords) {
        for (String keyword : keywords) {
            int index = text.indexOf(keyword);
            while (index >= 0) {
                if (!isNegated(text, index)) {
                    return true;
                }
                index = text.indexOf(keyword, index + keyword.length());
            }
        }
        return false;
    }

    private boolean isNegated(String text, int keywordIndex) {
        int start = Math.max(0, keywordIndex - 3);
        String prefix = text.substring(start, keywordIndex);
        return prefix.contains("不") || prefix.contains("无") || prefix.contains("没") || prefix.contains("否认");
    }

    private boolean isChineseNumber(char ch) {
        return "一二三四五六七八九十两半".indexOf(ch) >= 0;
    }

    private String safe(String message) {
        return message == null ? "" : message.trim();
    }
}

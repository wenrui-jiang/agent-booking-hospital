package com.atguigu.yygh.agent.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AgentDateTools {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern FULL_DATE_PATTERN = Pattern.compile("(20\\d{2})[.\\-/年](\\d{1,2})[.\\-/月](\\d{1,2})日?");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})月(\\d{1,2})日?");
    private static final Pattern NEXT_WEEKDAY_PATTERN = Pattern.compile("下周([一二三四五六日天])");

    public Map<String, Object> getCurrentDateTime() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zone);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", now.toLocalDate().format(DATE_FORMATTER));
        result.put("time", now.toLocalTime().withNano(0).toString());
        result.put("dateTime", now.format(DATE_TIME_FORMATTER));
        result.put("dayOfWeek", now.getDayOfWeek().toString());
        result.put("timezone", zone.getId());
        return result;
    }

    public String normalizeDate(String text) {
        return normalizeDate(text, LocalDate.now());
    }

    public String normalizeDate(String text, LocalDate baseDate) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        Matcher fullDate = FULL_DATE_PATTERN.matcher(value);
        if (fullDate.find()) {
            return LocalDate.of(
                    Integer.parseInt(fullDate.group(1)),
                    Integer.parseInt(fullDate.group(2)),
                    Integer.parseInt(fullDate.group(3))).format(DATE_FORMATTER);
        }
        Matcher monthDay = MONTH_DAY_PATTERN.matcher(value);
        if (monthDay.find()) {
            return LocalDate.of(baseDate.getYear(),
                    Integer.parseInt(monthDay.group(1)),
                    Integer.parseInt(monthDay.group(2))).format(DATE_FORMATTER);
        }
        LocalDate calculated = calculateDate(value, baseDate);
        return calculated == null ? null : calculated.format(DATE_FORMATTER);
    }

    public LocalDate calculateDate(String text, LocalDate baseDate) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        if (value.contains("今天")) {
            return baseDate;
        }
        if (value.contains("明天") || value.contains("最近")) {
            return baseDate.plusDays(1);
        }
        if (value.contains("后天")) {
            return baseDate.plusDays(2);
        }
        Matcher nextWeekday = NEXT_WEEKDAY_PATTERN.matcher(value);
        if (nextWeekday.find()) {
            DayOfWeek target = parseChineseWeekday(nextWeekday.group(1));
            int delta = target.getValue() - baseDate.getDayOfWeek().getValue();
            if (delta <= 0) {
                delta += 7;
            }
            return baseDate.plusDays(delta);
        }
        return null;
    }

    private DayOfWeek parseChineseWeekday(String value) {
        if ("一".equals(value)) return DayOfWeek.MONDAY;
        if ("二".equals(value)) return DayOfWeek.TUESDAY;
        if ("三".equals(value)) return DayOfWeek.WEDNESDAY;
        if ("四".equals(value)) return DayOfWeek.THURSDAY;
        if ("五".equals(value)) return DayOfWeek.FRIDAY;
        if ("六".equals(value)) return DayOfWeek.SATURDAY;
        return DayOfWeek.SUNDAY;
    }
}

package com.atguigu.yygh.agent.service;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AgentDateToolsTest {

    private final AgentDateTools tools = new AgentDateTools();

    @Test
    public void getCurrentDateTimeReturnsCurrentDateFields() {
        Map<String, Object> result = tools.getCurrentDateTime();

        assertEquals(LocalDate.now().toString(), result.get("date"));
        assertNotNull(result.get("time"));
        assertNotNull(result.get("dayOfWeek"));
        assertNotNull(result.get("timezone"));
    }

    @Test
    public void normalizeDateParsesTomorrowAndDottedDate() {
        LocalDate baseDate = LocalDate.of(2026, 7, 8);

        assertEquals("2026-07-09", tools.normalizeDate("明天去看", baseDate));
        assertEquals("2026-07-09", tools.normalizeDate("想 2026.7.9 去看", baseDate));
    }
}

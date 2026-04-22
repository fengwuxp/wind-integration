import com.wind.common.exception.BaseException;
import com.wind.integration.funds.ledger.spec.SettlementPolicySpec;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementTypeTests {

    @Test
    void testParseRealtime() {
        SettlementPolicySpec st = SettlementPolicySpec.parse("RT");
        assertEquals(SettlementPolicySpec.RT, st);
        LocalDateTime now = LocalDateTime.of(2026, 4, 22, 10, 30);
        assertEquals(now, st.nextSettlementTime(now));
    }

    @Test
    void testParseDelayDays() {
        SettlementPolicySpec t1 = SettlementPolicySpec.parse("T+1");
        LocalDateTime now = LocalDateTime.of(2026, 4, 22, 10, 0);
        assertEquals(now.plusDays(1), t1.nextSettlementTime(now));

        SettlementPolicySpec t2 = SettlementPolicySpec.parse("T+2");
        assertEquals(now.plusDays(2), t2.nextSettlementTime(now));
    }

    @Test
    void testParseHourly() {
        SettlementPolicySpec h1 = SettlementPolicySpec.parse("H+1");
        LocalDateTime now = LocalDateTime.of(2026, 4, 22, 10, 23);
        assertEquals(LocalDateTime.of(2026, 4, 22, 11, 0), h1.nextSettlementTime(now));

        SettlementPolicySpec h6 = SettlementPolicySpec.parse("H+6");
        assertEquals(LocalDateTime.of(2026, 4, 22, 12, 0), h6.nextSettlementTime(now));
        now = LocalDateTime.of(2026, 4, 22, 20, 0);
        assertEquals(LocalDateTime.of(2026, 4, 23, 0, 0), h6.nextSettlementTime(now));
    }

    @Test
    void testParseWeekly() {
        SettlementPolicySpec weeklyMon = SettlementPolicySpec.parse("W+1@1");
        LocalDateTime now = LocalDateTime.of(2026, 4, 22, 15, 0);
        assertEquals(LocalDateTime.of(2026, 4, 27, 0, 0), weeklyMon.nextSettlementTime(now));

        // 每两周周五：2026-04-22之后第一个周五是4月24，但间隔2周，所以下一个符合的是5月8日
        SettlementPolicySpec biweeklyFri = SettlementPolicySpec.parse("W+2@5");
        now = LocalDateTime.of(2026, 4, 22, 15, 0);
        assertEquals(LocalDateTime.of(2026, 5, 8, 0, 0), biweeklyFri.nextSettlementTime(now));
    }

    @Test
    void testParseMonthly() {
        SettlementPolicySpec monthly15 = SettlementPolicySpec.parse("M+1@15");
        LocalDateTime now = LocalDateTime.of(2026, 4, 10, 10, 0);
        assertEquals(LocalDateTime.of(2026, 4, 15, 0, 0), monthly15.nextSettlementTime(now));
        now = LocalDateTime.of(2026, 4, 20, 10, 0);
        assertEquals(LocalDateTime.of(2026, 5, 15, 0, 0), monthly15.nextSettlementTime(now));

        SettlementPolicySpec monthlyEnd = SettlementPolicySpec.parse("M+1@L");
        now = LocalDateTime.of(2026, 4, 15, 10, 0);
        assertEquals(LocalDateTime.of(2026, 4, 30, 0, 0), monthlyEnd.nextSettlementTime(now));
        now = LocalDateTime.of(2026, 4, 30, 12, 0);
        assertEquals(LocalDateTime.of(2026, 5, 31, 0, 0), monthlyEnd.nextSettlementTime(now));

        SettlementPolicySpec biMonthly1 = SettlementPolicySpec.parse("M+2@1");
        now = LocalDateTime.of(2026, 4, 1, 10, 0);
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), biMonthly1.nextSettlementTime(now));
    }

    @Test
    void testParseQuarterly() {
        SettlementPolicySpec quarterly = SettlementPolicySpec.parse("Q+1");
        LocalDateTime now = LocalDateTime.of(2026, 4, 22, 10, 0);
        assertEquals(LocalDateTime.of(2026, 7, 1, 0, 0), quarterly.nextSettlementTime(now));

        SettlementPolicySpec quarterlyEnd = SettlementPolicySpec.parse("Q+1@L");
        now = LocalDateTime.of(2026, 4, 22, 10, 0);
        assertEquals(LocalDateTime.of(2026, 6, 30, 0, 0), quarterlyEnd.nextSettlementTime(now));
    }

    @Test
    void testParseYearly() {
        SettlementPolicySpec yearly = SettlementPolicySpec.parse("Y+1");
        LocalDateTime now = LocalDateTime.of(2026, 4, 22, 10, 0);
        assertEquals(LocalDateTime.of(2027, 4, 22, 0, 0), yearly.nextSettlementTime(now));

        SettlementPolicySpec yearlyJan1 = SettlementPolicySpec.parse("Y+1@01-01");
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), yearlyJan1.nextSettlementTime(now));
    }

    @Test
    void testParseCustomCycle() {
        SettlementPolicySpec cycle = SettlementPolicySpec.parse("C@05-04");
        LocalDateTime now = LocalDateTime.of(2026, 4, 5, 10, 0);
        assertEquals(LocalDateTime.of(2026, 5, 4, 0, 0), cycle.nextSettlementTime(now));
        now = LocalDateTime.of(2026, 4, 4, 10, 0);
        assertEquals(LocalDateTime.of(2026, 5, 4, 0, 0), cycle.nextSettlementTime(now));
        now = LocalDateTime.of(2026, 4, 6, 10, 0);
        assertEquals(LocalDateTime.of(2026, 5, 4, 0, 0), cycle.nextSettlementTime(now));
        now = LocalDateTime.of(2026, 4, 30, 10, 0);
        assertEquals(LocalDateTime.of(2026, 5, 4, 0, 0), cycle.nextSettlementTime(now));
    }

    // ========================= 校验测试 =========================

    @Test
    void testValidationWeekly() {
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("W+1@0"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("W+1@8"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("W+1@-1"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("W+1"));
    }

    @Test
    void testValidationMonthly() {
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("M+1@0"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("M+1@32"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("M+1@-5"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("M+1"));
    }

    @Test
    void testValidationQuarterly() {
        // Q+1@1 应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("Q+1@1"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("Q+1@05"));
        assertThrows(BaseException.class, () -> SettlementPolicySpec.parse("Q+1@L@1"));
    }

    @Test
    void testValidationYearly() {
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("Y+1@13-01"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("Y+1@00-01"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("Y+1@01-32"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("Y+1@01-00"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("Y+1@1-1"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("Y+1@01"));
    }

    @Test
    void testValidationCustomCycle() {
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("C@00-04"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("C@05-00"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("C@32-04"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("C@05-32"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("C@05"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("C@05-04-extra"));
    }

    @Test
    void testValidationInterval() {
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("T+0"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("H+0"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("W+0@1"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("M+0@1"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("Q+0"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("Y+0"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("T-1"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("W-1@1"));
    }

    @Test
    void testValidationNegativeParameter() {
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("W+1@-5"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("M+1@-3"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("C@-05-04"));
        assertThrows(IllegalArgumentException.class, () -> SettlementPolicySpec.parse("C@05--04"));
    }

    @Test
    void testPredefinedConstants() {
        assertEquals(SettlementPolicySpec.T1, SettlementPolicySpec.parse("T+1"));
        assertEquals(SettlementPolicySpec.WEEKLY_MONDAY, SettlementPolicySpec.parse("W+1@1"));
        assertEquals(SettlementPolicySpec.MONTHLY_END, SettlementPolicySpec.parse("M+1@L"));
        assertEquals(SettlementPolicySpec.BILLING_CYCLE_5_4, SettlementPolicySpec.parse("C@05-04"));
        assertEquals(SettlementPolicySpec.QUARTERLY_END, SettlementPolicySpec.parse("Q+1@L"));
        assertEquals(SettlementPolicySpec.YEARLY_JAN1, SettlementPolicySpec.parse("Y+1@01-01"));
    }
}
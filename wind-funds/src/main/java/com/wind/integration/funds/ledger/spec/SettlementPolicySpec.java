package com.wind.integration.funds.ledger.spec;

import com.wind.common.WindConstants;
import com.wind.common.exception.AssertUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.Assert;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结算策略规范
 *
 * <h2>设计目标</h2>
 * 用于统一表达金融系统中的结算规则（Settlement Policy），覆盖：
 *
 * <ul>
 *     <li>支付 / 卡交易 T+N 延迟结算</li>
 *     <li>周期性结算（天 / 周 / 月 / 季度 / 年）</li>
 *     <li>账单周期结算（信用卡 / VCC billing cycle）</li>
 *     <li>自定义账期（跨月账期）</li>
 * </ul>
 *
 * <h2>表达式规范（DSL）</h2>
 *
 * <pre>
 * ===================== 延迟结算 =====================
 * RT              实时结算
 * T+1             交易后1天结算
 * T+2             交易后2天结算
 *
 * ===================== 小时级 =====================
 * H+1             每1小时结算
 * H+6             每6小时结算
 *
 * ===================== 周结算 =====================
 * W+1@1           每周一结算
 * W+1@5           每周五结算
 * W+2@1           每两周周一结算
 *
 * ===================== 月结算 =====================
 * M+1@1           每月1号
 * M+1@15          每月15号
 * M+1@L           每月最后一天（关键）
 * M+2@1           每两个月1号
 *
 * ===================== 季度结算 =====================
 * Q+1              每季度结算
 * Q+1@L            每季度最后一天
 *
 * ===================== 年结算 =====================
 * Y+1@01-01        每年1月1日
 * Y+1@12-31        每年最后一天
 *
 * ===================== 自定义账期 =====================
 * C@05-04          5号 -> 次月4号（信用卡账单模型）
 * C@10-09          10号 -> 次月9号
 * </pre>
 *
 * @author wuxp
 * @date 2026-04-22
 */
@Getter
public final class SettlementPolicySpec {

    // ========================= 常用标准结算策略 =========================

    public static final SettlementPolicySpec RT = new SettlementPolicySpec(SettlementMode.REALTIME, 0, null, null, "RT");
    public static final SettlementPolicySpec T1 = new SettlementPolicySpec(SettlementMode.DELAY_DAYS, 1, null, null, "T+1");
    public static final SettlementPolicySpec T2 = new SettlementPolicySpec(SettlementMode.DELAY_DAYS, 2, null, null, "T+2");
    public static final SettlementPolicySpec T3 = new SettlementPolicySpec(SettlementMode.DELAY_DAYS, 3, null, null, "T+3");
    public static final SettlementPolicySpec T7 = new SettlementPolicySpec(SettlementMode.DELAY_DAYS, 7, null, null, "T+7");

    public static final SettlementPolicySpec WEEKLY_MONDAY = new SettlementPolicySpec(SettlementMode.WEEKLY, 1, 1, null, "W+1@1");
    public static final SettlementPolicySpec WEEKLY_FRIDAY = new SettlementPolicySpec(SettlementMode.WEEKLY, 1, 5, null, "W+1@5");

    public static final SettlementPolicySpec MONTHLY_FIRST_DAY = new SettlementPolicySpec(SettlementMode.MONTHLY, 1, 1, null, "M+1@1");
    public static final SettlementPolicySpec MONTHLY_MID = new SettlementPolicySpec(SettlementMode.MONTHLY, 1, 15, null, "M+1@15");
    public static final SettlementPolicySpec MONTHLY_END = new SettlementPolicySpec(SettlementMode.MONTHLY, 1, -1, null, "M+1@L");

    public static final SettlementPolicySpec QUARTERLY = new SettlementPolicySpec(SettlementMode.QUARTERLY, 1, null, null, "Q+1");
    public static final SettlementPolicySpec QUARTERLY_END = new SettlementPolicySpec(SettlementMode.QUARTERLY, 1, -1, null, "Q+1@L");

    public static final SettlementPolicySpec YEARLY = new SettlementPolicySpec(SettlementMode.YEARLY, 1, null, null, "Y+1");
    public static final SettlementPolicySpec YEARLY_JAN1 = new SettlementPolicySpec(SettlementMode.YEARLY, 1, 1, 1, "Y+1@01-01");

    public static final SettlementPolicySpec BILLING_CYCLE_5_4 = new SettlementPolicySpec(SettlementMode.CUSTOM_CYCLE, 0, 5, 4, "C@05-04");

    // ========================= 预定义常量缓存 =========================

    private static final Map<String, SettlementPolicySpec> CONSTANTS = new ConcurrentHashMap<>();

    static {
        CONSTANTS.put("RT", RT);
        CONSTANTS.put("T+1", T1);
        CONSTANTS.put("T+2", T2);
        CONSTANTS.put("T+3", T3);
        CONSTANTS.put("T+7", T7);
        CONSTANTS.put("W+1@1", WEEKLY_MONDAY);
        CONSTANTS.put("W+1@5", WEEKLY_FRIDAY);
        CONSTANTS.put("M+1@1", MONTHLY_FIRST_DAY);
        CONSTANTS.put("M+1@15", MONTHLY_MID);
        CONSTANTS.put("M+1@L", MONTHLY_END);
        CONSTANTS.put("Q+1", QUARTERLY);
        CONSTANTS.put("Q+1@L", QUARTERLY_END);
        CONSTANTS.put("Y+1", YEARLY);
        CONSTANTS.put("Y+1@01-01", YEARLY_JAN1);
        CONSTANTS.put("C@05-04", BILLING_CYCLE_5_4);
    }

    // ========================= 核心字段 =========================

    private final SettlementMode settlementMode;
    private final int interval;
    private final Integer param;
    private final Integer endParam;
    private final String raw;

    private SettlementPolicySpec(SettlementMode mode, int interval, Integer param, Integer endParam, String raw) {
        this.settlementMode = mode;
        this.interval = interval;
        this.param = param;
        this.endParam = endParam;
        this.raw = raw;
    }

    /**
     * 解析结算策略
     */
    public static SettlementPolicySpec parse(String expression) {
        Assert.hasText(expression, "expression must not be null");
        expression = expression.trim().toUpperCase();

        // 从缓存中获取预定义常量
        SettlementPolicySpec predefined = CONSTANTS.get(expression);
        if (predefined != null) {
            return predefined;
        }

        // 延迟结算 T+N
        if (expression.startsWith("T+")) {
            int days = parseInt(expression.substring(2));
            if (days <= 0) throw new IllegalArgumentException("T delay must be positive: " + expression);
            return new SettlementPolicySpec(SettlementMode.DELAY_DAYS, days, null, null, expression);
        }

        // 处理带 @ 的表达式
        String[] atParts = expression.split(WindConstants.AT);
        AssertUtils.isTrue(atParts.length <= 2, "Invalid format, expected at most one '@' character: " + expression);
        String left = atParts[0];
        String right = atParts.length > 1 ? atParts[1] : null;

        // 校验 @ 后面的部分不能包含负号（除非是合法的 L 或 Y 模式的 MM-dd）
        if (right != null && !"L".equals(right) && right.contains("-")) {
            if (!(left.charAt(0) == 'Y' && right.matches("\\d{2}-\\d{2}"))) {
                throw new IllegalArgumentException("Invalid parameter, negative numbers not allowed: " + expression);
            }
        }

        char prefix = left.charAt(0);
        String numPart = left.substring(1);
        if (!numPart.startsWith("+")) {
            throw new IllegalArgumentException("Invalid format, expected '+' after prefix: " + expression);
        }
        int interval = parseInt(numPart.substring(1));
        if (interval <= 0) throw new IllegalArgumentException("Interval must be positive: " + expression);

        return switch (prefix) {
            case 'H' -> new SettlementPolicySpec(SettlementMode.HOURLY, interval, null, null, expression);
            case 'W' -> {
                if (right == null) {
                    throw new IllegalArgumentException("Weekday must be specified after @: " + expression);
                }
                int weekday = parseInt(right);
                if (weekday < 1 || weekday > 7) {
                    throw new IllegalArgumentException("Weekday must be 1-7, got: " + weekday);
                }
                yield new SettlementPolicySpec(SettlementMode.WEEKLY, interval, weekday, null, expression);
            }
            case 'M' -> {
                if (right == null) throw new IllegalArgumentException("Day must be specified after @ (1-31 or L): " + expression);
                int day;
                if ("L".equals(right)) {
                    day = -1;
                } else {
                    day = parseInt(right);
                    if (day < 1 || day > 31) throw new IllegalArgumentException("Day must be 1-31 or L, got: " + day);
                }
                yield new SettlementPolicySpec(SettlementMode.MONTHLY, interval, day, null, expression);
            }
            case 'Q' -> {
                if (right == null) {
                    yield new SettlementPolicySpec(SettlementMode.QUARTERLY, interval, null, null, expression);
                } else if ("L".equals(right)) {
                    yield new SettlementPolicySpec(SettlementMode.QUARTERLY, interval, -1, null, expression);
                } else {
                    throw new IllegalArgumentException("Quarterly only supports @L or no suffix: " + expression);
                }
            }
            case 'Y' -> {
                if (right != null && right.matches("\\d{2}-\\d{2}")) {
                    String[] md = right.split("-");
                    int month = parseInt(md[0]);
                    int day = parseInt(md[1]);
                    if (month < 1 || month > 12) throw new IllegalArgumentException("Month must be 1-12");
                    if (day < 1 || day > 31) throw new IllegalArgumentException("Day must be 1-31");
                    yield new SettlementPolicySpec(SettlementMode.YEARLY, interval, month, day, expression);
                } else if (right == null) {
                    yield new SettlementPolicySpec(SettlementMode.YEARLY, interval, null, null, expression);
                } else {
                    throw new IllegalArgumentException("Invalid year format, expected MM-dd or nothing: " + expression);
                }
            }
            case 'C' -> parseCustomCycle(expression);
            default -> throw new IllegalArgumentException("Unknown settlement type: " + expression);
        };
    }

    private static SettlementPolicySpec parseCustomCycle(String expr) {
        if (!expr.startsWith("C@")) throw new IllegalArgumentException("Custom cycle must start with C@: " + expr);
        String body = expr.substring(2);
        String[] parts = body.split("-");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid custom cycle, expected C@dd-dd : " + expr);
        int start = parseInt(parts[0]);
        int end = parseInt(parts[1]);
        if (start < 1 || start > 31) throw new IllegalArgumentException("Custom cycle start day must be 1-31: " + start);
        if (end < 1 || end > 31) throw new IllegalArgumentException("Custom cycle end day must be 1-31: " + end);
        return new SettlementPolicySpec(SettlementMode.CUSTOM_CYCLE, 0, start, end, expr);
    }

    // ========================= 核心计算 =========================

    public LocalDateTime nextSettlementTime(LocalDateTime now) {
        return switch (settlementMode) {
            case REALTIME -> now;
            case DELAY_DAYS -> now.plusDays(interval);
            case HOURLY -> nextHourly(now, interval);
            case WEEKLY -> nextWeekly(now, interval, param);
            case MONTHLY -> nextMonthly(now, interval, param);
            case QUARTERLY -> nextQuarterly(now, interval, param);
            case YEARLY -> nextYearly(now, interval, param, endParam);
            case CUSTOM_CYCLE -> nextCustomCycle(now, param, endParam);
        };
    }

    private static LocalDateTime nextHourly(LocalDateTime now, int intervalHours) {
        int hour = now.getHour();
        int alignedHour = ((hour / intervalHours) + 1) * intervalHours;
        if (alignedHour >= 24) {
            return now.plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(0);
        }
        return now.truncatedTo(ChronoUnit.HOURS).withHour(alignedHour);
    }

    // 修正周结算间隔逻辑
    private static LocalDateTime nextWeekly(LocalDateTime now, int intervalWeeks, int targetWeekday) {
        LocalDate current = now.toLocalDate();
        DayOfWeek target = DayOfWeek.of(targetWeekday);
        // 找到下一个目标星期几（包括今天）
        LocalDate nextDate = current.with(TemporalAdjusters.nextOrSame(target));
        // 如果找到的日期就是今天，但当前时间不是00:00，则应该算下一个周期
        if (nextDate.equals(current) && !now.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            nextDate = nextDate.plusWeeks(intervalWeeks);
        }
        // 计算从基准周（周一）到目标日期的周数差，确保是 intervalWeeks 的倍数
        long weeksDiff = ChronoUnit.WEEKS.between(getWeekBase(current), getWeekBase(nextDate));
        if (weeksDiff == 0 || weeksDiff % intervalWeeks != 0) {
            long remainder = intervalWeeks - (weeksDiff % intervalWeeks);
            nextDate = nextDate.plusWeeks(remainder);
        }
        // 最终确保日期在 now 之后
        if (nextDate.isEqual(current) && now.toLocalTime().isAfter(LocalTime.MIDNIGHT)) {
            nextDate = nextDate.plusWeeks(intervalWeeks);
        }
        return nextDate.atStartOfDay();
    }

    private static LocalDate getWeekBase(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDateTime nextMonthly(LocalDateTime now, int intervalMonths, int targetDay) {
        LocalDate current = now.toLocalDate();
        boolean isEndOfMonth = targetDay == -1;
        LocalDate nextDate;
        if (isEndOfMonth) {
            nextDate = current.withDayOfMonth(current.lengthOfMonth());
            if (nextDate.isEqual(current) && now.toLocalTime().isAfter(LocalTime.MIDNIGHT)) {
                nextDate = current.plusMonths(intervalMonths).withDayOfMonth(current.plusMonths(intervalMonths).lengthOfMonth());
            } else if (nextDate.isBefore(current)) {
                nextDate = current.plusMonths(intervalMonths).withDayOfMonth(current.plusMonths(intervalMonths).lengthOfMonth());
            }
        } else {
            int lastDayOfMonth = current.lengthOfMonth();
            int realDay = Math.min(targetDay, lastDayOfMonth);
            nextDate = current.withDayOfMonth(realDay);
            if (nextDate.isEqual(current) && now.toLocalTime().isAfter(LocalTime.MIDNIGHT)) {
                nextDate = current.plusMonths(intervalMonths);
                lastDayOfMonth = nextDate.lengthOfMonth();
                nextDate = nextDate.withDayOfMonth(Math.min(targetDay, lastDayOfMonth));
            } else if (nextDate.isBefore(current)) {
                nextDate = current.plusMonths(intervalMonths);
                lastDayOfMonth = nextDate.lengthOfMonth();
                nextDate = nextDate.withDayOfMonth(Math.min(targetDay, lastDayOfMonth));
            }
        }
        long monthsDiff = ChronoUnit.MONTHS.between(getMonthBase(current), getMonthBase(nextDate));
        if (monthsDiff % intervalMonths != 0) {
            long remainder = intervalMonths - (monthsDiff % intervalMonths);
            nextDate = nextDate.plusMonths(remainder);
            if (isEndOfMonth) {
                nextDate = nextDate.withDayOfMonth(nextDate.lengthOfMonth());
            } else {
                int last = nextDate.lengthOfMonth();
                nextDate = nextDate.withDayOfMonth(Math.min(targetDay, last));
            }
        }
        return nextDate.atStartOfDay();
    }

    private static LocalDate getMonthBase(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    private static LocalDateTime nextQuarterly(LocalDateTime now, int intervalQuarters, Integer targetDay) {
        int currentQuarterStartMonth = ((now.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate currentQuarterStart = LocalDate.of(now.getYear(), currentQuarterStartMonth, 1);
        if (targetDay == null) {
            long quartersToAdd = intervalQuarters;
            LocalDate targetDate = currentQuarterStart.plusMonths(3L * quartersToAdd);
            if (targetDate.isBefore(now.toLocalDate()) ||
                    (targetDate.isEqual(now.toLocalDate()) && now.toLocalTime().isAfter(LocalTime.MIDNIGHT))) {
                targetDate = targetDate.plusMonths(3L * intervalQuarters);
            }
            return targetDate.atStartOfDay();
        } else if (targetDay == -1) {
            LocalDate currentQuarterEnd = currentQuarterStart.plusMonths(3).minusDays(1);
            if (currentQuarterEnd.isAfter(now.toLocalDate()) ||
                    (currentQuarterEnd.isEqual(now.toLocalDate()) && now.toLocalTime().equals(LocalTime.MIDNIGHT))) {
                return currentQuarterEnd.atStartOfDay();
            }
            LocalDate nextQuarterStart = currentQuarterStart.plusMonths(3L * intervalQuarters);
            LocalDate nextQuarterEnd = nextQuarterStart.plusMonths(3).minusDays(1);
            if (nextQuarterEnd.isBefore(now.toLocalDate()) ||
                    (nextQuarterEnd.isEqual(now.toLocalDate()) && now.toLocalTime().isAfter(LocalTime.MIDNIGHT))) {
                nextQuarterEnd = nextQuarterStart.plusMonths(3L * (intervalQuarters + 1)).minusDays(1);
            }
            return nextQuarterEnd.atStartOfDay();
        } else {
            throw new IllegalStateException("Unexpected targetDay for quarterly: " + targetDay);
        }
    }

    private static LocalDateTime nextYearly(LocalDateTime now, int intervalYears, Integer month, Integer day) {
        LocalDate current = now.toLocalDate();
        LocalDate targetDate;
        if (month != null && day != null) {
            targetDate = LocalDate.of(current.getYear(), month, day);
            if (targetDate.isBefore(current) || (targetDate.isEqual(current) && now.toLocalTime().isAfter(LocalTime.MIDNIGHT))) {
                targetDate = targetDate.plusYears(intervalYears);
            }
        } else {
            targetDate = current;
            if (now.toLocalTime().isAfter(LocalTime.MIDNIGHT)) {
                targetDate = targetDate.plusYears(intervalYears);
            }
        }
        long yearsDiff = ChronoUnit.YEARS.between(getYearBase(current), getYearBase(targetDate));
        if (yearsDiff % intervalYears != 0) {
            long remainder = intervalYears - (yearsDiff % intervalYears);
            targetDate = targetDate.plusYears(remainder);
        }
        return targetDate.atStartOfDay();
    }

    private static LocalDate getYearBase(LocalDate date) {
        return date.withDayOfYear(1);
    }

    private static LocalDateTime nextCustomCycle(LocalDateTime now, int startDay, int endDay) {
        int today = now.getDayOfMonth();
        LocalDate targetDate;
        if (today >= startDay) {
            targetDate = now.toLocalDate().plusMonths(1);
        } else {
            targetDate = now.toLocalDate();
        }
        int lastDay = targetDate.lengthOfMonth();
        int realEnd = Math.min(endDay, lastDay);
        targetDate = targetDate.withDayOfMonth(realEnd);
        if (targetDate.equals(now.toLocalDate()) && now.toLocalTime().isAfter(LocalTime.MIDNIGHT)) {
            targetDate = targetDate.plusMonths(1).withDayOfMonth(Math.min(endDay, targetDate.plusMonths(1).lengthOfMonth()));
        } else if (targetDate.isBefore(now.toLocalDate())) {
            targetDate = targetDate.plusMonths(1).withDayOfMonth(Math.min(endDay, targetDate.plusMonths(1).lengthOfMonth()));
        }
        return targetDate.atStartOfDay();
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number: " + s);
        }
    }

    @Override
    public String toString() {
        return raw;
    }

    @Getter
    @AllArgsConstructor
    public enum SettlementMode {
        REALTIME("RT"), DELAY_DAYS("T"), HOURLY("H"),
        WEEKLY("W"), MONTHLY("M"), QUARTERLY("Q"),
        YEARLY("Y"), CUSTOM_CYCLE("C");
        private final String prefix;
    }
}
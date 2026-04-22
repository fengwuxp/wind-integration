package com.wind.integration.funds.enums;

import com.wind.common.WindDateFormater;
import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账户余额周期类型
 *
 * @author wuxp
 * @date 2026-04-22 14:13
 **/
@AllArgsConstructor
@Getter
public enum AccountBalancePeriodType implements DescriptiveEnum {

    LIFETIME("实时"),

    DAYS("天"),

    HOURLY("小时"),

    WEEKLY("周"),

    MONTHLY("月"),

    QUARTERLY("季"),

    YEARLY("年"),

    CUSTOM_CYCLE("自定义周期");

    private final String desc;


    public String formatPeriodId() {
        LocalDateTime now = LocalDateTime.now();
        return switch (this) {
            case LIFETIME -> LIFETIME.name();
            case DAYS -> WindDateFormater.YYYY_MM_DD.format(now);
            case HOURLY -> WindDateFormater.YYYY_MM_DD_HH.format(now);
            case WEEKLY -> {
                // 返回本周一的日期作为周标识（例如：2026-04-20）
                LocalDate monday = now.toLocalDate().with(DayOfWeek.MONDAY);
                yield WindDateFormater.YYYY_MM_DD.format(monday.atStartOfDay());
            }
            case MONTHLY -> WindDateFormater.YYYY_MM.format(now);
            case QUARTERLY -> {
                // 返回年份+季度（例如：2026Q2）
                int year = now.getYear();
                int quarter = (now.getMonthValue() - 1) / 3 + 1;
                yield year + "Q" + quarter;
            }
            case YEARLY -> WindDateFormater.YYYY.format(now);
            default -> throw new IllegalArgumentException("不支持的周期类型: " + this);
        };
    }
}

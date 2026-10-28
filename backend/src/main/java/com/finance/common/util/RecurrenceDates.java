package com.finance.common.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** 月度始终以原始开始日为锚点，避免1月31日经过2月后漂移为每月28日。 */
public final class RecurrenceDates {
    private RecurrenceDates() {}

    public static LocalDate next(LocalDate start, LocalDate current, String frequency) {
        if ("DAILY".equals(frequency)) return current.plusDays(1);
        if ("WEEKLY".equals(frequency)) return current.plusWeeks(1);
        if ("MONTHLY".equals(frequency)) {
            long month = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(current));
            return start.plusMonths(month + 1);
        }
        throw new IllegalArgumentException("Unsupported frequency");
    }

    public static LocalDate onOrAfter(LocalDate start, LocalDate candidate, String frequency) {
        if (!candidate.isAfter(start)) return start;
        if ("DAILY".equals(frequency)) return candidate;
        if ("WEEKLY".equals(frequency)) {
            long days = ChronoUnit.DAYS.between(start, candidate);
            return start.plusWeeks((days + 6) / 7);
        }
        if (!"MONTHLY".equals(frequency)) throw new IllegalArgumentException("Unsupported frequency");
        long month = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(candidate));
        LocalDate date = start.plusMonths(month);
        return date.isBefore(candidate) ? start.plusMonths(month + 1) : date;
    }
}

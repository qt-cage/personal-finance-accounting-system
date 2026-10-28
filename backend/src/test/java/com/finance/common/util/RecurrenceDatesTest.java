package com.finance.common.util;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class RecurrenceDatesTest {
    @Test void monthEndKeepsOriginalAnchorIncludingLeapYear() {
        LocalDate start=LocalDate.of(2026,1,31);
        LocalDate feb=RecurrenceDates.next(start,start,"MONTHLY");
        assertEquals(LocalDate.of(2026,2,28),feb);
        assertEquals(LocalDate.of(2026,3,31),RecurrenceDates.next(start,feb,"MONTHLY"));
        LocalDate leap=LocalDate.of(2028,1,31);
        assertEquals(LocalDate.of(2028,2,29),RecurrenceDates.next(leap,leap,"MONTHLY"));
    }
    @Test void resumingSkipsPausedDatesWithoutMovingMonthlyAnchor() {
        assertEquals(LocalDate.of(2026,10,31),RecurrenceDates.onOrAfter(LocalDate.of(2026,1,31),LocalDate.of(2026,10,9),"MONTHLY"));
        assertEquals(LocalDate.of(2026,3,31),RecurrenceDates.onOrAfter(LocalDate.of(2026,1,31),LocalDate.of(2026,3,31),"MONTHLY"));
    }
    @Test void dailyAndWeeklyCrossYearBoundaries() {
        LocalDate start=LocalDate.of(2026,12,31);
        assertEquals(LocalDate.of(2027,1,1),RecurrenceDates.next(start,start,"DAILY"));
        assertEquals(LocalDate.of(2027,1,7),RecurrenceDates.next(start,start,"WEEKLY"));
        assertEquals(LocalDate.of(2027,1,14),RecurrenceDates.onOrAfter(start,LocalDate.of(2027,1,8),"WEEKLY"));
    }
    @Test void unknownFrequencyIsRejected() {
        assertThrows(IllegalArgumentException.class,()->RecurrenceDates.onOrAfter(LocalDate.of(2026,1,1),LocalDate.of(2026,2,1),"YEARLY"));
    }
}

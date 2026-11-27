package com.red_escuela.backend.simulation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class SyntheticHolidayCalendar {

    public record SyntheticHoliday(LocalDate date, String name) {
    }

    private static final TreeMap<MonthDay, String> FIXED = new TreeMap<>();
    private static final int BRIDGE_DAYS_PER_YEAR = 2;

    static {
        FIXED.put(MonthDay.of(3, 24), "Día de la Memoria");
        FIXED.put(MonthDay.of(4, 2), "Día de Malvinas");
        FIXED.put(MonthDay.of(5, 1), "Día del Trabajador");
        FIXED.put(MonthDay.of(5, 25), "Revolución de Mayo");
        FIXED.put(MonthDay.of(7, 9), "Día de la Independencia");
        FIXED.put(MonthDay.of(10, 12), "Diversidad Cultural");
        FIXED.put(MonthDay.of(12, 8), "Inmaculada Concepción");
    }

    private final long seed;

    public SyntheticHolidayCalendar(long seed) {
        this.seed = seed;
    }

    public List<SyntheticHoliday> holidaysOf(int year) {
        TreeSet<LocalDate> dates = new TreeSet<>();
        FIXED.keySet().forEach(monthDay -> dates.add(monthDay.atYear(year)));
        Random random = new Random(seed * 31 + year);
        int added = 0;
        while (added < BRIDGE_DAYS_PER_YEAR) {
            LocalDate candidate = LocalDate.of(year, 3, 1).plusDays(random.nextInt(290));
            if (isWeekday(candidate) && dates.add(candidate)) {
                added++;
            }
        }
        return dates.stream().map(date -> new SyntheticHoliday(date, nameOf(date))).toList();
    }

    public Set<LocalDate> holidayDatesOf(int year) {
        return holidaysOf(year).stream().map(SyntheticHoliday::date).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    public boolean isClassDay(LocalDate date) {
        return isWeekday(date) && !holidayDatesOf(date.getYear()).contains(date);
    }

    private static boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private static String nameOf(LocalDate date) {
        String fixed = FIXED.get(MonthDay.from(date));
        return fixed != null ? fixed : "Feriado puente " + date;
    }
}

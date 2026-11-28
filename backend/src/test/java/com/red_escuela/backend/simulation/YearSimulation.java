package com.red_escuela.backend.simulation;

import com.red_escuela.backend.demo.DemoPersona;
import com.red_escuela.backend.demo.infrastructure.DemoHttpClient;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class YearSimulation {

    public static final int FIRST_EXAM_CLASS = 18;
    public static final int SECOND_EXAM_CLASS = 30;

    private static final double[] ABSENCE_RATES = {0.02, 0.10, 0.25, 0.40};

    private final DemoHttpClient api;
    private final JdbcTemplate jdbc;
    private final SyntheticHolidayCalendar calendar;
    private final SimulationClockDriver driver;
    private final SimulationReport report;
    private List<Long> sectionIds = List.of();
    private final long seed;
    private final int year;
    private final Map<Integer, Integer> classesGivenBySection = new LinkedHashMap<>();
    private final java.util.Set<LocalDate> loadedHolidays = new java.util.HashSet<>();

    public YearSimulation(DemoHttpClient api, JdbcTemplate jdbc, SyntheticHolidayCalendar calendar,
                          SimulationClockDriver driver, SimulationReport report,
                          long seed, int year) {
        this.api = api;
        this.jdbc = jdbc;
        this.calendar = calendar;
        this.driver = driver;
        this.report = report;
        this.seed = seed;
        this.year = year;
    }

    public void loadSyntheticHolidays(int fromYear, int toYear) {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (JsonNode holiday : api.get(DemoPersona.ADMIN, "/holidays")) {
            existing.add(holiday.path("date").asString(""));
            loadedHolidays.add(LocalDate.parse(holiday.path("date").asString("")));
        }
        for (int holidayYear = fromYear; holidayYear <= toYear; holidayYear++) {
            for (SyntheticHolidayCalendar.SyntheticHoliday holiday : calendar.holidaysOf(holidayYear)) {
                if (existing.add(holiday.date().toString())) {
                    api.post(DemoPersona.ADMIN, "/holidays",
                            Map.of("date", holiday.date().toString(), "name", holiday.name(), "type", "NATIONAL"));
                    loadedHolidays.add(holiday.date());
                    report.count("holidaysLoaded");
                }
            }
        }
    }

    public void run(List<Long> sectionIds, LocalDate firstDay, LocalDate lastDay) {
        this.sectionIds = sectionIds;
        classesGivenBySection.clear();
        long startedAt = System.currentTimeMillis();
        int days = driver.run(firstDay, lastDay, this::simulateDay);
        report.finish(days, System.currentTimeMillis() - startedAt);
        recordOutcomes();
    }

    private void simulateDay(LocalDate day) {
        if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY || loadedHolidays.contains(day)) {
            return;
        }
        for (int position = 0; position < sectionIds.size(); position++) {
            if (day.getDayOfWeek() == classWeekdayOf(position)) {
                holdClass(position, day);
            }
        }
    }

    private DayOfWeek classWeekdayOf(int sectionPosition) {
        return DayOfWeek.of(1 + sectionPosition % 5);
    }

    private void holdClass(int sectionPosition, LocalDate day) {
        List<Long> enrollmentIds = rosterOf(sectionIds.get(sectionPosition));
        if (enrollmentIds.isEmpty()) {
            report.count("classesSkippedWithoutActiveStudents");
            return;
        }
        int classNumber = classesGivenBySection.merge(sectionPosition, 1, Integer::sum);
        Random random = new Random(seed * 1_000_003L + sectionPosition * 10_007L + day.toEpochDay());
        List<Map<String, Object>> entries = new ArrayList<>();
        List<Integer> absentPositions = new ArrayList<>();
        for (int rosterPosition = 0; rosterPosition < enrollmentIds.size(); rosterPosition++) {
            boolean absent = random.nextDouble() < ABSENCE_RATES[rosterPosition % ABSENCE_RATES.length];
            if (absent) {
                absentPositions.add(rosterPosition);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("enrollmentId", enrollmentIds.get(rosterPosition));
            entry.put("status", absent ? "ABSENT" : "PRESENT");
            entries.add(entry);
        }
        api.post(DemoPersona.TEACHER, "/program-sections/" + sectionIds.get(sectionPosition) + "/attendance",
                Map.of("classDate", day.toString(), "entries", entries));
        report.count("attendanceSheets");
        report.recordEvent(day + " S" + sectionPosition + " class#" + classNumber + " absent=" + absentPositions);
        if (classNumber == FIRST_EXAM_CLASS) {
            holdExam(sectionPosition, enrollmentIds, 1, day, random);
        } else if (classNumber == SECOND_EXAM_CLASS) {
            holdExam(sectionPosition, enrollmentIds, 2, day, random);
        }
    }

    private void holdExam(int sectionPosition, List<Long> enrollmentIds, int sequenceNumber, LocalDate day, Random random) {
        List<Integer> scores = new ArrayList<>();
        for (Long enrollmentId : enrollmentIds) {
            int score = 1 + random.nextInt(10);
            scores.add(score);
            Map<String, Object> gradeItem = new LinkedHashMap<>();
            gradeItem.put("type", "EXAM");
            gradeItem.put("sequenceNumber", sequenceNumber);
            gradeItem.put("date", day.atStartOfDay().toString());
            gradeItem.put("score", BigDecimal.valueOf(score));
            api.post(DemoPersona.TEACHER, "/section-enrollments/" + enrollmentId + "/grade-items", gradeItem);
            report.count("gradeItems");
        }
        Map<String, Object> publication = new LinkedHashMap<>();
        publication.put("gradeType", "EXAM");
        publication.put("sequenceNumber", sequenceNumber);
        api.post(DemoPersona.TEACHER, "/program-sections/" + sectionIds.get(sectionPosition) + "/grade-items/publish", publication);
        report.count("examPublications");
        report.recordEvent(day + " S" + sectionPosition + " exam#" + sequenceNumber + " scores=" + scores);
    }

    private List<Long> rosterOf(long sectionId) {
        List<Long> enrollmentIds = new ArrayList<>();
        api.get(DemoPersona.TEACHER, "/program-sections/" + sectionId + "/roster")
                .forEach(entry -> enrollmentIds.add(entry.get("enrollmentId").asLong()));
        enrollmentIds.sort(Comparator.naturalOrder());
        return enrollmentIds;
    }

    private List<Long> enrollmentIdsOf(long sectionId) {
        return jdbc.queryForList("select id from subject_section_enrollment where program_section_id = ? order by id", Long.class, sectionId);
    }

    private void recordOutcomes() {
        for (int sectionPosition = 0; sectionPosition < sectionIds.size(); sectionPosition++) {
            List<Long> enrollmentIds = enrollmentIdsOf(sectionIds.get(sectionPosition));
            for (int rosterPosition = 0; rosterPosition < enrollmentIds.size(); rosterPosition++) {
                long enrollmentId = enrollmentIds.get(rosterPosition);
                Integer absences = jdbc.queryForObject(
                        "select count(*) from attendance where program_section_enrollment_id = ? and status = 'ABSENT'",
                        Integer.class, enrollmentId);
                List<BigDecimal> scores = new ArrayList<>();
                api.get(DemoPersona.ADMIN, "/section-enrollments/" + enrollmentId + "/grade-items")
                        .forEach(item -> scores.add(item.get("score").decimalValue()));
                scores.sort(Comparator.naturalOrder());
                String status = jdbc.queryForObject("select status from subject_section_enrollment where id = ?", String.class, enrollmentId);
                report.recordOutcome("S" + sectionPosition + "/R" + rosterPosition + " year=" + year + " status=" + status + " absences=" + absences
                        + " scores=" + scores.stream().map(BigDecimal::toPlainString).toList());
            }
        }
    }
}

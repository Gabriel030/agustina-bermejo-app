package com.red_escuela.backend.simulation;

import com.red_escuela.backend.demo.DemoCatalog;
import com.red_escuela.backend.demo.DemoPersona;
import com.red_escuela.backend.demo.infrastructure.DemoHttpClient;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

public class MultiYearSimulation {

    public static final int FIRST_EXAM_CLASS = 12;
    public static final int SECOND_EXAM_CLASS = 22;
    public static final int FIRST_YEAR = 2026;
    public static final int LAST_YEAR = 2030;

    private static final String SCHEME_NAME = "Esquema simulación: promoción con 7";

    private record Plan(Pupil pupil, int studyYear, int subjectIndex, int attempt) {
    }

    private static final class Pupil {
        final int index;
        final StudentProfile profile;
        final int cohortYear;
        final long studentId;
        Long programEnrollmentId;

        Pupil(int index, StudentProfile profile, int cohortYear, long studentId) {
            this.index = index;
            this.profile = profile;
            this.cohortYear = cohortYear;
            this.studentId = studentId;
        }
    }

    private final DemoHttpClient api;
    private final JdbcTemplate jdbc;
    private final SimulationClockDriver driver;
    private final SimulationReport report;
    private final long seed;
    private final DemoCatalog.ProgramSpec spec;
    private final long programId;
    private final long instructorId;
    private final List<Pupil> pupils = new ArrayList<>();
    private final Set<LocalDate> holidays;
    private final Map<Integer, Long> termIds = new TreeMap<>();
    private final Map<Integer, Map<String, Long>> sectionsByYear = new TreeMap<>();
    private final Map<Long, Plan> plansByEnrollment = new LinkedHashMap<>();
    private final Map<Integer, List<Long>> activeSectionsByYear = new TreeMap<>();
    private final Map<Long, Integer> classesBySection = new LinkedHashMap<>();
    private final Map<Integer, Long> boardIdsByKey = new LinkedHashMap<>();
    private long schemeId;
    private int folio;

    public MultiYearSimulation(DemoHttpClient api, JdbcTemplate jdbc, SimulationClockDriver driver, SimulationReport report, long seed,
                               DemoCatalog.ProgramSpec spec, long programId, long instructorId, Set<LocalDate> holidays) {
        this.holidays = holidays;
        this.api = api;
        this.jdbc = jdbc;
        this.driver = driver;
        this.report = report;
        this.seed = seed;
        this.spec = spec;
        this.programId = programId;
        this.instructorId = instructorId;
    }

    public void registerPupil(int index, StudentProfile profile, int cohortYear, long studentId) {
        pupils.add(new Pupil(index, profile, cohortYear, studentId));
    }

    public static Set<LocalDate> loadHolidays(DemoHttpClient api, SyntheticHolidayCalendar calendar, int fromYear, int toYear) {
        Set<LocalDate> holidays = new HashSet<>();
        Set<String> existing = new HashSet<>();
        for (JsonNode holiday : api.get(DemoPersona.ADMIN, "/holidays")) {
            existing.add(holiday.path("date").asString(""));
            holidays.add(LocalDate.parse(holiday.path("date").asString("")));
        }
        for (int year = fromYear; year <= toYear; year++) {
            for (SyntheticHolidayCalendar.SyntheticHoliday holiday : calendar.holidaysOf(year)) {
                if (existing.add(holiday.date().toString())) {
                    api.post(DemoPersona.ADMIN, "/holidays", Map.of("date", holiday.date().toString(), "name", holiday.name(), "type", "NATIONAL"));
                    holidays.add(holiday.date());
                }
            }
        }
        return holidays;
    }

    public void adoptSeededYear(int year) {
        termIds.put(year, annualTermId(year));
        schemeId = api.post(DemoPersona.DIRECTOR, "/evaluation-schemes", scheme()).get("id").asLong();
        sectionsByYear.put(year, sectionsOf(year));
        sectionsByYear.get(year).values().forEach(sectionId ->
                api.put(DemoPersona.ADMIN, "/program-sections/" + sectionId, Map.of("evaluationSchemeId", schemeId)));
    }

    public void run(LocalDate firstDay, LocalDate lastDay) {
        long startedAt = System.currentTimeMillis();
        int days = driver.run(firstDay, lastDay, this::simulateDay);
        report.finish(days, System.currentTimeMillis() - startedAt);
        recordOutcomes();
    }

    private void simulateDay(LocalDate day) {
        int year = day.getYear();
        int month = day.getMonthValue();
        int dayOfMonth = day.getDayOfMonth();
        if (month == 2 && dayOfMonth == 10 && year > FIRST_YEAR) {
            createFinalBoards(year);
        } else if (month == 2 && dayOfMonth == 12 && year > FIRST_YEAR) {
            registerForFinals(year);
        } else if (month == 2 && dayOfMonth == 20 && year > FIRST_YEAR) {
            gradeFinals(year);
        } else if (month == 2 && dayOfMonth == 21 && year > FIRST_YEAR) {
            closeFinalBoards();
        } else if (month == 2 && dayOfMonth == 22 && year > FIRST_YEAR) {
            openYear(year);
            handleGraduations();
        } else if (month == 2 && dayOfMonth == 25) {
            enrollPupils(year);
        } else if (month == 6 && dayOfMonth == 15) {
            dropOut(year);
        } else if (month == 12 && dayOfMonth == 21) {
            closeYear(year);
        }
        if (isClassDay(day) && month >= 3 && month <= 11) {
            List<Long> sections = activeSectionsByYear.getOrDefault(year, List.of());
            for (int position = 0; position < sections.size(); position++) {
                if (day.getDayOfWeek() == DayOfWeek.of(1 + position % 5)) {
                    holdClass(sections.get(position), position, day);
                }
            }
        }
    }

    private boolean isClassDay(LocalDate day) {
        return day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(day);
    }

    private void openYear(int year) {
        Map<String, Object> term = new LinkedHashMap<>();
        term.put("year", year);
        term.put("description", "Ciclo lectivo " + year);
        term.put("type", "ANNUAL");
        term.put("startDate", year + "-03-01T00:00:00");
        term.put("endDate", year + "-12-20T00:00:00");
        term.put("enrollmentStart", (year - 1) + "-12-01T00:00:00");
        term.put("enrollmentEnd", year + "-12-20T00:00:00");
        long termId = api.post(DemoPersona.ADMIN, "/terms", term).get("id").asLong();
        api.post(DemoPersona.ADMIN, "/terms/" + termId + "/enrollment/open", null);
        termIds.put(year, termId);
        long cycleId = api.post(DemoPersona.ADMIN, "/academic-offering-cycles", Map.of("programId", programId, "termId", termId)).get("id").asLong();
        for (JsonNode link : api.get(DemoPersona.ADMIN, "/programs/" + programId + "/subjects")) {
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("name", "Comisión A");
            section.put("subjectId", link.get("subjectId").asLong());
            section.put("capacity", 30);
            section.put("shift", "MORNING");
            long sectionId = api.post(DemoPersona.ADMIN, "/academic-offering-cycles/" + cycleId + "/sections", section).asLong();
            api.put(DemoPersona.ADMIN, "/program-sections/" + sectionId, Map.of("evaluationSchemeId", schemeId));
            api.post(DemoPersona.ADMIN, "/program-sections/" + sectionId + "/instructors", Map.of("instructorId", instructorId, "role", "LEAD"));
        }
        api.post(DemoPersona.ADMIN, "/academic-offering-cycles/" + cycleId + "/submit", null);
        api.post(DemoPersona.DIRECTOR, "/academic-offering-cycles/" + cycleId + "/approve", null);
        sectionsByYear.put(year, sectionsOf(year));
        report.count("yearsOpened");
    }

    private void enrollPupils(int year) {
        long cohortId = 0;
        List<Long> active = new ArrayList<>();
        for (Pupil pupil : pupils) {
            if (pupil.cohortYear > year || pupil.profile == StudentProfile.DROPS_OUT && pupil.cohortYear < year - 1) {
                continue;
            }
            if (pupil.cohortYear == year) {
                if (cohortId == 0) {
                    cohortId = createCohort(year);
                }
                pupil.programEnrollmentId = api.post(DemoPersona.ADMIN, "/students/" + pupil.studentId + "/program-enrollments",
                        Map.of("programId", programId, "cohortId", cohortId)).get("id").asLong();
                report.count("cohortEnrollments");
            }
            if (!"ENROLLED".equals(programStatus(pupil))) {
                continue;
            }
            enrollInPendingSubjects(pupil, year, active);
        }
        active.sort(Long::compare);
        activeSectionsByYear.put(year, active);
    }

    private long createCohort(int year) {
        Map<String, Object> cohort = new LinkedHashMap<>();
        cohort.put("intake", "FIRST");
        cohort.put("admissionStart", (year - 1) + "-12-01");
        cohort.put("admissionEnd", year + "-12-31");
        cohort.put("cursadaStart", year + "-03-01");
        report.count("cohortsCreated");
        return api.post(DemoPersona.ADMIN, "/programs/" + programId + "/terms/" + termIds.get(year) + "/cohorts", cohort).get("id").asLong();
    }

    private void enrollInPendingSubjects(Pupil pupil, int year, List<Long> active) {
        Map<String, List<String>> statusesBySubject = statusesBySubject(pupil);
        for (int studyYear = 1; studyYear <= spec.subjectsByYear().size(); studyYear++) {
            List<String> subjects = spec.subjectsByYear().get(studyYear - 1);
            List<Integer> pending = new ArrayList<>();
            boolean blocked = false;
            for (int subjectIndex = 0; subjectIndex < subjects.size(); subjectIndex++) {
                List<String> statuses = statusesBySubject.getOrDefault(subjects.get(subjectIndex), List.of());
                if (statuses.contains("APPROVED")) {
                    continue;
                }
                if (statuses.contains("REGULAR")) {
                    blocked = true;
                    continue;
                }
                pending.add(subjectIndex);
            }
            if (pending.isEmpty() && !blocked) {
                continue;
            }
            for (int subjectIndex : pending) {
                long sectionId = sectionsByYear.get(year).get(subjects.get(subjectIndex));
                api.post(DemoPersona.ADMIN, "/program-enrollments/" + pupil.programEnrollmentId + "/section-enrollments", Map.of("sectionId", sectionId));
                long enrollmentId = jdbc.queryForObject("select id from subject_section_enrollment where program_enrollment_id = ? and program_section_id = ?",
                        Long.class, pupil.programEnrollmentId, sectionId);
                int attempt = statusesBySubject.getOrDefault(subjects.get(subjectIndex), List.of()).size() + 1;
                plansByEnrollment.put(enrollmentId, new Plan(pupil, studyYear, subjectIndex, attempt));
                if (!active.contains(sectionId)) {
                    active.add(sectionId);
                }
                report.count("sectionEnrollments");
            }
            return;
        }
    }

    private Map<String, List<String>> statusesBySubject(Pupil pupil) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        jdbc.query("""
                select sub.name, sse.status from subject_section_enrollment sse
                join subject_section ss on ss.id = sse.program_section_id
                join subject sub on sub.id = ss.subject_id
                where sse.program_enrollment_id = ? order by sse.id
                """, rs -> {
            result.computeIfAbsent(rs.getString(1), name -> new ArrayList<>()).add(rs.getString(2));
        }, pupil.programEnrollmentId);
        return result;
    }

    private String programStatus(Pupil pupil) {
        return jdbc.queryForObject("select status from program_enrollment where id = ?", String.class, pupil.programEnrollmentId);
    }

    private void holdClass(long sectionId, int position, LocalDate day) {
        List<Long> enrollmentIds = new ArrayList<>();
        api.get(DemoPersona.TEACHER, "/program-sections/" + sectionId + "/roster").forEach(entry -> enrollmentIds.add(entry.get("enrollmentId").asLong()));
        enrollmentIds.sort(Long::compare);
        if (enrollmentIds.isEmpty()) {
            return;
        }
        int classNumber = classesBySection.merge(sectionId, 1, Integer::sum);
        Random random = new Random(seed * 1_000_003L + position * 10_007L + day.toEpochDay());
        List<Map<String, Object>> entries = new ArrayList<>();
        int absences = 0;
        for (long enrollmentId : enrollmentIds) {
            Plan plan = plansByEnrollment.get(enrollmentId);
            boolean absent = random.nextDouble() < plan.pupil().profile.absenceRate(plan.studyYear(), plan.subjectIndex(), plan.attempt());
            absences += absent ? 1 : 0;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("enrollmentId", enrollmentId);
            entry.put("status", absent ? "ABSENT" : "PRESENT");
            entries.add(entry);
        }
        api.post(DemoPersona.TEACHER, "/program-sections/" + sectionId + "/attendance", Map.of("classDate", day.toString(), "entries", entries));
        report.count("attendanceSheets");
        report.recordEvent(day + " S" + position + " class#" + classNumber + " absent=" + absences);
        if (classNumber == FIRST_EXAM_CLASS || classNumber == SECOND_EXAM_CLASS) {
            holdExam(sectionId, enrollmentIds, classNumber == FIRST_EXAM_CLASS ? 1 : 2, day);
        }
    }

    private void holdExam(long sectionId, List<Long> enrollmentIds, int sequenceNumber, LocalDate day) {
        for (long enrollmentId : enrollmentIds) {
            Plan plan = plansByEnrollment.get(enrollmentId);
            BigDecimal score = plan.pupil().profile.examScore(plan.studyYear(), plan.subjectIndex(), plan.attempt(), sequenceNumber);
            if (score == null) {
                report.count("examsSkipped");
                continue;
            }
            Map<String, Object> gradeItem = new LinkedHashMap<>();
            gradeItem.put("type", "EXAM");
            gradeItem.put("sequenceNumber", sequenceNumber);
            gradeItem.put("date", day.atStartOfDay().toString());
            gradeItem.put("score", score);
            api.post(DemoPersona.TEACHER, "/section-enrollments/" + enrollmentId + "/grade-items", gradeItem);
            report.count("gradeItems");
        }
        api.post(DemoPersona.TEACHER, "/program-sections/" + sectionId + "/grade-items/publish", Map.of("gradeType", "EXAM", "sequenceNumber", sequenceNumber));
        report.count("examPublications");
        if (sequenceNumber == 1) {
            for (long enrollmentId : enrollmentIds) {
                Plan plan = plansByEnrollment.get(enrollmentId);
                if (plan.pupil().profile.needsRecovery(plan.studyYear(), plan.subjectIndex(), plan.attempt())) {
                    recoverFirstExam(enrollmentId);
                }
            }
        }
    }

    private void recoverFirstExam(long enrollmentId) {
        for (JsonNode item : api.get(DemoPersona.ADMIN, "/section-enrollments/" + enrollmentId + "/grade-items")) {
            if ("EXAM".equals(item.path("type").asString("")) && item.path("sequenceNumber").asInt() == 1) {
                api.post(DemoPersona.TEACHER, "/grade-items/" + item.get("id").asLong() + "/recovery", Map.of("score", StudentProfile.RECOVERY_SCORE));
                report.count("recoveries");
                return;
            }
        }
    }

    private void closeYear(int year) {
        for (long sectionId : activeSectionsByYear.getOrDefault(year, List.of())) {
            api.get(DemoPersona.TEACHER, "/program-sections/" + sectionId + "/closure-sheet");
            api.post(DemoPersona.TEACHER, "/program-sections/" + sectionId + "/closure/confirm", null);
            Map<String, Object> approval = new LinkedHashMap<>();
            approval.put("book", "L" + year);
            approval.put("folio", "F" + (++folio));
            approval.put("closeWithoutTeacherConfirmation", false);
            api.post(DemoPersona.ADMIN, "/program-sections/" + sectionId + "/closure/approve", approval);
            report.count("closures");
        }
    }

    private void handleGraduations() {
        for (Pupil pupil : pupils) {
            if (pupil.programEnrollmentId == null) {
                continue;
            }
            for (JsonNode request : api.get(DemoPersona.ADMIN, "/program-enrollments/" + pupil.programEnrollmentId + "/graduation-requests")) {
                long requestId = request.get("id").asLong();
                String status = request.path("status").asString("");
                report.recordEvent("graduation P" + pupil.index + " " + status);
                if ("REQUESTED".equals(status)) {
                    api.post(DemoPersona.ADMIN, "/graduation-requests/" + requestId + "/verify-debt", null);
                    status = "ADMIN_VERIFIED";
                }
                if ("ADMIN_VERIFIED".equals(status)) {
                    api.post(DemoPersona.DIRECTOR, "/graduation-requests/" + requestId + "/approve", null);
                    report.count("graduationsApproved");
                }
            }
        }
    }

    private void dropOut(int year) {
        for (Pupil pupil : pupils) {
            if (pupil.profile == StudentProfile.DROPS_OUT && pupil.cohortYear + 1 == year && pupil.programEnrollmentId != null) {
                api.post(DemoPersona.ADMIN, "/program-enrollments/" + pupil.programEnrollmentId + "/withdraw",
                        Map.of("effectiveDate", LocalDate.of(year, 6, 15).toString(), "reason", "Abandono simulado por el alumno"));
                report.count("withdrawals");
            }
        }
    }

    private List<Long> regularEnrollmentsOf(int closedYear) {
        return jdbc.queryForList("""
                select sse.id from subject_section_enrollment sse join subject_section ss on ss.id = sse.program_section_id
                join program_enrollment pe on pe.id = sse.program_enrollment_id
                where sse.status = 'REGULAR' and ss.term_id = ? and pe.status = 'ENROLLED' order by sse.id
                """, Long.class, termIds.get(closedYear));
    }

    private void createFinalBoards(int year) {
        boardIdsByKey.clear();
        for (long enrollmentId : regularEnrollmentsOf(year - 1)) {
            long subjectId = jdbc.queryForObject("select ss.subject_id from subject_section ss join subject_section_enrollment sse on sse.program_section_id = ss.id where sse.id = ?",
                    Long.class, enrollmentId);
            boardIdsByKey.computeIfAbsent((int) subjectId, key -> {
                Map<String, Object> board = new LinkedHashMap<>();
                board.put("subjectId", subjectId);
                board.put("termId", termIds.get(year - 1));
                board.put("examDate", LocalDateTime.of(year, 2, 20, 10, 0).toString());
                board.put("registrationStart", LocalDateTime.of(year, 2, 10, 0, 0).toString());
                board.put("registrationEnd", LocalDateTime.of(year, 2, 17, 0, 0).toString());
                board.put("classroom", "Aula 1");
                long boardId = api.post(DemoPersona.ADMIN, "/exam-boards", board).get("id").asLong();
                api.post(DemoPersona.ADMIN, "/exam-boards/" + boardId + "/tribunal", Map.of("instructorId", instructorId));
                report.count("examBoards");
                return boardId;
            });
        }
    }

    private final Map<Long, List<Long>> registrationsByBoard = new LinkedHashMap<>();

    private void registerForFinals(int year) {
        registrationsByBoard.clear();
        for (long enrollmentId : regularEnrollmentsOf(year - 1)) {
            long subjectId = jdbc.queryForObject("select ss.subject_id from subject_section ss join subject_section_enrollment sse on sse.program_section_id = ss.id where sse.id = ?",
                    Long.class, enrollmentId);
            long boardId = boardIdsByKey.get((int) subjectId);
            long registrationId = api.post(DemoPersona.ADMIN, "/exam-boards/" + boardId + "/registrations",
                    Map.of("examBoardId", boardId, "programSectionEnrollmentId", enrollmentId)).get("id").asLong();
            registrationsByBoard.computeIfAbsent(boardId, key -> new ArrayList<>()).add(registrationId);
            report.count("finalRegistrations");
        }
    }

    private void gradeFinals(int year) {
        registrationsByBoard.forEach((boardId, registrationIds) -> {
            List<Map<String, Object>> items = new ArrayList<>();
            for (long registrationId : registrationIds) {
                items.add(Map.of("registrationId", registrationId, "score", StudentProfile.FINAL_SCORE));
            }
            api.put(DemoPersona.TEACHER, "/exam-boards/" + boardId + "/final-grades", Map.of("items", items));
            report.count("finalGrades");
        });
    }

    private void closeFinalBoards() {
        registrationsByBoard.keySet().forEach(boardId -> {
            api.post(DemoPersona.ADMIN, "/exam-boards/" + boardId + "/close", null);
            report.count("examBoardsClosed");
        });
        registrationsByBoard.clear();
    }

    private Map<String, Long> sectionsOf(int year) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query("""
                select sub.name, ss.id from subject_section ss join subject sub on sub.id = ss.subject_id
                join academic_offering_cycle c on c.id = ss.academic_offering_cycle_id
                where c.program_id = ? and c.term_id = ? order by ss.id
                """, rs -> {
            result.put(rs.getString(1), rs.getLong(2));
        }, programId, termIds.get(year));
        return result;
    }

    private long annualTermId(int year) {
        for (JsonNode term : api.get(DemoPersona.ADMIN, "/terms")) {
            if (term.path("year").asInt() == year && "ANNUAL".equals(term.path("type").asString(""))) {
                return term.get("id").asLong();
            }
        }
        throw new IllegalStateException("No existe el ciclo lectivo " + year);
    }

    private Map<String, Object> scheme() {
        Map<String, Object> scheme = new LinkedHashMap<>();
        scheme.put("name", SCHEME_NAME);
        scheme.put("examCountExpected", 2);
        scheme.put("hasFinal", true);
        scheme.put("finalWeight", 0.5);
        scheme.put("aggregationMethod", "SIMPLE_AVERAGE");
        scheme.put("minRegularGrade", 4);
        scheme.put("minApprovalGrade", 6);
        scheme.put("promotionMinGrade", 7);
        scheme.put("recoveryQuota", 1);
        return scheme;
    }

    private void recordOutcomes() {
        for (Pupil pupil : pupils) {
            report.recordOutcome("P" + pupil.index + " " + pupil.profile + " cohort=" + pupil.cohortYear + " program=" + programStatus(pupil)
                    + " graduation=" + graduationStatuses(pupil) + " subjects=" + statusesBySubject(pupil));
        }
    }

    private List<String> graduationStatuses(Pupil pupil) {
        return jdbc.queryForList("select status from graduation_request where program_enrollment_id = ? order by id", String.class, pupil.programEnrollmentId);
    }

    public List<String> expectationFailures() {
        List<String> failures = new ArrayList<>();
        for (Pupil pupil : pupils) {
            Map<String, List<String>> statuses = statusesBySubject(pupil);
            String program = programStatus(pupil);
            String first = spec.subjectsByYear().get(0).get(0);
            switch (pupil.profile) {
                case GRADUATES, PASSES_EVERYTHING -> {
                    if (pupil.cohortYear + 3 <= LAST_YEAR) {
                        expect(failures, pupil, "COMPLETED".equals(program), "program " + program);
                    }
                    expect(failures, pupil, statuses.values().stream().noneMatch(list -> list.contains("FAILED")), "failed subject " + statuses);
                }
                case FAILS_AND_RETAKES -> expect(failures, pupil, List.of("FAILED", "APPROVED").equals(statuses.get(first)), "retake " + statuses.get(first));
                case BECOMES_FREE -> expect(failures, pupil, absencesOf(pupil, first) > 0 && !statuses.get(first).isEmpty()
                        && statuses.get(first).stream().noneMatch("ENROLLED"::equals), "free " + statuses.get(first));
                case RECOVERY_AND_FINAL -> expect(failures, pupil, statuses.get(spec.subjectsByYear().get(0).get(StudentProfile.FINAL_SUBJECT_INDEX)).equals(List.of("APPROVED")), "final exam approval " + statuses);
                case DROPS_OUT -> expect(failures, pupil, "WITHDRAWN".equals(program), "program " + program);
                case REPEATS_YEAR -> expect(failures, pupil, spec.subjectsByYear().get(0).stream().allMatch(name -> statuses.get(name).equals(List.of("FAILED", "APPROVED"))), "repeat " + statuses);
            }
        }
        return failures;
    }

    private int absencesOf(Pupil pupil, String subjectName) {
        return jdbc.queryForObject("""
                select count(*) from attendance a join subject_section_enrollment sse on sse.id = a.program_section_enrollment_id
                join subject_section ss on ss.id = sse.program_section_id join subject sub on sub.id = ss.subject_id
                where sse.program_enrollment_id = ? and sub.name = ? and a.status = 'ABSENT'
                """, Integer.class, pupil.programEnrollmentId, subjectName);
    }

    private void expect(List<String> failures, Pupil pupil, boolean condition, String detail) {
        if (!condition) {
            failures.add("P" + pupil.index + " " + pupil.profile + ": " + detail);
        }
    }

    public long completedWithGraduationRequestCount() {
        return pupils.stream().filter(pupil -> "COMPLETED".equals(programStatus(pupil)) && !graduationStatuses(pupil).isEmpty()).count();
    }
}

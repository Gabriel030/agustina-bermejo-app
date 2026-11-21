package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeCalendarIntegrationTest extends CourseworkCharacterizationSupport {

    private static final LocalDate TERM_START = LocalDate.of(2026, 3, 2);
    private static final LocalDate TERM_END = LocalDate.of(2026, 3, 27);
    private static final String MARCH_APRIL = "?from=2026-03-01&to=2026-04-30";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private SubjectSection boundedProgramSection(Classroom classroom) {
        Term term = classroom.section().getTerm();
        term.setStartDate(TERM_START.atStartOfDay());
        term.setEndDate(TERM_END.atTime(23, 59));
        entityManager.flush();
        return classroom.section();
    }

    private Classroom studentWithProgramSection(String key, String dniBase) {
        Classroom classroom = persistClassroom(key, dniBase, persistScheme(2, true, null),
                Percentage.ofPoints(new BigDecimal("25")), 10, 10, com.red_escuela.backend.academicstructure.domain.InstructorRole.ASSISTANT);
        boundedProgramSection(classroom);
        return classroom;
    }

    private com.red_escuela.backend.academicstructure.entity.Classroom persistRoom(String name) {
        com.red_escuela.backend.academicstructure.entity.Classroom room = new com.red_escuela.backend.academicstructure.entity.Classroom();
        room.setName(name);
        room.setCapacity(30);
        entityManager.persist(room);
        return room;
    }

    private void addProgramSlot(SubjectSection section, DayOfWeek day, String roomName) {
        SectionScheduleSlot slot = new SectionScheduleSlot();
        slot.setSubjectSection(section);
        slot.setClassroom(persistRoom(roomName));
        slot.setDayOfWeek(day);
        slot.setStartTime(LocalTime.of(18, 0));
        slot.setEndTime(LocalTime.of(20, 0));
        entityManager.persist(slot);
        entityManager.flush();
    }

    private void clearHolidays(LocalDate from, LocalDate to) {
        jdbcTemplate.update("delete from holiday where date between ? and ?", Date.valueOf(from), Date.valueOf(to));
    }

    private void addHoliday(LocalDate date, String name) {
        Holiday holiday = new Holiday();
        holiday.setDate(date);
        holiday.setName(name);
        holiday.setType(HolidayType.NATIONAL);
        entityManager.persist(holiday);
        entityManager.flush();
    }

    private void cancelProgramClass(SubjectSection section, LocalDate date, com.red_escuela.backend.academicstructure.entity.Instructor creator) {
        ClassSession session = new ClassSession();
        session.setSubjectSection(section);
        session.setClassDate(date);
        session.setStatus(ClassSessionStatus.CANCELED);
        session.setCreatedBy(creator);
        entityManager.persist(session);
        entityManager.flush();
    }

    private JsonNode eventsOf(Person actor, String query) throws Exception {
        entityManager.clear();
        return bodyOf(getAs(actor, "/me/calendar" + query).andExpect(status().isOk())).get("events");
    }

    private List<String> idsOf(JsonNode events) {
        List<String> ids = new ArrayList<>();
        events.forEach(event -> ids.add(event.get("id").asString()));
        return ids;
    }

    private ExamBoard persistBoard(Subject subject, Term term, LocalDateTime examDate, ExamBoardStatus boardStatus,
                                   LocalDateTime registrationEnd) {
        ExamBoard board = new ExamBoard();
        board.setSubject(subject);
        board.setTerm(term);
        board.setExamDate(examDate);
        board.setStatus(boardStatus);
        board.setClassroom("Aula Magna");
        board.setRegistrationEnd(registrationEnd);
        entityManager.persist(board);
        entityManager.flush();
        return board;
    }

    private void register(ExamBoard board, SubjectSectionEnrollment enrollment) {
        ExamBoardRegistration registration = new ExamBoardRegistration();
        registration.setExamBoard(board);
        registration.setProgramSubjectSectionEnrollment(enrollment);
        registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        registration.setRegisteredAt(LocalDateTime.now());
        entityManager.persist(registration);
        entityManager.flush();
    }

    @Test
    void weeklyClassesStayInsideTheTermAndHolidayReplacesTheClassAndCanceledClassIsMarked() throws Exception {
        clearHolidays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30));
        Classroom classroom = studentWithProgramSection("calw", "7710001");
        addProgramSlot(classroom.section(), DayOfWeek.MONDAY, "Aula 7");
        addHoliday(LocalDate.of(2026, 3, 9), "Feriado de prueba");
        cancelProgramClass(classroom.section(), LocalDate.of(2026, 3, 16), classroom.instructor());

        JsonNode events = eventsOf(classroom.student(), MARCH_APRIL);

        long sectionId = classroom.section().getId();
        assertThat(idsOf(events)).containsExactly(
                "class-program-" + sectionId + "-2026-03-02-18:00",
                "holiday-2026-03-09",
                "class-program-" + sectionId + "-2026-03-16-18:00",
                "class-program-" + sectionId + "-2026-03-23-18:00");
        JsonNode first = events.get(0);
        assertThat(first.get("type").asString()).isEqualTo("CLASS");
        assertThat(first.get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(first.get("startTime").asString()).isEqualTo("18:00");
        assertThat(first.get("endTime").asString()).isEqualTo("20:00");
        assertThat(first.get("title").asString()).isEqualTo(classroom.subject().getName());
        assertThat(first.get("subjectName").asString()).isEqualTo(classroom.subject().getName());
        assertThat(first.get("sectionName").asString()).isEqualTo("Comision calw");
        assertThat(first.get("place").asString()).isEqualTo("Aula 7");
        assertThat(events.get(1).get("type").asString()).isEqualTo("HOLIDAY");
        assertThat(events.get(1).get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(events.get(2).get("status").asString()).isEqualTo("CANCELED");
        assertThat(events.get(2).get("reason").isNull()).isTrue();
    }

    @Test
    void courseSectionClassesAreComputedAsWellAsProgramOnes() throws Exception {
        clearHolidays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        Person person = persistPerson("calc.student", "7710002", "ALUMNO");
        Student student = persistStudent(person);
        Term term = persistTerm(TERM_START.atStartOfDay(), TERM_END.atTime(23, 59));
        Course course = new Course();
        course.setName("Curso calc");
        course.setTerm(term);
        entityManager.persist(course);
        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setCourse(course);
        courseSubject.setName("Modulo calc");
        entityManager.persist(courseSubject);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comision calc");
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        CourseEnrollment courseEnrollment = new CourseEnrollment();
        courseEnrollment.setStudent(student);
        courseEnrollment.setCourse(course);
        courseEnrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        courseEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(courseEnrollment);
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourseEnrollment(courseEnrollment);
        enrollment.setCourseSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.now());
        entityManager.persist(enrollment);
        SectionScheduleSlot slot = new SectionScheduleSlot();
        slot.setCourseSection(section);
        slot.setClassroom(persistRoom("Aula curso"));
        slot.setDayOfWeek(DayOfWeek.WEDNESDAY);
        slot.setStartTime(LocalTime.of(9, 30));
        slot.setEndTime(LocalTime.of(11, 0));
        entityManager.persist(slot);
        entityManager.flush();

        JsonNode events = eventsOf(person, "?from=2026-03-01&to=2026-03-31");

        assertThat(idsOf(events)).containsExactly(
                "class-course-" + section.getId() + "-2026-03-04-09:30",
                "class-course-" + section.getId() + "-2026-03-11-09:30",
                "class-course-" + section.getId() + "-2026-03-18-09:30",
                "class-course-" + section.getId() + "-2026-03-25-09:30");
        assertThat(events.get(0).get("title").asString()).isEqualTo("Modulo calc");
    }

    @Test
    void examBoardsAreRegisteredOpenCanceledAndPastOpenBoardsAreDropped() throws Exception {
        Classroom classroom = studentWithProgramSection("cale", "7710003");
        LocalDateTime nextWeek = LocalDateTime.now().plusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDate boardDay = nextWeek.toLocalDate();
        clearHolidays(LocalDate.now().minusDays(5), LocalDate.now().plusDays(20));
        addHoliday(boardDay, "Feriado con mesa");
        Subject subject = classroom.subject();
        Term term = classroom.section().getTerm();
        ExamBoard registered = persistBoard(subject, term, nextWeek, ExamBoardStatus.SCHEDULED, null);
        register(registered, classroom.enrollment());
        ExamBoard open = persistBoard(subject, term, nextWeek.plusDays(1), ExamBoardStatus.SCHEDULED, null);
        ExamBoard canceled = persistBoard(subject, term, nextWeek.plusDays(2), ExamBoardStatus.CANCELED, null);
        register(canceled, classroom.enrollment());
        ExamBoard pastWithNullWindow = persistBoard(subject, term, LocalDateTime.now().minusDays(3), ExamBoardStatus.SCHEDULED, null);
        String range = "?from=" + LocalDate.now().minusDays(5) + "&to=" + LocalDate.now().plusDays(20);

        JsonNode events = eventsOf(classroom.student(), range);

        assertThat(idsOf(events)).containsExactly("exam-" + registered.getId(), "holiday-" + boardDay,
                "exam-" + open.getId(), "exam-" + canceled.getId());
        assertThat(events.get(0).get("status").asString()).isEqualTo("REGISTERED");
        assertThat(events.get(0).get("place").asString()).isEqualTo("Aula Magna");
        assertThat(events.get(0).get("link").asString()).isEqualTo("/alumno/mis-carreras");
        assertThat(events.get(2).get("status").asString()).isEqualTo("OPEN_FOR_REGISTRATION");
        assertThat(events.get(3).get("status").asString()).isEqualTo("CANCELED");
        assertThat(idsOf(events)).doesNotContain("exam-" + pastWithNullWindow.getId());
    }

    @Test
    void exportAndPublicFeedServeTheRealCalendarOfTheOwner() throws Exception {
        Classroom classroom = studentWithProgramSection("calf", "7710004");
        ExamBoard board = persistBoard(classroom.subject(), classroom.section().getTerm(),
                LocalDateTime.now().plusDays(10), ExamBoardStatus.SCHEDULED, null);
        register(board, classroom.enrollment());
        entityManager.clear();

        String export = getAs(classroom.student(), "/me/calendar/export.ics").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = bodyOf(postEmpty(classroom.student(), "/me/calendar/feed/token").andExpect(status().isCreated()))
                .get("token").asString();
        String feed = mockMvc.perform(get("/public/calendar/" + token + ".ics")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(export).contains("UID:exam-" + board.getId() + "@");
        assertThat(feed).contains("UID:exam-" + board.getId() + "@");
    }

    @Test
    void anotherStudentNeverSeesTheEventsOfTheOwner() throws Exception {
        clearHolidays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30));
        Classroom owner = studentWithProgramSection("calo", "7710005");
        addProgramSlot(owner.section(), DayOfWeek.MONDAY, "Aula 1");
        Person stranger = persistPerson("calo.other", "7710006", "ALUMNO");
        persistStudent(stranger);
        entityManager.flush();

        assertThat(eventsOf(owner.student(), MARCH_APRIL)).hasSize(4);
        JsonNode body = bodyOf(getAs(stranger, "/me/calendar" + MARCH_APRIL).andExpect(status().isOk()));

        assertThat(body.get("events")).isEmpty();
        assertThat(body.get("hasActiveEnrollments").asBoolean()).isFalse();
    }

    @Test
    void nonStudentGetsEmptyCalendar() throws Exception {
        Person teacher = persistPerson("calt.teacher", "7710007", "DOCENTE");

        JsonNode body = bodyOf(getAs(teacher, "/me/calendar" + MARCH_APRIL).andExpect(status().isOk()));

        assertThat(body.get("events")).isEmpty();
        assertThat(body.get("hasActiveEnrollments").asBoolean()).isFalse();
        assertThat(body.get("generatedAt").isNull()).isFalse();
    }

    @Test
    void enrolledStudentWithoutClassesInTheRangeStillHasActiveEnrollments() throws Exception {
        clearHolidays(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31));
        Classroom classroom = studentWithProgramSection("calh", "7710008");
        addProgramSlot(classroom.section(), DayOfWeek.MONDAY, "Aula 1");

        JsonNode body = bodyOf(getAs(classroom.student(), "/me/calendar?from=2030-01-01&to=2030-01-31").andExpect(status().isOk()));

        assertThat(body.get("events")).isEmpty();
        assertThat(body.get("hasActiveEnrollments").asBoolean()).isTrue();
    }

    @Test
    void rangeOfSixtyTwoDaysIsAcceptedAndSixtyThreeIsRejected() throws Exception {
        Person person = persistPerson("calr.student", "7710009", "ALUMNO");
        persistStudent(person);

        getAs(person, "/me/calendar?from=2026-03-01&to=2026-05-01").andExpect(status().isOk());
        getAs(person, "/me/calendar?from=2026-03-01&to=2026-05-02").andExpect(status().isBadRequest());
        getAs(person, "/me/calendar?from=2026-03-05&to=2026-03-01").andExpect(status().isBadRequest());
        getAs(person, "/me/calendar?from=2026-03-05").andExpect(status().isBadRequest());
        getAs(person, "/me/calendar").andExpect(status().isBadRequest());
    }

    @Test
    void calendarIsCachedPrivatelyWhileFeedAndExportStayNoStore() throws Exception {
        Person person = persistPerson("calx.student", "7710010", "ALUMNO");
        persistStudent(person);

        MockHttpServletResponse calendar = getAs(person, "/me/calendar" + MARCH_APRIL).andReturn().getResponse();
        MockHttpServletResponse feed = getAs(person, "/me/calendar/feed").andReturn().getResponse();
        MockHttpServletResponse export = getAs(person, "/me/calendar/export.ics").andReturn().getResponse();

        assertThat(calendar.getHeaders("Cache-Control")).containsExactly("private, max-age=60");
        assertThat(feed.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(export.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void statementCountDoesNotGrowWithTheNumberOfSections() throws Exception {
        clearHolidays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30));
        Classroom classroom = studentWithProgramSection("caln", "7710011");
        addProgramSlot(classroom.section(), DayOfWeek.MONDAY, "Aula 1");
        long withOneSection = statementsOf(classroom.student());

        for (int index = 0; index < 7; index++) {
            Subject subject = persistSubject("Materia extra " + index);
            SubjectSection extra = new SubjectSection();
            extra.setName("Comision extra " + index);
            extra.setSubject(subject);
            extra.setTerm(classroom.section().getTerm());
            extra.setEvaluationScheme(classroom.section().getEvaluationScheme());
            extra.setMaxAbsenceRate(classroom.section().getMaxAbsenceRate());
            extra.setCapacity(30);
            extra.setStatus(StatusType.ACTIVE);
            entityManager.persist(extra);
            SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
            enrollment.setStudent(classroom.enrollment().getStudent());
            enrollment.setProgramEnrollment(classroom.programEnrollment());
            enrollment.setSubjectSection(extra);
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            enrollment.setEnrolledAt(LocalDateTime.now());
            enrollment.setAttemptNumber(1);
            entityManager.persist(enrollment);
            addProgramSlot(extra, DayOfWeek.TUESDAY, "Aula extra " + index);
        }
        long withEightSections = statementsOf(classroom.student());

        assertThat(withEightSections).isLessThanOrEqualTo(withOneSection);
    }

    private long statementsOf(Person student) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        try {
            getAs(student, "/me/calendar" + MARCH_APRIL).andExpect(status().isOk());
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }
}

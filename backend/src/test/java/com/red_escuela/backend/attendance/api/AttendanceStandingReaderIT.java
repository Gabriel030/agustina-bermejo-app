package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.specialist.SectionRosterBuilder;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceStandingReaderIT extends AbstractIntegrationTest {

    private static final LocalDate FIRST_CLASS = LocalDate.of(2025, 9, 1);

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private AttendanceStandingReader reader;
    @Autowired
    private SectionAbsenceCountsReader absenceCountsReader;
    @Autowired
    private SectionFreeByAbsencesFinder freeFinder;
    @Autowired
    private SectionPresencePercentFinder presenceFinder;
    @Autowired
    private SectionRosterBuilder rosterBuilder;

    private CycleTestFixtures fixtures;
    private SubjectSection section;
    private Instructor recorder;
    private ProgramEnrollment programEnrollment;
    private SubjectSectionEnrollment onTheLimit;
    private SubjectSectionEnrollment overTheLimit;
    private SubjectSectionEnrollment withoutClasses;

    @BeforeEach
    void seedSectionWithThreeStudents() {
        fixtures = new CycleTestFixtures(entityManager);
        Term term = fixtures.term(2040 + (int) (System.nanoTime() % 50));
        var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
        section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
        section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
        recorder = fixtures.instructor();
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        programEnrollment = new ProgramEnrollment();
        Student first = student(institution);
        programEnrollment.setStudent(first);
        programEnrollment.setProgram(fixtures.program());
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
        onTheLimit = enrollmentOf(first);
        overTheLimit = enrollmentOf(student(institution));
        withoutClasses = enrollmentOf(student(institution));
        record(onTheLimit, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.LATE,
                AttendanceStatus.JUSTIFIED, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT,
                AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFICATION_PENDING);
        record(overTheLimit, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT,
                AttendanceStatus.PRESENT, AttendanceStatus.ABSENT, AttendanceStatus.ABSENT,
                AttendanceStatus.JUSTIFICATION_PENDING, AttendanceStatus.JUSTIFIED);
        entityManager.flush();
    }

    @Test
    void b2IT01EnrollmentStandingCountsPendingJustificationAsCountableAbsence() {
        AttendanceStanding standing = reader.readProgramEnrollmentStanding(overTheLimit.getId());

        assertThat(standing.classesHeld()).isEqualTo(8);
        assertThat(standing.countableAbsences()).isEqualTo(3);
        assertThat(standing.pendingJustificationAbsences()).isEqualTo(1);
        assertThat(standing.absencePercent().points()).isEqualByComparingTo("37.5");
        assertThat(standing.exceedsAbsenceLimit()).isTrue();
        assertThat(standing.allowedAbsences()).isEqualTo(2);
        assertThat(standing.remainingAbsences()).isZero();
    }

    @Test
    void b2IT02AnEnrollmentExactlyOnTheLimitIsNotFreeWhileItsNeighbourIs() {
        assertThat(reader.readProgramEnrollmentStanding(onTheLimit.getId()).exceedsAbsenceLimit()).isFalse();
        assertThat(reader.readProgramEnrollmentStanding(overTheLimit.getId()).exceedsAbsenceLimit()).isTrue();
        assertThat(freeFinder.findProgramEnrollmentIdsFreeByAbsences(section.getId(), section.getMaxAbsenceRate()))
                .containsExactly(overTheLimit.getId());
    }

    @Test
    void b2IT03BatchAndSectionReadsAgreeWithTheSingleEnrollmentRead() {
        List<Long> ids = List.of(onTheLimit.getId(), overTheLimit.getId(), withoutClasses.getId());
        Map<Long, AttendanceStanding> batch = reader.readProgramEnrollmentStandings(ids);
        Map<Long, AttendanceStanding> bySection = reader.readProgramSectionStandings(section.getId(), section.getMaxAbsenceRate());

        for (Long id : List.of(onTheLimit.getId(), overTheLimit.getId())) {
            assertThat(batch.get(id)).isEqualTo(reader.readProgramEnrollmentStanding(id));
            assertThat(bySection.get(id)).isEqualTo(batch.get(id));
        }
        assertThat(batch.get(withoutClasses.getId()).classesHeld()).isZero();
        assertThat(batch.get(withoutClasses.getId()).exceedsAbsenceLimit()).isFalse();
        assertThat(bySection).doesNotContainKey(withoutClasses.getId());
    }

    @Test
    void b2IT04NoticesCountPendingJustificationLikeTheFreeDecision() {
        Map<Long, EnrollmentAbsenceCountsView> counts = absenceCountsReader.countByProgramEnrollments(
                List.of(onTheLimit.getId(), overTheLimit.getId()));

        assertThat(counts.get(overTheLimit.getId())).isEqualTo(new EnrollmentAbsenceCountsView(8, 3, 1));
        assertThat(counts.get(onTheLimit.getId())).isEqualTo(new EnrollmentAbsenceCountsView(8, 2, 1));
    }

    @Test
    void b2IT05RosterShowsTheSameCountAndTheSameFreeDecision() {
        Map<Long, RosterEntryResponse> roster = new java.util.HashMap<>();
        rosterBuilder.buildForProgramSection(section.getId()).forEach(entry -> roster.put(entry.enrollmentId(), entry));

        RosterEntryResponse over = roster.get(overTheLimit.getId());
        assertThat(over.absenceCount()).isEqualTo(3);
        assertThat(over.atRisk()).isTrue();
        assertThat(over.pendingJustificationAbsences()).isEqualTo(1);
        assertThat(over.remainingAbsences()).isZero();
        assertThat(roster.get(onTheLimit.getId()).atRisk()).isFalse();
        assertThat(roster.get(onTheLimit.getId()).remainingAbsences()).isZero();
        assertThat(roster.get(withoutClasses.getId()).classesHeld()).isZero();
        assertThat(roster.get(withoutClasses.getId()).remainingAbsences()).isNull();
    }

    @Test
    void b2IT06PresencePercentComesFromTheSameCounts() {
        Map<Long, Percentage> presence = presenceFinder.findPresencePercentByEnrollment(SectionKind.PROGRAM, section.getId());

        assertThat(presence.get(onTheLimit.getId()).points()).isEqualByComparingTo("75");
        assertThat(presence.get(overTheLimit.getId()).points()).isEqualByComparingTo("62.5");
        assertThat(presence).doesNotContainKey(withoutClasses.getId());
    }

    private Student student(Institution institution) {
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private SubjectSectionEnrollment enrollmentOf(Student student) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }

    private void record(SubjectSectionEnrollment enrollment, AttendanceStatus... statuses) {
        for (int index = 0; index < statuses.length; index++) {
            Attendance attendance = new Attendance();
            attendance.setProgramSubjectSectionEnrollment(enrollment);
            attendance.setClassDate(FIRST_CLASS.plusDays(index));
            attendance.setStatus(statuses[index]);
            attendance.setRecordedBy(recorder);
            entityManager.persist(attendance);
        }
    }
}

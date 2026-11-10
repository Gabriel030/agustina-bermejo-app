package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.GradeItemAuditResponse;
import com.red_escuela.backend.evaluation.dto.PublishGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.RecordExamRecoveryRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemAuditKind;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.events.GradeCorrectedEvent;
import com.red_escuela.backend.evaluation.exception.GradeItemAlreadyRecoveredException;
import com.red_escuela.backend.evaluation.exception.GradeItemNotPublishedException;
import com.red_escuela.backend.evaluation.exception.GradeItemNotRecoverableException;
import com.red_escuela.backend.evaluation.exception.PendingCourseworkException;
import com.red_escuela.backend.evaluation.exception.RecoveryQuotaExceededException;
import com.red_escuela.backend.evaluation.repository.GradeItemAuditRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class ExamRecoveryRecorderTest extends AbstractIntegrationTest {

    @Autowired
    private ExamRecoveryRecorder examRecoveryRecorder;

    @Autowired
    private GradeItemRecorder gradeItemRecorder;

    @Autowired
    private SectionGradeItemPublisher sectionGradeItemPublisher;

    @Autowired
    private GradeItemRepository gradeItemRepository;

    @Autowired
    private GradeItemAuditRepository gradeItemAuditRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private EntityManager entityManager;

    private int dniSequence = 91000000;

    private record Fixture(Instructor instructor, SubjectSection section, SubjectSectionEnrollment enrollment) {
    }

    private Person persistPerson(String name) {
        String dni = String.valueOf(dniSequence++);
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName(name);
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Instructor persistInstructor() {
        Instructor instructor = new Instructor();
        instructor.setPerson(persistPerson("Docente"));
        entityManager.persist(instructor);
        return instructor;
    }

    private Fixture fixture(int examCount, int tpCount, Integer recoveryQuota) {
        Instructor instructor = persistInstructor();
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema de recuperatorios");
        scheme.setExamCountExpected(examCount);
        scheme.setTpCountExpected(tpCount);
        scheme.setHasFinal(true);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
        if (recoveryQuota != null) {
            scheme.setRecoveryQuota(recoveryQuota);
        }
        entityManager.persist(scheme);
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia recuperatorios " + dniSequence);
        entityManager.persist(subject);
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setEvaluationScheme(scheme);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(persistPerson("Alumno"));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        Program program = new Program();
        program.setName("Carrera " + dniSequence);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return new Fixture(instructor, section, enrollment);
    }

    private void load(Fixture fixture, GradeItemType type, int sequence, double score) {
        gradeItemRecorder.loadProgramEnrollmentGrade(fixture.enrollment().getId(), fixture.instructor().getId(),
                new CreateGradeItemRequest(type, sequence, null, LocalDateTime.now(), BigDecimal.valueOf(score), null, null));
    }

    private void publish(Fixture fixture, GradeItemType type, int sequence) {
        entityManager.flush();
        sectionGradeItemPublisher.publishProgramSection(fixture.section().getId(), fixture.instructor().getId(),
                fixture.instructor().getPerson().getId(), new PublishGradeItemRequest(type, sequence));
        entityManager.flush();
    }

    private Fixture publishedExams(int tpCount, Integer recoveryQuota, double first, double second) {
        Fixture fixture = fixture(2, tpCount, recoveryQuota);
        for (int sequence = 1; sequence <= tpCount; sequence++) {
            load(fixture, GradeItemType.TP, sequence, 7.0);
        }
        load(fixture, GradeItemType.EXAM, 1, first);
        load(fixture, GradeItemType.EXAM, 2, second);
        publish(fixture, GradeItemType.EXAM, 1);
        publish(fixture, GradeItemType.EXAM, 2);
        return fixture;
    }

    private GradeItem item(Fixture fixture, GradeItemType type, int sequence) {
        entityManager.flush();
        return gradeItemRepository.findByProgramSubjectSectionEnrollmentId(fixture.enrollment().getId()).stream()
                .filter(candidate -> candidate.getType() == type && candidate.getSequenceNumber() == sequence)
                .findFirst().orElseThrow();
    }

    private GradeItemAuditResponse recover(Fixture fixture, GradeItem item, double score) {
        return examRecoveryRecorder.record(item.getId(), fixture.instructor().getId(),
                fixture.instructor().getPerson().getId(), new RecordExamRecoveryRequest(BigDecimal.valueOf(score)));
    }

    @Test
    void recoveryReplacesTheEffectiveScoreKeepsTheOriginalAndAuditsIt() {
        Fixture fixture = publishedExams(0, null, 3.0, 4.0);
        GradeItem first = item(fixture, GradeItemType.EXAM, 1);

        GradeItemAuditResponse audit = recover(fixture, first, 6.0);
        entityManager.flush();

        GradeItem stored = gradeItemRepository.findById(first.getId()).orElseThrow();
        assertThat(stored.getScore()).isEqualTo(Grade.of(BigDecimal.valueOf(6.0)));
        assertThat(stored.getOriginalScore()).isEqualTo(Grade.of(BigDecimal.valueOf(3.0)));
        assertThat(stored.isRecovered()).isTrue();
        assertThat(stored.getRecoveryLoadedBy()).isEqualTo(fixture.instructor().getPerson().getId());
        assertThat(audit.kind()).isEqualTo(GradeItemAuditKind.RECOVERY);
        assertThat(audit.previousScore()).isEqualTo(Grade.of(BigDecimal.valueOf(3.0)));
        assertThat(audit.newScore()).isEqualTo(Grade.of(BigDecimal.valueOf(6.0)));
        assertThat(gradeItemAuditRepository.findAll()).filteredOn(row -> row.getKind() == GradeItemAuditKind.RECOVERY).hasSize(1);
        assertThat(applicationEvents.stream(GradeCorrectedEvent.class).toList()).hasSize(1);
    }

    @Test
    void recoveryScoreCountsForTheConditionEvenWhenItIsLowerThanTheOriginal() {
        Fixture fixture = publishedExams(0, null, 8.0, 6.0);
        entityManager.refresh(fixture.enrollment());
        assertThat(fixture.enrollment().getStatus()).isEqualTo(EnrollmentStatus.REGULAR);

        recover(fixture, item(fixture, GradeItemType.EXAM, 1), 1.0);
        entityManager.flush();
        entityManager.refresh(fixture.enrollment());

        GradeItem first = item(fixture, GradeItemType.EXAM, 1);
        assertThat(first.getScore()).isEqualTo(Grade.of(BigDecimal.valueOf(1.0)));
        assertThat(first.getOriginalScore()).isEqualTo(Grade.of(BigDecimal.valueOf(8.0)));
        assertThat(fixture.enrollment().getStatus()).isEqualTo(EnrollmentStatus.FAILED);
    }

    @Test
    void recoveryLiftsAFailedConditionToRegular() {
        Fixture fixture = publishedExams(0, null, 3.0, 4.0);
        entityManager.refresh(fixture.enrollment());
        assertThat(fixture.enrollment().getStatus()).isEqualTo(EnrollmentStatus.FAILED);

        recover(fixture, item(fixture, GradeItemType.EXAM, 1), 6.0);
        entityManager.flush();
        entityManager.refresh(fixture.enrollment());

        assertThat(fixture.enrollment().getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void defaultQuotaOfOneIsSharedBetweenTheExamsOfTheSubject() {
        Fixture fixture = publishedExams(0, null, 3.0, 4.0);
        recover(fixture, item(fixture, GradeItemType.EXAM, 1), 6.0);

        assertThatThrownBy(() -> recover(fixture, item(fixture, GradeItemType.EXAM, 2), 6.0))
                .isInstanceOf(RecoveryQuotaExceededException.class);
        assertThat(item(fixture, GradeItemType.EXAM, 2).isRecovered()).isFalse();
    }

    @Test
    void aLargerQuotaAllowsRecoveringMoreThanOneExam() {
        Fixture fixture = publishedExams(0, 2, 3.0, 4.0);

        recover(fixture, item(fixture, GradeItemType.EXAM, 1), 6.0);
        recover(fixture, item(fixture, GradeItemType.EXAM, 2), 7.0);

        assertThat(item(fixture, GradeItemType.EXAM, 2).getScore()).isEqualTo(Grade.of(BigDecimal.valueOf(7.0)));
    }

    @Test
    void aQuotaOfZeroDisablesRecoveries() {
        Fixture fixture = publishedExams(0, 0, 3.0, 4.0);

        assertThatThrownBy(() -> recover(fixture, item(fixture, GradeItemType.EXAM, 1), 6.0))
                .isInstanceOf(RecoveryQuotaExceededException.class);
    }

    @Test
    void theSameExamCannotBeRecoveredTwice() {
        Fixture fixture = publishedExams(0, 3, 3.0, 4.0);
        GradeItem first = item(fixture, GradeItemType.EXAM, 1);
        recover(fixture, first, 6.0);

        assertThatThrownBy(() -> recover(fixture, first, 7.0)).isInstanceOf(GradeItemAlreadyRecoveredException.class);
    }

    @Test
    void anApprovedExamCanBeRecovered() {
        Fixture fixture = publishedExams(0, null, 8.0, 9.0);

        recover(fixture, item(fixture, GradeItemType.EXAM, 1), 5.0);

        GradeItem first = item(fixture, GradeItemType.EXAM, 1);
        assertThat(first.getScore()).isEqualTo(Grade.of(BigDecimal.valueOf(5.0)));
        assertThat(first.getOriginalScore()).isEqualTo(Grade.of(BigDecimal.valueOf(8.0)));
    }

    @Test
    void anUnpublishedExamCannotBeRecovered() {
        Fixture fixture = fixture(2, 0, null);
        load(fixture, GradeItemType.EXAM, 1, 3.0);

        assertThatThrownBy(() -> recover(fixture, item(fixture, GradeItemType.EXAM, 1), 6.0))
                .isInstanceOf(GradeItemNotPublishedException.class);
    }

    @Test
    void onlyExamsHaveARecovery() {
        Fixture fixture = fixture(2, 1, null);
        load(fixture, GradeItemType.TP, 1, 7.0);

        assertThatThrownBy(() -> recover(fixture, item(fixture, GradeItemType.TP, 1), 6.0))
                .isInstanceOf(GradeItemNotRecoverableException.class);
    }

    @Test
    void anInstructorOutsideTheSectionCannotRecordTheRecovery() {
        Fixture fixture = publishedExams(0, null, 3.0, 4.0);
        Instructor outsider = persistInstructor();
        GradeItem first = item(fixture, GradeItemType.EXAM, 1);

        assertThatThrownBy(() -> examRecoveryRecorder.record(first.getId(), outsider.getId(), outsider.getPerson().getId(),
                new RecordExamRecoveryRequest(BigDecimal.valueOf(6.0))))
                .isInstanceOf(NotOwnSectionException.class);
    }

    @Test
    void loadingAnExamWithoutApprovedCourseworkIsRejected() {
        Fixture fixture = fixture(2, 2, null);
        load(fixture, GradeItemType.TP, 1, 7.0);

        assertThatThrownBy(() -> load(fixture, GradeItemType.EXAM, 1, 6.0)).isInstanceOf(PendingCourseworkException.class);
    }

    @Test
    void aFailedCourseworkDoesNotCountAsApproved() {
        Fixture fixture = fixture(2, 1, null);
        load(fixture, GradeItemType.TP, 1, 2.0);

        assertThatThrownBy(() -> load(fixture, GradeItemType.EXAM, 1, 6.0)).isInstanceOf(PendingCourseworkException.class);
    }

    @Test
    void examsAreAcceptedOnceEveryExpectedCourseworkIsApproved() {
        Fixture fixture = fixture(2, 2, null);
        load(fixture, GradeItemType.TP, 1, 7.0);
        load(fixture, GradeItemType.TP, 2, 4.0);

        load(fixture, GradeItemType.EXAM, 1, 6.0);

        assertThat(item(fixture, GradeItemType.EXAM, 1).getScore()).isEqualTo(Grade.of(BigDecimal.valueOf(6.0)));
    }

    @Test
    void sectionsWithoutExpectedCourseworkAcceptExamsFreely() {
        Fixture fixture = fixture(2, 0, null);

        load(fixture, GradeItemType.EXAM, 1, 6.0);

        assertThat(item(fixture, GradeItemType.EXAM, 1).getScore()).isEqualTo(Grade.of(BigDecimal.valueOf(6.0)));
    }
}

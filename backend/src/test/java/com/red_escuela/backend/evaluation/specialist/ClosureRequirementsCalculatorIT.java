package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import com.red_escuela.backend.evaluation.domain.ClosureAlert;
import com.red_escuela.backend.evaluation.domain.ClosureSnapshotResult;
import com.red_escuela.backend.evaluation.domain.ClosureStudentAssessment;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.repository.CommissionClosureStudentRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClosureRequirementsCalculatorIT extends AbstractIntegrationTest {

    private static final int BIG_SECTION = 40;
    private static final int SMALL_SECTION = 4;
    private static final long QUERY_CEILING = 60;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ClosureRequirementsCalculator calculator;

    @Autowired
    private CommissionClosureSnapshotRecorder snapshotRecorder;

    @Autowired
    private CommissionClosureStudentRepository closureStudentRepository;

    private int sequence = 81000000;
    private Institution institution;
    private Program program;
    private Subject target;
    private Subject prerequisite;
    private Term term;

    private void persistPlan() {
        institution = new Institution();
        institution.setName("Closure institute");
        entityManager.persist(institution);
        program = new Program();
        program.setName("Closure program");
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        target = persistSubject("Target");
        prerequisite = persistSubject("Prerequisite");
        ProgramSubject targetEntry = persistProgramSubject(target);
        ProgramSubject prerequisiteEntry = persistProgramSubject(prerequisite);
        ProgramSubjectPrerequisite link = new ProgramSubjectPrerequisite();
        link.setProgramSubject(targetEntry);
        link.setPrerequisiteProgramSubject(prerequisiteEntry);
        link.setRequirementType(RequirementType.APPROVED);
        entityManager.persist(link);
        term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
    }

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    private ProgramSubject persistProgramSubject(Subject subject) {
        ProgramSubject entry = new ProgramSubject();
        entry.setProgram(program);
        entry.setSubject(subject);
        entry.setCredits(4);
        entry.setStatus(StatusType.ACTIVE);
        entityManager.persist(entry);
        return entry;
    }

    private SubjectSection persistSection(Subject subject, EvaluationScheme scheme) {
        SubjectSection section = new SubjectSection();
        section.setName("Section " + subject.getName());
        section.setSubject(subject);
        section.setTerm(term);
        section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
        section.setStatus(StatusType.ACTIVE);
        section.setEvaluationScheme(scheme);
        entityManager.persist(section);
        return section;
    }

    private EvaluationScheme persistScheme() {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Scheme " + TestSubjectCodes.next());
        scheme.setExamCountExpected(2);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(true);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6)));
        scheme.setPromotionMinGrade(Grade.of(BigDecimal.valueOf(7)));
        entityManager.persist(scheme);
        return scheme;
    }

    private SubjectSection persistCommission(int students) {
        if (institution == null) {
            persistPlan();
        }
        SubjectSection section = persistSection(target, persistScheme());
        SubjectSection prerequisiteSection = persistSection(prerequisite, persistScheme());
        for (int index = 0; index < students; index++) {
            Student student = persistStudent();
            ProgramEnrollment programEnrollment = new ProgramEnrollment();
            programEnrollment.setStudent(student);
            programEnrollment.setProgram(program);
            programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            programEnrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(programEnrollment);
            SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.IN_PROGRESS);
            persistExam(enrollment, 1, 8);
            persistExam(enrollment, 2, 9);
            if (index % 2 == 0) {
                SubjectSectionEnrollment approved = persistEnrollment(
                        student, programEnrollment, prerequisiteSection, EnrollmentStatus.APPROVED);
                approved.setFinalGradeType(FinalGradeType.FINAL_EXAM);
            }
            if (index % 4 == 1) {
                persistEnrollment(student, programEnrollment, prerequisiteSection, EnrollmentStatus.REGULAR);
            }
            if (index % 3 == 0) {
                for (DocumentType type : DocumentType.values()) {
                    persistDocument(student, type);
                }
            }
        }
        entityManager.persist(SectionGradeItemPublication.of(
                SectionKind.PROGRAM, section.getId(), GradeItemType.EXAM, 1, LocalDateTime.now(), null));
        entityManager.persist(SectionGradeItemPublication.of(
                SectionKind.PROGRAM, section.getId(), GradeItemType.EXAM, 2, LocalDateTime.now(), null));
        entityManager.flush();
        entityManager.clear();
        return section;
    }

    private SubjectSectionEnrollment persistEnrollment(
            Student student, ProgramEnrollment programEnrollment, SubjectSection section, EnrollmentStatus status) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }

    private void persistExam(SubjectSectionEnrollment enrollment, int sequenceNumber, int score) {
        GradeItem item = new GradeItem();
        item.setProgramSubjectSectionEnrollment(enrollment);
        item.setType(GradeItemType.EXAM);
        item.setSequenceNumber(sequenceNumber);
        item.setWeight(0.5);
        item.setDate(LocalDateTime.now());
        item.setScore(Grade.of(BigDecimal.valueOf(score)));
        entityManager.persist(item);
    }

    private void persistDocument(Student student, DocumentType type) {
        StudentDocument document = new StudentDocument();
        document.setStudent(student);
        document.setType(type);
        document.setFilePath("documents/" + student.getId() + "/" + type);
        document.setUploadedAt(LocalDateTime.now());
        entityManager.persist(document);
    }

    private Student persistStudent() {
        String dni = String.valueOf(sequence++);
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Name" + dni);
        person.setLastname("Last" + dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private long countStatements(Supplier<?> action) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        action.get();
        long count = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        return count;
    }

    @Test
    void statementCountDoesNotGrowWithTheNumberOfStudents() {
        SubjectSection small = persistCommission(SMALL_SECTION);
        SubjectSection big = persistCommission(BIG_SECTION);

        long smallCount = countStatements(() -> calculator.calculate(SectionKind.PROGRAM, small.getId()));
        entityManager.clear();
        long bigCount = countStatements(() -> calculator.calculate(SectionKind.PROGRAM, big.getId()));

        assertThat(bigCount).isEqualTo(smallCount).isLessThanOrEqualTo(QUERY_CEILING);
    }

    @Test
    void requirementsAreAssessedPerStudentInTheWholeCommission() {
        SubjectSection section = persistCommission(BIG_SECTION);

        List<ClosureStudentAssessment> assessments = calculator.calculate(SectionKind.PROGRAM, section.getId());

        assertThat(assessments).hasSize(BIG_SECTION);
        assertThat(assessments).allSatisfy(assessment -> assertThat(assessment.payments()).isEqualTo(PaymentsStanding.OK));
        List<ClosureStudentAssessment> prerequisiteMet = assessments.stream()
                .filter(assessment -> assessment.correlativesOk()).toList();
        List<ClosureStudentAssessment> prerequisiteUnmet = assessments.stream()
                .filter(assessment -> !assessment.correlativesOk()).toList();
        assertThat(prerequisiteMet).hasSize(BIG_SECTION / 2);
        assertThat(prerequisiteUnmet).hasSize(BIG_SECTION / 2);
        assertThat(prerequisiteUnmet).allSatisfy(assessment -> {
            assertThat(assessment.finalCondition()).contains(ClosureFinalCondition.GOES_TO_FINAL);
            assertThat(assessment.alerts()).containsExactly(ClosureAlert.UNMET_PREREQUISITE);
            assertThat(assessment.gradeOk()).isTrue();
            assertThat(assessment.promotionGrade()).isNull();
            assertThat(assessment.prerequisites().orElseThrow().details()).singleElement()
                    .satisfies(detail -> assertThat(detail.subjectCode()).isEqualTo(prerequisite.getCode()));
        });
        assertThat(prerequisiteUnmet.stream().map(assessment -> assessment.prerequisites().orElseThrow().details().get(0).standing()))
                .contains(PrerequisiteStanding.REGULAR, PrerequisiteStanding.NOT_TAKEN);
        assertThat(prerequisiteMet).allSatisfy(assessment -> {
            assertThat(assessment.alerts()).isEmpty();
            assertThat(assessment.promotionGrade()).isEqualTo(Grade.of(new BigDecimal("8.50")));
            assertThat(assessment.finalCondition()).contains(
                    assessment.documentsComplete() ? ClosureFinalCondition.PROMOTES : ClosureFinalCondition.PROMOTION_CONDITIONAL);
        });
        assertThat(prerequisiteMet.stream().filter(ClosureStudentAssessment::documentsComplete)).isNotEmpty();
        assertThat(prerequisiteMet.stream().filter(assessment -> !assessment.documentsComplete())).isNotEmpty();
    }

    @Test
    void snapshotPersistsTheFiveRequirementsAndKeepsTheTeacherObservationOnRecalculation() {
        SubjectSection section = persistCommission(SMALL_SECTION);
        CommissionClosure closure = CommissionClosure.builder()
                .sectionKind(SectionKind.PROGRAM).sectionId(section.getId()).status(ClosureStatus.DRAFT_AVAILABLE).build();
        entityManager.persist(closure);
        entityManager.flush();

        ClosureSnapshotResult first = snapshotRecorder.recordSnapshot(closure.getId());
        entityManager.flush();
        entityManager.clear();
        var rows = closureStudentRepository.findByClosureId(closure.getId());

        assertThat(first.pendingEnrollmentIds()).isEmpty();
        assertThat(rows).hasSize(SMALL_SECTION);
        Map<Long, ClosureStudentAssessment> assessmentByEnrollment = first.assessments().stream()
                .collect(Collectors.toMap(ClosureStudentAssessment::enrollmentId, a -> a));
        assertThat(rows).allSatisfy(row -> {
            ClosureStudentAssessment assessment = assessmentByEnrollment.get(row.getEnrollmentId());
            assertThat(row.getFinalCondition()).isEqualTo(assessment.finalCondition().orElseThrow());
            assertThat(row.getCorrelativesOk()).isEqualTo(assessment.correlativesOk());
            assertThat(row.getAdministrativeOk()).isTrue();
            assertThat(row.getDocumentsComplete()).isEqualTo(assessment.documentsComplete());
            assertThat(row.getAttendanceOk()).isTrue();
            assertThat(row.getGradeOk()).isTrue();
        });
        assertThat(rows.stream().filter(row -> row.getFinalCondition() == ClosureFinalCondition.GOES_TO_FINAL)).isNotEmpty();
        assertThat(rows.stream().filter(row -> row.getFinalCondition() == ClosureFinalCondition.PROMOTES
                || row.getFinalCondition() == ClosureFinalCondition.PROMOTION_CONDITIONAL)
                .allMatch(row -> row.getPromotionGrade() != null)).isTrue();

        snapshotRecorder.recordSnapshot(closure.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(closureStudentRepository.findByClosureId(closure.getId())).hasSize(SMALL_SECTION)
                .extracting(row -> row.getId()).containsExactlyInAnyOrderElementsOf(rows.stream().map(row -> row.getId()).toList());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ClosureStatus.class, names = {"TEACHER_CONFIRMED", "CLOSED"})
    void recalculationIsRejectedAndTheSignedSnapshotIsKeptWhenTheClosureIsNotDraftOrReopened(ClosureStatus status) {
        SubjectSection section = persistCommission(SMALL_SECTION);
        CommissionClosure closure = CommissionClosure.builder()
                .sectionKind(SectionKind.PROGRAM).sectionId(section.getId()).status(ClosureStatus.DRAFT_AVAILABLE).build();
        entityManager.persist(closure);
        entityManager.flush();
        snapshotRecorder.recordSnapshot(closure.getId());
        entityManager.flush();
        entityManager.createNativeQuery("""
                update commission_closure set status = :status, report_number = 'ACTA-2026-0001', book = 'L1', folio = '1',
                closed_by_id = (select min(id) from person), closed_at = now() where id = :id
                """.replace("report_number = 'ACTA-2026-0001', book = 'L1', folio = '1',\n                closed_by_id = (select min(id) from person), closed_at = now()",
                        status == ClosureStatus.CLOSED ? "report_number = 'ACTA-2026-0001', book = 'L1', folio = '1', closed_by_id = (select min(id) from person), closed_at = now()" : "lock_version = lock_version"))
                .setParameter("status", status.name()).setParameter("id", closure.getId()).executeUpdate();
        entityManager.clear();
        var before = closureStudentRepository.findByClosureId(closure.getId());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> snapshotRecorder.recordSnapshot(closure.getId()))
                .isInstanceOf(com.red_escuela.backend.evaluation.exception.ClosureSnapshotNotRecalculableException.class);

        entityManager.clear();
        assertThat(closureStudentRepository.findByClosureId(closure.getId())).hasSize(before.size());
    }

    @Test
    void aReopenedClosureCanBeRecalculated() {
        SubjectSection section = persistCommission(SMALL_SECTION);
        CommissionClosure closure = CommissionClosure.builder()
                .sectionKind(SectionKind.PROGRAM).sectionId(section.getId()).status(ClosureStatus.REOPENED).build();
        entityManager.persist(closure);
        entityManager.flush();

        assertThat(snapshotRecorder.recordSnapshot(closure.getId()).assessments()).hasSize(SMALL_SECTION);
    }
}

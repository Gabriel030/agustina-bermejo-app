package com.red_escuela.backend.evaluation.specialist;

import tools.jackson.databind.ObjectMapper;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.domain.ClosureBlockedReason;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;
import com.red_escuela.backend.evaluation.dto.ClosureSheetResponse;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.exception.ClosureAlreadyConfirmedException;
import com.red_escuela.backend.evaluation.exception.ClosureNotAvailableException;
import com.red_escuela.backend.evaluation.exception.PublishedGradesIncompleteException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommissionClosureSheetIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CommissionClosureSheetReader sheetReader;
    @Autowired
    private CommissionClosureTeacherConfirmer teacherConfirmer;
    @Autowired
    private ClosureTeacherObservationRecorder observationRecorder;
    @Autowired
    private CommissionClosureRepository closureRepository;
    @Autowired
    private CommissionClosureStudentRepository closureStudentRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private int sequence = 82000000;
    private Institution institution;
    private Long teacherPersonId;
    private Long teacherInstructorId;
    private Long strangerInstructorId;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private SubjectSection persistCommission() {
        institution = new Institution();
        institution.setName("Sheet institute");
        entityManager.persist(institution);
        Program program = new Program();
        program.setName("Sheet program");
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Sheet subject");
        entityManager.persist(subject);
        ProgramSubject entry = new ProgramSubject();
        entry.setProgram(program);
        entry.setSubject(subject);
        entry.setCredits(4);
        entry.setStatus(StatusType.ACTIVE);
        entityManager.persist(entry);
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
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
        SubjectSection section = new SubjectSection();
        section.setName("Sheet section");
        section.setSubject(subject);
        section.setTerm(term);
        section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
        section.setStatus(StatusType.ACTIVE);
        section.setEvaluationScheme(scheme);
        entityManager.persist(section);
        Person teacherPerson = persistPerson();
        teacherPersonId = teacherPerson.getId();
        Instructor teacher = persistInstructor(teacherPerson);
        teacherInstructorId = teacher.getId();
        strangerInstructorId = persistInstructor(persistPerson()).getId();
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(teacher);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);
        for (int index = 0; index < 3; index++) {
            Student student = persistStudent();
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
            persistExam(enrollment, 1, 8);
            persistExam(enrollment, 2, 9);
        }
        entityManager.flush();
        entityManager.clear();
        return section;
    }

    private void publishExam(Long sectionId, int sequenceNumber) {
        entityManager.persist(SectionGradeItemPublication.of(
                SectionKind.PROGRAM, sectionId, GradeItemType.EXAM, sequenceNumber, LocalDateTime.now(), null));
        entityManager.flush();
        entityManager.clear();
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

    private Person persistPerson() {
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
        return person;
    }

    private Instructor persistInstructor(Person person) {
        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        entityManager.persist(instructor);
        return instructor;
    }

    private Student persistStudent() {
        Student student = new Student();
        student.setPerson(persistPerson());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private void actAsTeacher() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(teacherPersonId), null, List.of()));
    }

    @Test
    void sheetIsNotAvailableUntilSomethingIsPublished() {
        SubjectSection section = persistCommission();

        assertThatThrownBy(() -> sheetReader.readSheet(ClosureSectionKind.PROGRAM, section.getId(), ClosureViewerProfile.STAFF, null))
                .isInstanceOf(ClosureNotAvailableException.class);
    }

    @Test
    void teacherSheetHidesMissingDocumentsWhileStaffSheetShowsThem() throws Exception {
        SubjectSection section = persistCommission();
        publishExam(section.getId(), 1);

        ClosureSheetResponse teacherSheet = sheetReader.readSheet(
                ClosureSectionKind.PROGRAM, section.getId(), ClosureViewerProfile.TEACHER, teacherInstructorId);
        ClosureSheetResponse staffSheet = sheetReader.readSheet(
                ClosureSectionKind.PROGRAM, section.getId(), ClosureViewerProfile.STAFF, null);

        String teacherJson = objectMapper.writeValueAsString(teacherSheet);
        assertThat(teacherJson).doesNotContain("missingDocuments").doesNotContainIgnoringCase("amount")
                .doesNotContainIgnoringCase("debt").doesNotContainIgnoringCase("balance");
        assertThat(teacherSheet.students()).hasSize(3)
                .allSatisfy(row -> assertThat(row.administrative().missingDocuments()).isNull());
        assertThat(staffSheet.students()).allSatisfy(row -> assertThat(row.administrative().missingDocuments()).isNotNull());
        assertThat(staffSheet.students().get(0).administrative().missingDocuments()).isNotEmpty();
        assertThat(objectMapper.writeValueAsString(staffSheet)).contains("missingDocuments");
        assertThat(teacherSheet.availability().canConfirm()).isFalse();
        assertThat(teacherSheet.availability().blockedReason()).isEqualTo(ClosureBlockedReason.PUBLISHED_GRADES_INCOMPLETE);
        assertThat(teacherSheet.status()).isEqualTo(ClosureStatus.DRAFT_AVAILABLE);
    }

    @Test
    void strangerTeacherCannotReadTheSheet() {
        SubjectSection section = persistCommission();
        publishExam(section.getId(), 1);

        assertThatThrownBy(() -> sheetReader.readSheet(
                ClosureSectionKind.PROGRAM, section.getId(), ClosureViewerProfile.TEACHER, strangerInstructorId))
                .isInstanceOf(NotOwnSectionException.class);
    }

    @Test
    void confirmIsRejectedWithUnpublishedExamsThenAcceptedOnceAndNotRepeated() {
        SubjectSection section = persistCommission();
        publishExam(section.getId(), 1);
        sheetReader.readSheet(ClosureSectionKind.PROGRAM, section.getId(), ClosureViewerProfile.TEACHER, teacherInstructorId);
        actAsTeacher();

        assertThatThrownBy(() -> teacherConfirmer.confirmClosure(ClosureSectionKind.PROGRAM, section.getId(), teacherInstructorId))
                .isInstanceOf(PublishedGradesIncompleteException.class);
        publishExam(section.getId(), 2);
        teacherConfirmer.confirmClosure(ClosureSectionKind.PROGRAM, section.getId(), teacherInstructorId);
        entityManager.flush();
        entityManager.clear();

        CommissionClosure closure = closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, section.getId())
                .orElseThrow();
        assertThat(closure.getStatus()).isEqualTo(ClosureStatus.TEACHER_CONFIRMED);
        assertThat(closure.getConfirmedById()).isEqualTo(teacherPersonId);
        assertThat(closure.getDeliveredAt()).isEqualTo(closure.getConfirmedAt());
        assertThatThrownBy(() -> teacherConfirmer.confirmClosure(ClosureSectionKind.PROGRAM, section.getId(), teacherInstructorId))
                .isInstanceOf(ClosureAlreadyConfirmedException.class);
    }

    @Test
    void teacherObservationSurvivesARecalculationAndIsRejectedAfterConfirmation() {
        SubjectSection section = persistCommission();
        publishExam(section.getId(), 1);
        publishExam(section.getId(), 2);
        ClosureSheetResponse sheet = sheetReader.readSheet(
                ClosureSectionKind.PROGRAM, section.getId(), ClosureViewerProfile.TEACHER, teacherInstructorId);
        Long enrollmentId = sheet.students().get(0).enrollmentId();

        observationRecorder.recordObservation(ClosureSectionKind.PROGRAM, section.getId(), enrollmentId, teacherInstructorId, "  Falta el TP  ");
        entityManager.flush();
        entityManager.clear();
        ClosureSheetResponse recalculated = sheetReader.readSheet(
                ClosureSectionKind.PROGRAM, section.getId(), ClosureViewerProfile.STAFF, null);

        assertThat(recalculated.students()).filteredOn(row -> row.enrollmentId().equals(enrollmentId))
                .singleElement().satisfies(row -> assertThat(row.teacherObservation()).isEqualTo("Falta el TP"));
        actAsTeacher();
        teacherConfirmer.confirmClosure(ClosureSectionKind.PROGRAM, section.getId(), teacherInstructorId);
        assertThatThrownBy(() -> observationRecorder.recordObservation(
                ClosureSectionKind.PROGRAM, section.getId(), enrollmentId, teacherInstructorId, "Otra"))
                .isInstanceOf(ClosureAlreadyConfirmedException.class);
    }
}

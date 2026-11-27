package com.red_escuela.backend.enrollment;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.api.ApprovalSource;
import com.red_escuela.backend.enrollment.api.ApprovedSubjectGrade;
import com.red_escuela.backend.enrollment.api.ApprovedSubjectGradesReader;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.shared.domain.Grade;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApprovedSubjectGradesReaderIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ApprovedSubjectGradesReader reader;

    private CycleTestFixtures fixtures;
    private Institution institution;
    private Program program;
    private ProgramEnrollment enrollment;
    private Term term;
    private AcademicOfferingCycle cycle;

    @BeforeEach
    void seed() {
        fixtures = new CycleTestFixtures(entityManager);
        institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        program = fixtures.program();
        term = fixtures.term(2026);
        cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        enrollment = programEnrollment();
    }

    @Test
    void returnsOnlyApprovedSubjectsWithGradeDateAndEquivalenceWithoutScore() {
        Subject approvedByCoursework = curriculumSubject(2);
        Subject inProgress = curriculumSubject(1);
        Subject recognized = curriculumSubject(1);
        Subject failed = curriculumSubject(1);
        sectionEnrollment(approvedByCoursework, EnrollmentStatus.APPROVED, "8.50", LocalDate.of(2026, 7, 10));
        sectionEnrollment(inProgress, EnrollmentStatus.IN_PROGRESS, null, null);
        sectionEnrollment(failed, EnrollmentStatus.FAILED, "2.00", LocalDate.of(2026, 7, 10));
        equivalence(recognized, LocalDate.of(2026, 3, 1));
        entityManager.flush();
        entityManager.clear();

        List<ApprovedSubjectGrade> result = reader.listApprovedSubjectGrades(
                enrollment.getStudent().getId(), enrollment.getId());

        assertThat(result).extracting(ApprovedSubjectGrade::subjectId)
                .containsExactly(recognized.getId(), approvedByCoursework.getId());
        ApprovedSubjectGrade equivalenceGrade = result.get(0);
        assertThat(equivalenceGrade.approvalSource()).isEqualTo(ApprovalSource.EQUIVALENCE);
        assertThat(equivalenceGrade.hasGrade()).isFalse();
        assertThat(equivalenceGrade.approvedOn()).isEqualTo(LocalDate.of(2026, 3, 1));
        ApprovedSubjectGrade courseworkGrade = result.get(1);
        assertThat(courseworkGrade.approvalSource()).isEqualTo(ApprovalSource.COURSEWORK);
        assertThat(courseworkGrade.grade().value()).isEqualByComparingTo("8.50");
        assertThat(courseworkGrade.approvedOn()).isEqualTo(LocalDate.of(2026, 7, 10));
    }

    @Test
    void anotherStudentsEnrollmentIsNotFound() {
        Long otherStudentId = programEnrollment().getStudent().getId();

        assertThatThrownBy(() -> reader.listApprovedSubjectGrades(otherStudentId, enrollment.getId()))
                .isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }

    private Subject curriculumSubject(int year) {
        Subject subject = fixtures.subject();
        ProgramSubject entry = new ProgramSubject();
        entry.setProgram(program);
        entry.setPlan(com.red_escuela.backend.testsupport.TestCurriculumPlans.planOf(program));
        entry.setSubject(subject);
        entry.setCredits(4);
        entry.setCurricularYear(year);
        entry.setStatus(StatusType.ACTIVE);
        entityManager.persist(entry);
        return subject;
    }

    private void sectionEnrollment(Subject subject, EnrollmentStatus status, String score, LocalDate scoreDate) {
        SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        SubjectSectionEnrollment created = new SubjectSectionEnrollment();
        created.setStudent(enrollment.getStudent());
        created.setProgramEnrollment(enrollment);
        created.setSubjectSection(section);
        created.setStatus(status);
        created.setEnrolledAt(LocalDateTime.now());
        created.setAttemptNumber(1);
        created.setFinalScore(score == null ? null : Grade.of(new BigDecimal(score)));
        created.setFinalScoreDate(scoreDate);
        entityManager.persist(created);
    }

    private void equivalence(Subject subject, LocalDate recognizedOn) {
        Person actor = fixtures.person();
        entityManager.persist(SubjectEquivalence.builder().programEnrollment(enrollment)
                .subjectId(subject.getId()).originInstitution("Universidad de origen")
                .originSubjectName("Materia de origen").originScore(null).recognizedOn(recognizedOn)
                .resolutionNumber("RES-" + UUID.randomUUID()).reason("Estudios previos")
                .status(SubjectEquivalenceStatus.ACTIVE).recordedById(actor.getId())
                .recordedAt(LocalDateTime.now()).build());
    }

    private ProgramEnrollment programEnrollment() {
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        ProgramEnrollment created = new ProgramEnrollment();
        created.setStudent(student);
        created.setProgram(program);
        created.setStatus(ProgramEnrollmentStatus.ENROLLED);
        created.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(created);
        entityManager.flush();
        return created;
    }
}

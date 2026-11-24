package com.red_escuela.backend.enrollment;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.api.ApprovalSource;
import com.red_escuela.backend.enrollment.api.RecognizedEquivalence;
import com.red_escuela.backend.enrollment.api.SubjectApprovalLookup;
import com.red_escuela.backend.enrollment.api.SubjectStanding;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Grade;
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
class SubjectApprovalLookupIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private SubjectApprovalLookup lookup;

    private CycleTestFixtures fixtures;
    private ProgramEnrollment enrollment;
    private ProgramEnrollment otherEnrollment;
    private Subject subject;
    private Subject otherSubject;
    private Person actor;

    @BeforeEach
    void seed() {
        fixtures = new CycleTestFixtures(entityManager);
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Program program = fixtures.program();
        enrollment = programEnrollment(institution, program);
        otherEnrollment = programEnrollment(institution, program);
        subject = fixtures.subject();
        otherSubject = fixtures.subject();
        actor = fixtures.person();
        entityManager.flush();
    }

    @Test
    void activeEquivalenceAppearsAsApprovedByEquivalenceInTheBatchLookup() {
        equivalence(enrollment, subject, "7.50", SubjectEquivalenceStatus.ACTIVE);
        equivalence(otherEnrollment, otherSubject, null, SubjectEquivalenceStatus.ACTIVE);

        Map<Long, Map<Long, SubjectStanding>> standings = lookup.findStandings(
                List.of(enrollment.getId(), otherEnrollment.getId()), List.of(subject.getId(), otherSubject.getId()),
                List.of(EnrollmentStatus.APPROVED, EnrollmentStatus.REGULAR));

        SubjectStanding first = standings.get(enrollment.getId()).get(subject.getId());
        assertThat(first.approvalSource()).isEqualTo(ApprovalSource.EQUIVALENCE);
        assertThat(first.approvedScore().value()).isEqualByComparingTo("7.50");
        assertThat(standings.get(enrollment.getId())).doesNotContainKey(otherSubject.getId());
        assertThat(standings.get(otherEnrollment.getId()).get(otherSubject.getId()).approvedScore()).isNull();
        assertThat(standings.get(otherEnrollment.getId()).get(otherSubject.getId()).isApproved()).isTrue();
    }

    @Test
    void voidedEquivalenceDoesNotCountAsApproval() {
        equivalence(enrollment, subject, "8.00", SubjectEquivalenceStatus.VOIDED);

        assertThat(lookup.findStandings(List.of(enrollment.getId()), List.of(subject.getId()),
                List.of(EnrollmentStatus.APPROVED)).get(enrollment.getId())).isEmpty();
        assertThat(lookup.findHistory(enrollment.getId())).isEmpty();
        assertThat(lookup.findActiveEquivalence(enrollment.getId(), subject.getId())).isEmpty();
    }

    @Test
    void historyAndActiveEquivalenceExposeTheRecognitionData() {
        equivalence(enrollment, subject, "9.00", SubjectEquivalenceStatus.ACTIVE);

        RecognizedEquivalence found = lookup.findActiveEquivalence(enrollment.getId(), subject.getId()).orElseThrow();

        assertThat(found.resolutionNumber()).isEqualTo("RES-1");
        assertThat(found.originInstitution()).isEqualTo("Universidad de origen");
        assertThat(found.originScore().value()).isEqualByComparingTo("9.00");
        assertThat(lookup.findHistory(enrollment.getId()).get(subject.getId()).isApprovedByEquivalence()).isTrue();
        assertThat(lookup.findHistory(otherEnrollment.getId())).isEmpty();
    }

    private void equivalence(ProgramEnrollment programEnrollment, Subject recognizedSubject, String score,
            SubjectEquivalenceStatus status) {
        boolean voided = status == SubjectEquivalenceStatus.VOIDED;
        entityManager.persist(SubjectEquivalence.builder().programEnrollment(programEnrollment)
                .subjectId(recognizedSubject.getId()).originInstitution("Universidad de origen")
                .originSubjectName("Materia de origen")
                .originScore(score == null ? null : Grade.of(new BigDecimal(score)))
                .recognizedOn(LocalDate.of(2026, 3, 1)).resolutionNumber("RES-1").reason("Estudios previos")
                .status(status).recordedById(actor.getId()).recordedAt(LocalDateTime.now())
                .voidedById(voided ? actor.getId() : null).voidedAt(voided ? LocalDateTime.now() : null)
                .voidReason(voided ? "Cargada por error" : null).build());
        entityManager.flush();
    }

    private ProgramEnrollment programEnrollment(Institution institution, Program program) {
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
        return created;
    }
}

package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.api.GraduationRequestTriggerPort;
import com.red_escuela.backend.graduation.exception.GraduationRequirementsNotMetException;
import com.red_escuela.backend.graduation.exception.NotEligibleForGraduationException;
import com.red_escuela.backend.graduation.usecase.request.ApproveGraduationRequestUseCase;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GraduationRequestLosesEligibilityIT extends AbstractIntegrationTest {

    @Autowired
    private GraduationRequestTriggerPort graduationRequestTrigger;

    @Autowired
    private GraduationRequestRegistrar graduationRequestRegistrar;

    @Autowired
    private ApproveGraduationRequestUseCase approveGraduationRequest;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Person studentPerson;
    private ProgramEnrollment programEnrollment;
    private Long subjectEnrollmentId;

    @BeforeEach
    void seedAStudentWhoApprovedEveryMandatorySubject() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 12);
            Institution institution = new Institution();
            institution.setName("Instituto " + suffix);
            entityManager.persist(institution);

            studentPerson = persistPerson("student" + suffix);
            Student student = new Student();
            student.setPerson(studentPerson);
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);

            Program program = new Program();
            program.setName("Carrera " + suffix);
            program.setTotalYears(3);
            program.setTotalCreditsRequired(10);
            entityManager.persist(program);

            programEnrollment = new ProgramEnrollment();
            programEnrollment.setStudent(student);
            programEnrollment.setProgram(program);
            programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            programEnrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(programEnrollment);

            Subject subject = new Subject();
            subject.setCode(TestSubjectCodes.next());
            subject.setName("Materia " + suffix);
            entityManager.persist(subject);
            ProgramSubject programSubject = new ProgramSubject();
            programSubject.setProgram(program);
            programSubject.setPlan(com.red_escuela.backend.testsupport.TestCurriculumPlans.planOf(program));
            programSubject.setSubject(subject);
            programSubject.setCredits(10);
            programSubject.setStatus(StatusType.ACTIVE);
            entityManager.persist(programSubject);
            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setDescription("2026-1C");
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            SubjectSection section = new SubjectSection();
            section.setName("Comision " + suffix);
            section.setSubject(subject);
            section.setTerm(term);
            section.setStatus(StatusType.ACTIVE);
            entityManager.persist(section);
            SubjectSectionEnrollment passed = new SubjectSectionEnrollment();
            passed.setStudent(student);
            passed.setProgramEnrollment(programEnrollment);
            passed.setSubjectSection(section);
            passed.setStatus(EnrollmentStatus.APPROVED);
            passed.setEnrolledAt(LocalDateTime.now());
            passed.setAttemptNumber(1);
            entityManager.persist(passed);
            subjectEnrollmentId = passed.getId();

            Person admin = persistPerson("admin" + suffix);
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(admin.getId().toString(), null));
        });
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    private Person persistPerson(String key) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(key);
        person.setDni(key);
        person.setUsername(key);
        person.setEmail(key + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private void setSubjectEnrollmentStatus(EnrollmentStatus status) {
        jdbcTemplate.update("update subject_section_enrollment set status = ? where id = ?", status.name(), subjectEnrollmentId);
    }

    private Long onlyRequestId() {
        return jdbcTemplate.queryForObject("select id from graduation_request where program_enrollment_id = ?", Long.class, programEnrollment.getId());
    }

    private Object outdatedAtOfRequest() {
        return jdbcTemplate.queryForObject("select outdated_at from graduation_request where program_enrollment_id = ?", Object.class, programEnrollment.getId());
    }

    private long requestCount() {
        return jdbcTemplate.queryForObject("select count(*) from graduation_request where program_enrollment_id = ?", Long.class, programEnrollment.getId());
    }

    @Test
    void requestIsNotCreatedWhileAMandatorySubjectIsNotApproved() {
        setSubjectEnrollmentStatus(EnrollmentStatus.REGULAR);

        graduationRequestTrigger.requestGraduationIfEligible(programEnrollment.getId());

        assertThat(requestCount()).isZero();
        assertThatThrownBy(() -> graduationRequestRegistrar.registerRequest(programEnrollment.getId()))
                .isInstanceOf(NotEligibleForGraduationException.class);
    }

    @Test
    void losingEligibilityAfterTheRequestFlagsItOutdatedAndBlocksItsApproval() {
        graduationRequestTrigger.requestGraduationIfEligible(programEnrollment.getId());
        assertThat(requestCount()).isEqualTo(1L);
        assertThat(outdatedAtOfRequest()).isNull();
        setSubjectEnrollmentStatus(EnrollmentStatus.REGULAR);

        graduationRequestTrigger.flagGraduationRequestOutdated(programEnrollment.getId());
        jdbcTemplate.update("update graduation_request set status = 'ADMIN_VERIFIED' where id = ?", onlyRequestId());

        assertThat(outdatedAtOfRequest()).isNotNull();
        assertThatThrownBy(() -> approveGraduationRequest.approve(onlyRequestId()))
                .isInstanceOf(GraduationRequirementsNotMetException.class);
        assertThat(jdbcTemplate.queryForObject("select status from graduation_request where id = ?", String.class, onlyRequestId()))
                .isEqualTo("ADMIN_VERIFIED");
        assertThat(jdbcTemplate.queryForObject("select status from program_enrollment where id = ?", String.class, programEnrollment.getId()))
                .isEqualTo(ProgramEnrollmentStatus.ENROLLED.name());
    }

    @Test
    void recoveringEligibilityClearsTheOutdatedFlagWithoutCreatingASecondRequest() {
        graduationRequestTrigger.requestGraduationIfEligible(programEnrollment.getId());
        setSubjectEnrollmentStatus(EnrollmentStatus.REGULAR);
        graduationRequestTrigger.flagGraduationRequestOutdated(programEnrollment.getId());
        setSubjectEnrollmentStatus(EnrollmentStatus.APPROVED);

        graduationRequestTrigger.requestGraduationIfEligible(programEnrollment.getId());

        assertThat(requestCount()).isEqualTo(1L);
        assertThat(outdatedAtOfRequest()).isNull();
    }
}

package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.events.EnrollmentFeeExemptionRevokedEvent;
import com.red_escuela.backend.enrollment.events.ProgramApplicationApprovedEvent;
import com.red_escuela.backend.enrollment.support.StudentDocumentFixtures;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ApproveProgramEnrollmentApplicationCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ApproveProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RequestProgramEnrollmentApplicationCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RequestProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.identity.repository.RoleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import com.red_escuela.backend.enrollment.usecase.enrollmentfee.RevokeEnrollmentFeeExemptionUseCase;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class EnrollmentTransactionBoundaryIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class FailingListenerConfiguration {
        @Bean
        FailingExemptionRevocationListener failingExemptionRevocationListener() {
            return new FailingExemptionRevocationListener();
        }
    }

    static class FailingExemptionRevocationListener {
        private volatile boolean armed;
        private volatile boolean armedOnApproval;

        void arm() {
            this.armed = true;
        }

        void armOnApproval() {
            this.armedOnApproval = true;
        }

        void disarm() {
            this.armed = false;
            this.armedOnApproval = false;
        }

        @EventListener
        void onApproved(ProgramApplicationApprovedEvent event) {
            if (armedOnApproval) {
                throw new IllegalStateException("listener failed on approval");
            }
        }

        @EventListener
        void onRevoked(EnrollmentFeeExemptionRevokedEvent event) {
            if (armed) {
                throw new IllegalStateException("listener failed on revocation");
            }
        }
    }

    @Autowired
    private RevokeEnrollmentFeeExemptionUseCase revokeExemption;

    @Autowired
    private FailingExemptionRevocationListener failingListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RequestProgramEnrollmentApplicationUseCase requestApplication;

    @Autowired
    private ApproveProgramEnrollmentApplicationUseCase approveApplication;

    @Autowired
    private RoleRepository roleRepository;

    private Long exemptionId;

    @BeforeEach
    void seedAnActiveExemption() {
        long suffix = System.nanoTime() % 100000000L;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + suffix);
            entityManager.persist(institution);

            Person staff = persistPerson("staff" + suffix);
            Student student = new Student();
            student.setPerson(persistPerson("student" + suffix));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);

            Program program = new Program();
            program.setName("Carrera " + suffix);
            program.setTotalYears(3);
            program.setTotalCreditsRequired(100);
            entityManager.persist(program);

            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);

            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(program);
            template.setTerm(term);
            template.setCreatedBy(staff);
            entityManager.persist(template);

            ProgramEnrollment programEnrollment = new ProgramEnrollment();
            programEnrollment.setStudent(student);
            programEnrollment.setProgram(program);
            programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            programEnrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(programEnrollment);

            EnrollmentFeeExemption exemption = new EnrollmentFeeExemption();
            exemption.setProgramEnrollment(programEnrollment);
            exemption.setTemplate(template);
            exemption.setSlot(1);
            exemption.setReason("Habilitacion por situacion socioeconomica");
            exemption.setGrantedBy(staff);
            exemption.setGrantedAt(LocalDateTime.now());
            entityManager.persist(exemption);
            exemptionId = exemption.getId();

            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken(staff.getId().toString(), null));
        });
    }

    @AfterEach
    void cleanUp() {
        failingListener.disarm();
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

    private Long revokedByOf(Long id) {
        return jdbcTemplate.queryForObject(
                "select revoked_by_id from enrollment_fee_exemption where id = ?", Long.class, id);
    }

    @Test
    void revocationFailingAfterTheExemptionIsFlushedLeavesTheExemptionActive() {
        failingListener.arm();

        assertThatThrownBy(() -> revokeExemption.revoke(exemptionId, "Ya no corresponde"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(revokedByOf(exemptionId)).isNull();
    }

    @Test
    void revocationWithoutFailuresMarksTheExemptionAsRevoked() {
        revokeExemption.revoke(exemptionId, "Ya no corresponde");

        assertThat(revokedByOf(exemptionId)).isNotNull();
    }

    private ProgramEnrollmentApplication seedPendingApplication() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + System.nanoTime());
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(persistPerson("9" + System.nanoTime()));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            StudentDocumentFixtures.persistAllRequiredDocuments(entityManager, student);
            Program program = new Program();
            program.setVisibleInCatalog(true);
            program.setName("Carrera " + System.nanoTime());
            program.setTotalYears(3);
            program.setTotalCreditsRequired(0);
            entityManager.persist(program);
            Person director = persistPerson("DIR" + System.nanoTime());
            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(program);
            template.setTerm(term);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeInvoicesCount(template, 10);
            template.setCreatedBy(director);
            entityManager.persist(template);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.publish(entityManager, template);
            ProgramCohort cohort = new ProgramCohort();
            cohort.setTemplate(template);
            cohort.setIntake(CohortIntake.FIRST);
            cohort.setAdmissionStart(LocalDate.now().minusDays(30));
            cohort.setAdmissionEnd(LocalDate.now().plusDays(5));
            cohort.setCursadaStart(LocalDate.now());
            cohort.setCreatedBy(director);
            entityManager.persist(cohort);
            com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(program.getId(), null, FeeType.ENROLLMENT_FEE, "Matricula de prueba",
                    ars(BigDecimal.valueOf(50000)), LocalDate.now().minusDays(1), director.getId());
            entityManager.flush();
            return requestApplication.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId()));
        });
    }

    private Person persistPreceptor() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Person preceptor = persistPerson("PREC" + System.nanoTime());
            preceptor.getRoles().add(roleRepository.findByName("PRECEPTOR").orElseThrow());
            return preceptor;
        });
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private String statusOf(Long applicationId) {
        return jdbcTemplate.queryForObject("select status from program_enrollment_application where id = ?", String.class, applicationId);
    }

    private String decisionKeyOf(Long applicationId) {
        return jdbcTemplate.queryForObject("select decision_idempotency_key from program_enrollment_application where id = ?", String.class, applicationId);
    }

    @Test
    void approvalFailingAfterTheAdmissionInvoiceIsIssuedLeavesNoInvoiceAndTheApplicationPending() {
        ProgramEnrollmentApplication application = seedPendingApplication();
        Person preceptor = persistPreceptor();
        failingListener.armOnApproval();
        long invoicesBefore = count("invoice");

        assertThatThrownBy(() -> approveApplication.approve(
                new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor).withIdempotencyKey("key-rollback")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("invoice")).isEqualTo(invoicesBefore);
        assertThat(statusOf(application.getId())).isEqualTo("PENDING");
        assertThat(decisionKeyOf(application.getId())).isNull();
    }

    @Test
    void approvalWithoutFailuresLeavesTheInvoiceTheApprovedApplicationAndTheKey() {
        ProgramEnrollmentApplication application = seedPendingApplication();
        Person preceptor = persistPreceptor();
        long invoicesBefore = count("invoice");

        approveApplication.approve(
                new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor).withIdempotencyKey("key-ok"));

        assertThat(count("invoice")).isGreaterThan(invoicesBefore);
        assertThat(statusOf(application.getId())).isEqualTo("APPROVED");
        assertThat(decisionKeyOf(application.getId())).isEqualTo("key-ok");
    }
}

package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.support.StudentDocumentFixtures;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RequestProgramEnrollmentApplicationCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RequestProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
abstract class AdmissionP1CharacterizationSupport extends AbstractIntegrationTest {

    protected static final BigDecimal ADMISSION_FEE = BigDecimal.valueOf(50000);

    @Autowired protected RequestProgramEnrollmentApplicationUseCase requestUseCase;
    @Autowired protected ProgramEnrollmentApplicationRepository applicationRepository;
    @Autowired protected ProgramEnrollmentRepository programEnrollmentRepository;
    @Autowired protected InvoiceRepository invoiceRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected TransactionTemplate transactionTemplate;
    @Autowired protected EntityManager entityManager;
    @Autowired protected JdbcTemplate jdbc;

    protected Person persistPerson(String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    protected Person persistStaff(String roleName) {
        return transactionTemplate.execute(status -> {
            Person staff = persistPerson("STF" + System.nanoTime());
            staff.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            return staff;
        });
    }

    protected Student persistConfirmedStudent() {
        return transactionTemplate.execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + System.nanoTime());
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(persistPerson("9" + System.nanoTime()));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            StudentDocumentFixtures.persistAllRequiredDocuments(entityManager, student);
            return student;
        });
    }

    protected record OpenCohort(Long programId, Long cohortId) {
    }

    protected OpenCohort persistOpenCohort(boolean withAdmissionPrice) {
        return transactionTemplate.execute(status -> {
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
            TestTemplateTerms.setRecurringFeeInvoicesCount(template, 10);
            template.setCreatedBy(director);
            entityManager.persist(template);
            TestTemplateTerms.publish(entityManager, template);
            ProgramCohort cohort = new ProgramCohort();
            cohort.setTemplate(template);
            cohort.setIntake(CohortIntake.FIRST);
            cohort.setAdmissionStart(LocalDate.now().minusDays(30));
            cohort.setAdmissionEnd(LocalDate.now().plusDays(30));
            cohort.setCursadaStart(LocalDate.now());
            cohort.setCreatedBy(director);
            entityManager.persist(cohort);
            if (withAdmissionPrice) {
                PriceFixtures.persistPriceVersion(program.getId(), null, FeeType.ENROLLMENT_FEE, "Matrícula de prueba",
                        ars(ADMISSION_FEE), LocalDate.now().minusDays(1), director.getId());
            }
            entityManager.flush();
            return new OpenCohort(program.getId(), cohort.getId());
        });
    }

    protected ProgramEnrollmentApplication requestApplication(Student student, OpenCohort cohort) {
        return transactionTemplate.execute(status ->
                requestUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, cohort.programId(), cohort.cohortId())));
    }

    protected List<ProgramEnrollment> activeEnrollmentsOf(ProgramEnrollmentApplication application) {
        return programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(application.getStudent().getId(),
                application.getProgram().getId(), List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED));
    }

    protected long incompletePublicationsOf(Long invoiceId) {
        return jdbc.queryForObject("""
                select count(*) from event_publication
                where event_type = ? and (serialized_event::jsonb ->> 'invoiceId')::bigint = ? and completion_date is null
                """, Long.class, AdmissionInvoicePaidEvent.class.getName(), invoiceId);
    }

    protected long publicationsOf(Long invoiceId) {
        return jdbc.queryForObject("""
                select count(*) from event_publication
                where event_type = ? and (serialized_event::jsonb ->> 'invoiceId')::bigint = ?
                """, Long.class, AdmissionInvoicePaidEvent.class.getName(), invoiceId);
    }

    protected long invoicesOf(Student student) {
        return jdbc.queryForObject("""
                select count(*) from invoice i join student_current_account a on a.id = i.student_current_account_id
                where a.student_id = ?
                """, Long.class, student.getId());
    }

    protected Course persistPricedCourse() {
        return transactionTemplate.execute(status -> {
            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setVisibleInCatalog(true);
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setCapacity(20);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Precio de prueba",
                    ars(BigDecimal.valueOf(15000)), LocalDate.now().minusDays(1), persistPerson("DIR" + System.nanoTime()).getId());
            return course;
        });
    }
}

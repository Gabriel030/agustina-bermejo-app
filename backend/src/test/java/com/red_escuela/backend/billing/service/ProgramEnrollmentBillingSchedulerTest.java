package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.billing.repository.PriceRevisionRepository;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.repository.TermRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresión (auditoría 2026-09-04): antes, ProgramEnrollmentBillingScheduler.generarMatricula
 * creaba la Invoice de matrícula vía InvoiceFactory.crearParaCarrera (REQUIRES_NEW) — esa
 * Invoice quedaba commiteada en su propia transacción, ya cerrada, ANTES de que el resto de
 * generarMatricula (o de su caller, PaymentPlanSelectionService.seleccionarParaCarrera) terminara.
 * Si algo fallaba después, la Invoice de matrícula quedaba huérfana (sin PaymentPlanSelection y,
 * para cohorte SECOND, sin el 50% de descuento). El fix usa crearParaCarreraAtomica (REQUIRED),
 * atada a la MISMA transacción que el resto de generarMatricula.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProgramEnrollmentBillingSchedulerTest extends AbstractIntegrationTest {

    @Autowired
    private ProgramEnrollmentBillingScheduler billingScheduler;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private ProgramYearBillingTemplateRepository templateRepository;

    @Autowired
    private ProgramCohortRepository programCohortRepository;

    @Autowired
    private PriceRevisionRepository priceRevisionRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    private Person persistPerson(String dni) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private ProgramEnrollment persistProgramEnrollmentConCohorteSegunda(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);

        Program program = new Program();
        program.setName("Carrera de prueba " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        program = programRepository.save(program);

        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(LocalDateTime.of(2026, 1, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(2027, 12, 31, 23, 59));
        term = termRepository.save(term);

        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        template.setRecurringFeeInvoicesCount(5);
        template.setCreatedBy(persistPerson(dni + "-creador"));
        template = templateRepository.save(template);

        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplate(template);
        cohort.setIntake(CohortIntake.SECOND);
        cohort.setAdmissionStart(LocalDate.now().minusMonths(1));
        cohort.setAdmissionEnd(LocalDate.now().plusMonths(1));
        cohort.setCursadaStart(LocalDate.now());
        cohort.setCreatedBy(template.getCreatedBy());
        cohort = programCohortRepository.save(cohort);

        PriceRevision priceRevision = new PriceRevision();
        priceRevision.setProgram(program);
        priceRevision.setFeeType(FeeType.ENROLLMENT_FEE);
        priceRevision.setDescription("Matrícula de prueba");
        priceRevision.setMonthlyAmount(java.math.BigDecimal.valueOf(10000));
        priceRevision.setEffectiveFrom(LocalDate.now().minusYears(1));
        priceRevision.setCreatedBy(template.getCreatedBy());
        priceRevisionRepository.save(priceRevision);

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setProgramCohort(cohort);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return programEnrollmentRepository.save(enrollment);
    }

    @Test
    void siAlgoFallaDespuesDeGenerarMatriculaNoQuedaUnaInvoiceHuerfana() {
        ProgramEnrollment enrollment = persistProgramEnrollmentConCohorteSegunda("99000301");

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                billingScheduler.generarMatricula(enrollment);
                // Simula una falla real DESPUÉS de crear la Invoice de matrícula, todavía dentro
                // de la misma unidad de trabajo que en producción envuelve a generarMatricula
                // (PaymentPlanSelectionService.seleccionarParaCarrera).
                throw new RuntimeException("Falla simulada después de generar la matrícula");
            });
        } catch (RuntimeException ignored) {
            // Esperado — lo que importa es el estado de la base después del rollback.
        }

        List<com.red_escuela.backend.billing.entity.Invoice> invoices =
                invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId());
        assertThat(invoices).isEmpty();
    }
}

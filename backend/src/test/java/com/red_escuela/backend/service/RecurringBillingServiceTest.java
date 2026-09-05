package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.FeeSchedule;
import com.red_escuela.backend.entity.FeeScheduleConcept;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramCohort;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.repository.InvoiceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Épica 8 Fase 2: motor de facturación recurrente — genera la Invoice de cuota
 * mensual sin intervención manual, es idempotente (correrlo dos veces no
 * duplica), y respeta el inicio real de cursada y el status de la matriculación.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecurringBillingServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RecurringBillingService recurringBillingService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private EntityManager entityManager;

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        return institution;
    }

    private Person persistPersonBase(String dni) {
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

    private Student persistStudent(String dni) {
        Student student = new Student();
        student.setPerson(persistPersonBase(dni));
        student.setInstitution(persistInstitution());
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Program persistPrograma(int totalYears, int installmentsPerAcademicYear) {
        Program program = new Program();
        program.setName("Diseño Gráfico " + System.nanoTime());
        program.setTotalYears(totalYears);
        program.setTotalCreditsRequired(0);
        program.setInstallmentsPerAcademicYear(installmentsPerAcademicYear);
        entityManager.persist(program);
        return program;
    }

    private ProgramCohort persistCohort(Program program, LocalDate cursadaStart) {
        ProgramCohort cohort = new ProgramCohort();
        cohort.setProgram(program);
        cohort.setOrdenEnAnio(1);
        cohort.setCursadaStart(cursadaStart);
        entityManager.persist(cohort);
        return cohort;
    }

    private ProgramEnrollment persistProgramEnrollment(Student student, Program program, ProgramCohort cohort, ProgramEnrollmentStatus status) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setProgramCohort(cohort);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private void persistFeeSchedule(Program program, Course course, BigDecimal amount, Person createdBy) {
        FeeSchedule feeSchedule = new FeeSchedule();
        feeSchedule.setProgram(program);
        feeSchedule.setCourse(course);
        feeSchedule.setConcept(FeeScheduleConcept.CUOTA_MENSUAL);
        feeSchedule.setAmount(amount);
        feeSchedule.setVigenteDesde(LocalDate.now().minusYears(1));
        feeSchedule.setCreatedBy(createdBy);
        feeSchedule.setCreatedAt(LocalDateTime.now());
        entityManager.persist(feeSchedule);
    }

    private Course persistCurso(LocalDateTime cursadaStart, int durationMonths) {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(cursadaStart);
        entityManager.persist(term);

        Course course = new Course();
        course.setName("Illustrator desde cero " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        course.setDurationMonths(durationMonths);
        entityManager.persist(course);
        return course;
    }

    private CourseEnrollment persistCourseEnrollment(Student student, Course course, EnrollmentStatus status) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    @Test
    void processProgramGeneratesTheInstallmentCorrectWithinOfThePlan() {
        Student student = persistStudent("83000001");
        Program program = persistPrograma(3, 10);
        // Cursada arrancó hace 3 meses -> hoy corresponde la cuota 4.
        ProgramCohort cohort = persistCohort(program, LocalDate.now().minusMonths(3).withDayOfMonth(1));
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort, ProgramEnrollmentStatus.ENROLLED);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistFeeSchedule(program, null, BigDecimal.valueOf(15000), director);
        entityManager.flush();

        boolean generada = recurringBillingService.procesarCarrera(enrollment, LocalDate.now());

        assertThat(generada).isTrue();
        List<Invoice> facturas = invoiceRepository.findByStudentId(student.getId());
        assertThat(facturas).hasSize(1);
        assertThat(facturas.get(0).getInstallmentNumber()).isEqualTo(4);
        assertThat(facturas.get(0).getTotalInstallments()).isEqualTo(30);
        assertThat(facturas.get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(15000));
    }

    @Test
    void processProgramNotGeneratesNothingBeforeOfStartingEnrollment() {
        Student student = persistStudent("83000002");
        Program program = persistPrograma(3, 10);
        ProgramCohort cohort = persistCohort(program, LocalDate.now().plusMonths(2));
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort, ProgramEnrollmentStatus.ENROLLED);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistFeeSchedule(program, null, BigDecimal.valueOf(15000), director);
        entityManager.flush();

        boolean generada = recurringBillingService.procesarCarrera(enrollment, LocalDate.now());

        assertThat(generada).isFalse();
        assertThat(invoiceRepository.findByStudentId(student.getId())).isEmpty();
    }

    @Test
    void processProgramIsIdempotentNotDuplicateIfIsRunTwice() {
        Student student = persistStudent("83000003");
        Program program = persistPrograma(3, 10);
        ProgramCohort cohort = persistCohort(program, LocalDate.now().withDayOfMonth(1));
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort, ProgramEnrollmentStatus.ENROLLED);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistFeeSchedule(program, null, BigDecimal.valueOf(15000), director);
        entityManager.flush();

        boolean primeraCorrida = recurringBillingService.procesarCarrera(enrollment, LocalDate.now());
        boolean segundaCorrida = recurringBillingService.procesarCarrera(enrollment, LocalDate.now());

        assertThat(primeraCorrida).isTrue();
        assertThat(segundaCorrida).isFalse();
        assertThat(invoiceRepository.findByStudentId(student.getId())).hasSize(1);
    }

    @Test
    void processProgramNotGeneratesMoreBeyondOfTheTotalOfInstallmentsOfThePlan() {
        Student student = persistStudent("83000004");
        Program program = persistPrograma(1, 2);
        // Plan de 1 año × 2 cuotas = 2 cuotas totales; cursada arrancó hace 6 meses, ya se agotó.
        ProgramCohort cohort = persistCohort(program, LocalDate.now().minusMonths(6).withDayOfMonth(1));
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort, ProgramEnrollmentStatus.ENROLLED);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistFeeSchedule(program, null, BigDecimal.valueOf(15000), director);
        entityManager.flush();

        boolean generada = recurringBillingService.procesarCarrera(enrollment, LocalDate.now());

        assertThat(generada).isFalse();
        assertThat(invoiceRepository.findByStudentId(student.getId())).isEmpty();
    }

    @Test
    void generateInstallmentsOfProgramsIgnoresEnrollmentsGivenOfWithdrawal() {
        Student student = persistStudent("83000005");
        Program program = persistPrograma(3, 10);
        ProgramCohort cohort = persistCohort(program, LocalDate.now().withDayOfMonth(1));
        persistProgramEnrollment(student, program, cohort, ProgramEnrollmentStatus.WITHDRAWN);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistFeeSchedule(program, null, BigDecimal.valueOf(15000), director);
        entityManager.flush();

        recurringBillingService.generarCuotasDeCarreras(LocalDate.now());

        assertThat(invoiceRepository.findByStudentId(student.getId())).isEmpty();
    }

    @Test
    void processCourseGeneratesTheInstallmentCorrectAccordingToTheStartRealOfEnrollment() {
        Student student = persistStudent("83000006");
        Course course = persistCurso(LocalDateTime.now().minusMonths(1).withDayOfMonth(1), 3);
        CourseEnrollment enrollment = persistCourseEnrollment(student, course, EnrollmentStatus.ENROLLED);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistFeeSchedule(null, course, BigDecimal.valueOf(8000), director);
        entityManager.flush();

        boolean generada = recurringBillingService.procesarCurso(enrollment, LocalDate.now());

        assertThat(generada).isTrue();
        List<Invoice> facturas = invoiceRepository.findByStudentId(student.getId());
        assertThat(facturas).hasSize(1);
        assertThat(facturas.get(0).getInstallmentNumber()).isEqualTo(2);
        assertThat(facturas.get(0).getTotalInstallments()).isEqualTo(3);
    }
}

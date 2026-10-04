package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.entity.TermEnrollmentOverride;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidException;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.identity.exception.StudentNotActiveException;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.enrollment.exception.TermEnrollmentOverrideClosedException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.red_escuela.backend.enrollment.service.SubjectSectionEnrollmentService;
import com.red_escuela.backend.enrollment.exception.EnrollmentWindowClosedException;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubjectSectionEnrollmentServiceTest extends AbstractIntegrationTest {
    @Autowired
    private SubjectSectionEnrollmentService subjectSectionEnrollmentService;

    @Autowired
    private EntityManager entityManager;

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        return institution;
    }

    private Student persistStudent(String dni, StatusType personStatus, Institution institution) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Alumno");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(personStatus);
        entityManager.persist(person);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        return program;
    }

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    private ProgramSubject persistProgramSubject(Program program, Subject subject) {
        ProgramSubject ps = new ProgramSubject();
        ps.setProgram(program);
        ps.setSubject(subject);
        ps.setCredits(10);
        ps.setCurricularYear(1);
        ps.setCurricularTerm(CurricularTerm.FIRST);
        ps.setStatus(StatusType.ACTIVE);
        entityManager.persist(ps);
        return ps;
    }

    private Term persistTerm(LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd) {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        term.setEnrollmentStart(enrollmentStart);
        term.setEnrollmentEnd(enrollmentEnd);
        entityManager.persist(term);
        return term;
    }

    private SubjectSection persistSubjectSection(Subject subject, Term term, Integer capacity) {
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setCapacity(capacity);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private ProgramEnrollment persistProgramEnrollment(Student student, Program program, ProgramEnrollmentStatus status) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    @Test
    void enrollingWithInvertedEnrollmentWindowThrowsConfigurationExceptionNotWindowClosed() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000001", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaVentanaInvertida");
        Program program = persistProgram("CarreraVentanaInvertida");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        Term term = persistTerm(now.plusDays(10), now.minusDays(10));
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(InvalidWindowConfigurationException.class);
    }

    @Test
    void enrollingInASectionWhoseSubjectIsNotInTheProgramPlanThrowsSpecificException() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000002", StatusType.ACTIVE, institution);
        Subject subjectFueraDePlan = persistSubject("MateriaFueraDePlan");
        Program program = persistProgram("CarreraSinEsaMateria");
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subjectFueraDePlan, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(SubjectNotInProgramPlanException.class);
    }

    @Test
    void reEnrollingWithoutChangeSectionToAnotherSectionOfTheSameTermDoesNotIncrementAttemptNumber() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000003", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaReinscripcionManual");
        Program program = persistProgram("CarreraReinscripcionManual");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection seccionA = persistSubjectSection(subject, term, null);
        SubjectSection seccionB = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        SubjectSectionEnrollment primera = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(seccionA.getId()));
        assertThat(primera.getAttemptNumber()).isEqualTo(1);

        subjectSectionEnrollmentService.withdraw(primera.getId());

        SubjectSectionEnrollment segunda = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(seccionB.getId()));
        assertThat(segunda.getAttemptNumber()).isEqualTo(1);
    }

    @Test
    void reEnrollingInAnotherTermAfterWithdrawalDoesIncrementAttemptNumber() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000004", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaOtroTerm");
        Program program = persistProgram("CarreraOtroTerm");
        persistProgramSubject(program, subject);
        Term term1 = persistTerm(null, null);
        Term term2 = persistTerm(null, null);
        SubjectSection seccionTerm1 = persistSubjectSection(subject, term1, null);
        SubjectSection seccionTerm2 = persistSubjectSection(subject, term2, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        SubjectSectionEnrollment primera = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(seccionTerm1.getId()));
        subjectSectionEnrollmentService.withdraw(primera.getId());

        SubjectSectionEnrollment segunda = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(seccionTerm2.getId()));
        assertThat(segunda.getAttemptNumber()).isEqualTo(2);
    }

    @Test
    void changeSectionWithInactiveStudentFails() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000005", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaAlumnoInactivo");
        Program program = persistProgram("CarreraAlumnoInactivo");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection seccionA = persistSubjectSection(subject, term, null);
        SubjectSection seccionB = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        SubjectSectionEnrollment original = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(seccionA.getId()));

        student.getPerson().setStatus(StatusType.DELETED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.changeSection(
                original.getId(), new ChangeSectionRequest(seccionB.getId())))
                .isInstanceOf(StudentNotActiveException.class);
    }

    @Test
    void manuallyClosingTheWindowRejectsEnrollmentEvenIfTheDateIsStillValid() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000006", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaOverrideCerrado");
        Program program = persistProgram("CarreraOverrideCerrado");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        Term term = persistTerm(now.minusDays(1), now.plusDays(10));
        term.setEnrollmentOverride(TermEnrollmentOverride.CLOSED);
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(TermEnrollmentOverrideClosedException.class);
    }

    @Test
    void manuallyOpeningTheWindowAllowsEnrollingEvenIfTheDateHasAlreadyExpired() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000007", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaOverrideAbierto");
        Program program = persistProgram("CarreraOverrideAbierto");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        Term term = persistTerm(now.minusDays(20), now.minusDays(10));
        term.setEnrollmentOverride(TermEnrollmentOverride.OPEN);
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        SubjectSectionEnrollment enrollmentResult = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));
        assertThat(enrollmentResult.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void withoutOverrideItStillGoesByDateAsBefore() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000008", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaSinOverride");
        Program program = persistProgram("CarreraSinOverride");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        Term term = persistTerm(now.minusDays(20), now.minusDays(10));
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(com.red_escuela.backend.enrollment.exception.EnrollmentWindowClosedException.class);
    }

    private ProgramCohort persistCohort(Program program, Term term) {
        Person director = new Person();
        Address address = new Address();
        entityManager.persist(address);
        director.setUsername("director.matricula." + System.nanoTime());
        director.setEmail(director.getUsername() + "@example.com");
        director.setAddress(address);
        director.setStatus(StatusType.ACTIVE);
        entityManager.persist(director);

        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        template.setRecurringFeeInvoicesCount(10);
        template.setCreatedBy(director);
        entityManager.persist(template);

        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplate(template);
        cohort.setIntake(CohortIntake.FIRST);
        cohort.setAdmissionStart(LocalDate.now().minusDays(60));
        cohort.setAdmissionEnd(LocalDate.now().minusDays(1));
        cohort.setCursadaStart(LocalDate.now());
        cohort.setCreatedBy(director);
        entityManager.persist(cohort);
        return cohort;
    }

    private ProgramEnrollment persistProgramEnrollmentWithCohort(Student student, Program program, ProgramCohort cohort) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setProgramCohort(cohort);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private void persistEnrollmentFeeInvoice(ProgramEnrollment enrollment, InvoiceStatus status) {
        persistEnrollmentFeeInvoice(enrollment, status, YearMonth.now());
    }

    private void persistEnrollmentFeeInvoice(ProgramEnrollment enrollment, InvoiceStatus status, YearMonth period) {
        Invoice invoice = new Invoice();
        invoice.setProgramEnrollment(enrollment);
        invoice.setFeeType(FeeType.ENROLLMENT_FEE);
        invoice.setPeriod(period);
        invoice.setDueDate(LocalDate.now());
        invoice.setBaseAmount(ars(50000));
        invoice.setStatus(status);
        entityManager.persist(invoice);
    }

    @Test
    void enrollingWithoutHavingPaidTheFeeOfAnEnrollmentWithCohortIsRejected() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000009", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaMatriculaImpaga");
        Program program = persistProgram("CarreraMatriculaImpaga");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        Term term = persistTerm(now.minusDays(10), now.plusDays(10));
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramCohort cohort = persistCohort(program, term);
        ProgramEnrollment enrollment = persistProgramEnrollmentWithCohort(student, program, cohort);
        persistEnrollmentFeeInvoice(enrollment, InvoiceStatus.PENDING);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(EnrollmentFeeNotPaidException.class);
    }

    @Test
    void enrollingWithTheFeePaidWorks() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000010", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaMatriculaPaga");
        Program program = persistProgram("CarreraMatriculaPaga");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        Term term = persistTerm(now.minusDays(10), now.plusDays(10));
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramCohort cohort = persistCohort(program, term);
        ProgramEnrollment enrollment = persistProgramEnrollmentWithCohort(student, program, cohort);
        persistEnrollmentFeeInvoice(enrollment, InvoiceStatus.PAID);
        entityManager.flush();

        SubjectSectionEnrollment resultado = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));

        assertThat(resultado.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void enrollingWithAnEntryFeeAndAReenrollmentFeeOfTheFollowingYearDoesNotFail() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000021", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaReinscripcion");
        Program program = persistProgram("CarreraReinscripcion");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        Term term = persistTerm(now.minusDays(10), now.plusDays(10));
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramCohort cohort = persistCohort(program, term);
        ProgramEnrollment enrollment = persistProgramEnrollmentWithCohort(student, program, cohort);
        persistEnrollmentFeeInvoice(enrollment, InvoiceStatus.PAID, YearMonth.now().minusYears(1));
        persistEnrollmentFeeInvoice(enrollment, InvoiceStatus.PAID, YearMonth.now());
        entityManager.flush();

        SubjectSectionEnrollment resultado = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));

        assertThat(resultado.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void enrollingWithAPaidEntryFeeButAnUnpaidReenrollmentFeeIsRejected() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000022", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaReinscripcionImpaga");
        Program program = persistProgram("CarreraReinscripcionImpaga");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        Term term = persistTerm(now.minusDays(10), now.plusDays(10));
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramCohort cohort = persistCohort(program, term);
        ProgramEnrollment enrollment = persistProgramEnrollmentWithCohort(student, program, cohort);
        persistEnrollmentFeeInvoice(enrollment, InvoiceStatus.PAID, YearMonth.now().minusYears(1));
        persistEnrollmentFeeInvoice(enrollment, InvoiceStatus.PENDING, YearMonth.now());
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(EnrollmentFeeNotPaidException.class);
    }

    @Test
    void enrollingWithoutCohortDoesNotRequireFee() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000011", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaSinCohorte");
        Program program = persistProgram("CarreraSinCohorte");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        Term term = persistTerm(now.minusDays(10), now.plusDays(10));
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        SubjectSectionEnrollment resultado = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));

        assertThat(resultado.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }
}

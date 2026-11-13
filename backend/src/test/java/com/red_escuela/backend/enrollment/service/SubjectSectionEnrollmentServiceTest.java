package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.testsupport.TermFixtures;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.exception.SectionHasActiveEnrollmentsException;
import com.red_escuela.backend.academicstructure.exception.SectionNotOpenForEnrollmentException;
import com.red_escuela.backend.academicstructure.exception.TermHasActiveEnrollmentsException;
import com.red_escuela.backend.academicstructure.service.SubjectSectionService;
import com.red_escuela.backend.academicstructure.service.TermDeactivation;
import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.enrollment.exception.PrerequisiteNotMetException;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.entity.TermEnrollmentOverride;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidException;
import com.red_escuela.backend.identity.exception.StudentNotActiveException;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.enrollment.exception.TermEnrollmentOverrideClosedException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.red_escuela.backend.enrollment.exception.EnrollmentWindowClosedException;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubjectSectionEnrollmentServiceTest extends AbstractIntegrationTest {
    @Autowired
    private SubjectSectionEnrollmentService subjectSectionEnrollmentService;

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    @Autowired
    private SectionWaitlistPromoter waitlistPromoter;

    @Autowired
    private SubjectSectionService subjectSectionService;

    @Autowired
    private TermDeactivation termDeactivation;

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
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        term.setEnrollmentStart(enrollmentStart != null ? enrollmentStart : TermFixtures.OPEN_WINDOW_START);
        term.setEnrollmentEnd(enrollmentEnd != null ? enrollmentEnd : TermFixtures.OPEN_WINDOW_END);
        term.setStartDate(term.getEnrollmentStart());
        term.setEndDate(term.getEnrollmentEnd());
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

    private void persistSeatHolder(String dni, Institution institution, Program program, SubjectSection section, EnrollmentStatus status) {
        Student holder = persistStudent(dni, StatusType.ACTIVE, institution);
        ProgramEnrollment holderEnrollment = persistProgramEnrollment(holder, program, ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment row = new SubjectSectionEnrollment();
        row.setStudent(holder);
        row.setProgramEnrollment(holderEnrollment);
        row.setSubjectSection(section);
        row.setStatus(status);
        row.setEnrolledAt(LocalDateTime.now());
        row.setAttemptNumber(1);
        entityManager.persist(row);
    }

    @Test
    void enrollingIntoASectionWhoseSeatsAreHeldByEnrolledInProgressAndRegularStudentsEndsWaitlisted() {
        Institution institution = persistInstitution();
        Subject subject = persistSubject("MateriaCupoLleno");
        Program program = persistProgram("CarreraCupoLleno");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, 3);
        persistSeatHolder("61000001", institution, program, section, EnrollmentStatus.ENROLLED);
        persistSeatHolder("61000002", institution, program, section, EnrollmentStatus.IN_PROGRESS);
        persistSeatHolder("61000003", institution, program, section, EnrollmentStatus.REGULAR);
        Student applicant = persistStudent("61000004", StatusType.ACTIVE, institution);
        ProgramEnrollment enrollment = persistProgramEnrollment(applicant, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        SubjectSectionEnrollment result = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));

        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void waitlistedWithdrawnAndFailedRowsDoNotConsumeSeats() {
        Institution institution = persistInstitution();
        Subject subject = persistSubject("MateriaCupoLibre");
        Program program = persistProgram("CarreraCupoLibre");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, 2);
        persistSeatHolder("62000001", institution, program, section, EnrollmentStatus.ENROLLED);
        persistSeatHolder("62000002", institution, program, section, EnrollmentStatus.WAITLISTED);
        persistSeatHolder("62000003", institution, program, section, EnrollmentStatus.WITHDRAWN);
        persistSeatHolder("62000004", institution, program, section, EnrollmentStatus.FAILED);
        Student applicant = persistStudent("62000005", StatusType.ACTIVE, institution);
        ProgramEnrollment enrollment = persistProgramEnrollment(applicant, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        SubjectSectionEnrollment result = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));

        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void withdrawingAnEnrolledStudentPromotesTheOldestWaitlistedOne() {
        Institution institution = persistInstitution();
        Subject subject = persistSubject("MateriaPromocion");
        Program program = persistProgram("CarreraPromocion");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, 1);
        Student first = persistStudent("63000001", StatusType.ACTIVE, institution);
        Student second = persistStudent("63000002", StatusType.ACTIVE, institution);
        ProgramEnrollment firstEnrollment = persistProgramEnrollment(first, program, ProgramEnrollmentStatus.ENROLLED);
        ProgramEnrollment secondEnrollment = persistProgramEnrollment(second, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
        SubjectSectionEnrollment seated = subjectSectionEnrollmentService.enroll(
                firstEnrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));
        SubjectSectionEnrollment waiting = subjectSectionEnrollmentService.enroll(
                secondEnrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));
        assertThat(seated.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);

        subjectSectionEnrollmentService.withdraw(seated.getId());

        assertThat(subjectSectionEnrollmentService.get(waiting.getId()).getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    private void persistUnmetPrerequisite(ProgramSubject target, ProgramSubject prerequisite) {
        ProgramSubjectPrerequisite link = new ProgramSubjectPrerequisite();
        link.setProgramSubject(target);
        link.setPrerequisiteProgramSubject(prerequisite);
        link.setRequirementType(RequirementType.APPROVED);
        entityManager.persist(link);
    }

    private SubjectSectionEnrollment persistWaitlisted(
            Student student, ProgramEnrollment programEnrollment, SubjectSection section, LocalDateTime enrolledAt) {
        SubjectSectionEnrollment row = new SubjectSectionEnrollment();
        row.setStudent(student);
        row.setProgramEnrollment(programEnrollment);
        row.setSubjectSection(section);
        row.setStatus(EnrollmentStatus.WAITLISTED);
        row.setEnrolledAt(enrolledAt);
        row.setAttemptNumber(1);
        entityManager.persist(row);
        return row;
    }

    @Test
    void changeSectionIsRejectedWhenAPrerequisiteIsNoLongerMetAndKeepsTheOriginEnrollment() {
        Institution institution = persistInstitution();
        Student student = persistStudent("64000001", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaConCorrelativaCambio");
        Subject prerequisiteSubject = persistSubject("CorrelativaCambio");
        Program program = persistProgram("CarreraCorrelativaCambio");
        ProgramSubject target = persistProgramSubject(program, subject);
        ProgramSubject prerequisite = persistProgramSubject(program, prerequisiteSubject);
        Term term = persistTerm(null, null);
        SubjectSection sectionA = persistSubjectSection(subject, term, null);
        SubjectSection sectionB = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
        SubjectSectionEnrollment original = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(sectionA.getId()));
        persistUnmetPrerequisite(target, prerequisite);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.changeSection(
                original.getId(), new ChangeSectionRequest(sectionB.getId())))
                .isInstanceOf(PrerequisiteNotMetException.class);
        assertThat(subjectSectionEnrollmentService.get(original.getId()).getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void waitlistPromotionSkipsACandidateWhoseCorrelativeIsUnmetAndPromotesTheNextOne() {
        Institution institution = persistInstitution();
        Subject subject = persistSubject("MateriaConCorrelativaLista");
        Subject prerequisiteSubject = persistSubject("CorrelativaLista");
        Program program = persistProgram("CarreraCorrelativaLista");
        ProgramSubject target = persistProgramSubject(program, subject);
        ProgramSubject prerequisite = persistProgramSubject(program, prerequisiteSubject);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, 1);
        Student seatedStudent = persistStudent("64000002", StatusType.ACTIVE, institution);
        Student blockedStudent = persistStudent("64000003", StatusType.ACTIVE, institution);
        Student eligibleStudent = persistStudent("64000004", StatusType.ACTIVE, institution);
        ProgramEnrollment seatedEnrollment = persistProgramEnrollment(seatedStudent, program, ProgramEnrollmentStatus.ENROLLED);
        ProgramEnrollment blockedEnrollment = persistProgramEnrollment(blockedStudent, program, ProgramEnrollmentStatus.ENROLLED);
        ProgramEnrollment eligibleEnrollment = persistProgramEnrollment(eligibleStudent, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
        SubjectSectionEnrollment seated = subjectSectionEnrollmentService.enroll(
                seatedEnrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));
        SubjectSectionEnrollment blocked = persistWaitlisted(
                blockedStudent, blockedEnrollment, section, LocalDateTime.of(2026, 1, 1, 10, 0));
        SubjectSectionEnrollment eligible = persistWaitlisted(
                eligibleStudent, eligibleEnrollment, section, LocalDateTime.of(2026, 1, 2, 10, 0));
        persistUnmetPrerequisite(target, prerequisite);
        SubjectSectionEnrollment passedPrerequisite = new SubjectSectionEnrollment();
        passedPrerequisite.setStudent(eligibleStudent);
        passedPrerequisite.setProgramEnrollment(eligibleEnrollment);
        passedPrerequisite.setSubjectSection(persistSubjectSection(prerequisiteSubject, term, null));
        passedPrerequisite.setStatus(EnrollmentStatus.APPROVED);
        passedPrerequisite.setEnrolledAt(LocalDateTime.of(2025, 1, 1, 10, 0));
        passedPrerequisite.setAttemptNumber(1);
        entityManager.persist(passedPrerequisite);
        entityManager.flush();

        subjectSectionEnrollmentService.withdraw(seated.getId());

        assertThat(subjectSectionEnrollmentService.get(blocked.getId()).getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(subjectSectionEnrollmentService.get(eligible.getId()).getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
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
        director.setDni(Long.toString(Math.abs((long) ("director.matricula." + System.nanoTime()).hashCode())));
        director.setName("Persona");
        director.setLastname("Prueba");
        director.setEmail(director.getUsername() + "@example.com");
        director.setAddress(address);
        director.setStatus(StatusType.ACTIVE);
        entityManager.persist(director);

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

    private void persistEnrollmentFeeInvoice(ProgramEnrollment enrollment, InvoicePaymentStatus status) {
        persistEnrollmentFeeInvoice(enrollment, status, YearMonth.now());
    }

    private void persistEnrollmentFeeInvoice(ProgramEnrollment enrollment, InvoicePaymentStatus status, YearMonth period) {
        BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.ENROLLMENT_FEE).forPeriod(period).dueOn(LocalDate.now()).withBaseAmount(ars(50000)).inState(status).build();
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
        persistEnrollmentFeeInvoice(enrollment, InvoicePaymentStatus.PENDING);
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
        persistEnrollmentFeeInvoice(enrollment, InvoicePaymentStatus.PAID);
        entityManager.flush();

        SubjectSectionEnrollment resultado = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));

        assertThat(resultado.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    @Tag("D-032")
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
        persistEnrollmentFeeInvoice(enrollment, InvoicePaymentStatus.PAID, YearMonth.now().minusYears(1));
        persistEnrollmentFeeInvoice(enrollment, InvoicePaymentStatus.PAID, YearMonth.now());
        entityManager.flush();

        SubjectSectionEnrollment resultado = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));

        assertThat(resultado.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    @Tag("D-032")
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
        persistEnrollmentFeeInvoice(enrollment, InvoicePaymentStatus.PAID, YearMonth.now().minusYears(1));
        persistEnrollmentFeeInvoice(enrollment, InvoicePaymentStatus.PENDING, YearMonth.now());
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(EnrollmentFeeNotPaidException.class);
    }

    @Test
    @Tag("D-007")
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

    @Test
    void withdrawingTheProgramEnrollmentWithdrawsOpenSectionEnrollmentsAndPromotesTheWaitlist() {
        Institution institution = persistInstitution();
        Subject subject = persistSubject("MateriaBajaCarrera");
        Subject otherSubject = persistSubject("MateriaBajaCarreraDos");
        Program program = persistProgram("CarreraBaja");
        persistProgramSubject(program, subject);
        persistProgramSubject(program, otherSubject);
        Term term = persistTerm(null, null);
        SubjectSection fullSection = persistSubjectSection(subject, term, 1);
        SubjectSection otherSection = persistSubjectSection(otherSubject, term, 5);
        Student leaving = persistStudent("64000001", StatusType.ACTIVE, institution);
        Student waiting = persistStudent("64000002", StatusType.ACTIVE, institution);
        ProgramEnrollment leavingEnrollment = persistProgramEnrollment(leaving, program, ProgramEnrollmentStatus.ENROLLED);
        ProgramEnrollment waitingEnrollment = persistProgramEnrollment(waiting, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
        SubjectSectionEnrollment seated = subjectSectionEnrollmentService.enroll(
                leavingEnrollment.getId(), new CreateSectionEnrollmentRequest(fullSection.getId()));
        SubjectSectionEnrollment other = subjectSectionEnrollmentService.enroll(
                leavingEnrollment.getId(), new CreateSectionEnrollmentRequest(otherSection.getId()));
        SubjectSectionEnrollment queued = subjectSectionEnrollmentService.enroll(
                waitingEnrollment.getId(), new CreateSectionEnrollmentRequest(fullSection.getId()));
        assertThat(queued.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);

        programEnrollmentService.withdrawForNonpayment(leavingEnrollment.getId());

        assertThat(subjectSectionEnrollmentService.get(seated.getId()).getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(subjectSectionEnrollmentService.get(seated.getId()).getDroppedAt()).isNotNull();
        assertThat(subjectSectionEnrollmentService.get(other.getId()).getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(subjectSectionEnrollmentService.get(queued.getId()).getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void withdrawingTheProgramEnrollmentKeepsRegularApprovedAndFailedSectionEnrollmentsAsHistory() {
        Institution institution = persistInstitution();
        Program program = persistProgram("CarreraHistoria");
        Term term = persistTerm(null, null);
        Student student = persistStudent("65000001", StatusType.ACTIVE, institution);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        List<SubjectSectionEnrollment> rows = List.of(
                persistSectionEnrollment(enrollment, student, program, term, "HistoriaRegular", EnrollmentStatus.REGULAR),
                persistSectionEnrollment(enrollment, student, program, term, "HistoriaAprobada", EnrollmentStatus.APPROVED),
                persistSectionEnrollment(enrollment, student, program, term, "HistoriaDesaprobada", EnrollmentStatus.FAILED));
        entityManager.flush();

        programEnrollmentService.withdrawForNonpayment(enrollment.getId());

        assertThat(rows).extracting(row -> subjectSectionEnrollmentService.get(row.getId()).getStatus())
                .containsExactly(EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVED, EnrollmentStatus.FAILED);
    }

    private SubjectSectionEnrollment persistSectionEnrollment(ProgramEnrollment enrollment, Student student, Program program,
                                                              Term term, String subjectName, EnrollmentStatus status) {
        Subject subject = persistSubject(subjectName);
        persistProgramSubject(program, subject);
        SubjectSection section = persistSubjectSection(subject, term, 5);
        SubjectSectionEnrollment row = new SubjectSectionEnrollment();
        row.setStudent(student);
        row.setProgramEnrollment(enrollment);
        row.setSubjectSection(section);
        row.setStatus(status);
        row.setEnrolledAt(LocalDateTime.now());
        row.setAttemptNumber(1);
        entityManager.persist(row);
        return row;
    }

    private SubjectSection persistInactiveSection(Subject subject, Term term) {
        SubjectSection section = persistSubjectSection(subject, term, null);
        section.setStatus(StatusType.INACTIVE);
        entityManager.flush();
        return section;
    }

    @Test
    void enrollingInAnInactiveSectionIsRejectedWithoutCreatingARow() {
        Institution institution = persistInstitution();
        Student student = persistStudent("65000001", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaComisionInactiva");
        Program program = persistProgram("CarreraComisionInactiva");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection section = persistInactiveSection(subject, term);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(SectionNotOpenForEnrollmentException.class);
        assertThat(entityManager.createQuery("select count(e) from SubjectSectionEnrollment e where e.subjectSection.id = :id", Long.class)
                .setParameter("id", section.getId()).getSingleResult()).isZero();
    }

    @Test
    void enrollingInAnActiveSectionOfAnUnapprovedCycleIsRejected() {
        Institution institution = persistInstitution();
        Student student = persistStudent("65000002", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaCicloBorrador");
        Program program = persistProgram("CarreraCicloBorrador");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        var fixtures = new CycleTestFixtures(entityManager);
        var cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.DRAFT);
        SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(SectionNotOpenForEnrollmentException.class);
    }

    @Test
    void enrollingInAnActiveSectionOfAnApprovedCycleSucceeds() {
        Institution institution = persistInstitution();
        Student student = persistStudent("65000003", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaCicloAprobado");
        Program program = persistProgram("CarreraCicloAprobado");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        var fixtures = new CycleTestFixtures(entityManager);
        var cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        SubjectSectionEnrollment result = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));

        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void changingToAnInactiveSectionIsRejectedAndKeepsTheOriginEnrollment() {
        Institution institution = persistInstitution();
        Student student = persistStudent("65000004", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaCambioInactiva");
        Program program = persistProgram("CarreraCambioInactiva");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection origin = persistSubjectSection(subject, term, null);
        SubjectSection target = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
        SubjectSectionEnrollment original = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(origin.getId()));
        target.setStatus(StatusType.INACTIVE);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.changeSection(
                original.getId(), new ChangeSectionRequest(target.getId())))
                .isInstanceOf(SectionNotOpenForEnrollmentException.class);
        assertThat(subjectSectionEnrollmentService.get(original.getId()).getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void waitlistPromotionDoesNotPromoteIntoAnInactiveSection() {
        Institution institution = persistInstitution();
        Subject subject = persistSubject("MateriaPromocionInactiva");
        Program program = persistProgram("CarreraPromocionInactiva");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, 1);
        persistSeatHolder("65000005", institution, program, section, EnrollmentStatus.ENROLLED);
        persistSeatHolder("65000006", institution, program, section, EnrollmentStatus.WAITLISTED);
        section.setStatus(StatusType.INACTIVE);
        entityManager.flush();
        SubjectSectionEnrollment seated = entityManager.createQuery(
                "select e from SubjectSectionEnrollment e where e.subjectSection.id = :id and e.status = :status", SubjectSectionEnrollment.class)
                .setParameter("id", section.getId()).setParameter("status", EnrollmentStatus.ENROLLED).getSingleResult();
        seated.setStatus(EnrollmentStatus.WITHDRAWN);
        entityManager.flush();

        waitlistPromoter.promoteNextFromWaitlist(section.getId());

        assertThat(entityManager.createQuery("select count(e) from SubjectSectionEnrollment e where e.subjectSection.id = :id and e.status = :status", Long.class)
                .setParameter("id", section.getId()).setParameter("status", EnrollmentStatus.ENROLLED).getSingleResult()).isZero();
    }

    @Test
    void deactivatingASectionWithLiveEnrollmentsIsRejectedForEachLiveStatusAndKeepsItActive() {
        Institution institution = persistInstitution();
        Subject subject = persistSubject("MateriaBajaConInscriptos");
        Program program = persistProgram("CarreraBajaConInscriptos");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        int dni = 66000000;
        for (EnrollmentStatus live : List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS,
                EnrollmentStatus.REGULAR, EnrollmentStatus.WAITLISTED)) {
            SubjectSection section = persistSubjectSection(subject, term, null);
            persistSeatHolder(String.valueOf(dni++), institution, program, section, live);
            entityManager.flush();

            assertThatThrownBy(() -> subjectSectionService.deactivate(section.getId()))
                    .isInstanceOf(SectionHasActiveEnrollmentsException.class);
            assertThat(section.getStatus()).isEqualTo(StatusType.ACTIVE);
        }
    }

    @Test
    void deactivatingASectionWhoseEnrollmentsAreAllClosedSucceeds() {
        Institution institution = persistInstitution();
        Subject subject = persistSubject("MateriaBajaSinInscriptos");
        Program program = persistProgram("CarreraBajaSinInscriptos");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, null);
        persistSeatHolder("66100001", institution, program, section, EnrollmentStatus.WITHDRAWN);
        persistSeatHolder("66100002", institution, program, section, EnrollmentStatus.FAILED);
        persistSeatHolder("66100003", institution, program, section, EnrollmentStatus.APPROVED);
        entityManager.flush();

        subjectSectionService.deactivate(section.getId());

        assertThat(section.getStatus()).isEqualTo(StatusType.INACTIVE);
    }

    @Test
    void deactivatingATermWithALiveEnrollmentInAnyOfItsSectionsIsRejected() {
        Institution institution = persistInstitution();
        Subject subject = persistSubject("MateriaBajaTerminoConInscriptos");
        Program program = persistProgram("CarreraBajaTerminoConInscriptos");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, null);
        persistSeatHolder("66200001", institution, program, section, EnrollmentStatus.WAITLISTED);
        entityManager.flush();

        assertThatThrownBy(() -> termDeactivation.deactivate(term.getId()))
                .isInstanceOf(TermHasActiveEnrollmentsException.class);
        assertThat(term.getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void deactivatingATermWithoutLiveEnrollmentsSucceeds() {
        Institution institution = persistInstitution();
        Subject subject = persistSubject("MateriaBajaTerminoSinInscriptos");
        Program program = persistProgram("CarreraBajaTerminoSinInscriptos");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, null);
        persistSeatHolder("66300001", institution, program, section, EnrollmentStatus.WITHDRAWN);
        entityManager.flush();

        termDeactivation.deactivate(term.getId());

        assertThat(term.getStatus()).isEqualTo(StatusType.INACTIVE);
    }
}

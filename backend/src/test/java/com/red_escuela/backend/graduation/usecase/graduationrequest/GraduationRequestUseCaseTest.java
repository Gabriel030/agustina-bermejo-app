package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Grade;

import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.graduation.exception.GraduationRequestAlreadyActiveException;
import com.red_escuela.backend.graduation.exception.GraduationRequirementsNotMetException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentRequiredException;
import com.red_escuela.backend.graduation.exception.NotEligibleForGraduationException;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GraduationRequestUseCaseTest extends AbstractIntegrationTest {

    @Autowired
    private RequestGraduationUseCase requestGraduationUseCase;

    @Autowired
    private VerifyGraduationRequestDebtUseCase verifyGraduationRequestDebtUseCase;

    @Autowired
    private ApproveGraduationRequestUseCase approveGraduationRequestUseCase;

    @Autowired
    private RejectGraduationRequestUseCase rejectGraduationRequestUseCase;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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

    private ProgramEnrollment persistEnrollmentWithCompletedPlan(Student student, String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(1);
        program.setTotalCreditsRequired(0);
        entityManager.persist(program);

        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia aprobada " + name);
        entityManager.persist(subject);
        ProgramSubject programSubject = new ProgramSubject();
        programSubject.setProgram(program);
        programSubject.setSubject(subject);
        programSubject.setCredits(0);
        programSubject.setCurricularYear(1);
        programSubject.setCurricularTerm(CurricularTerm.FIRST);
        programSubject.setStatus(StatusType.ACTIVE);
        entityManager.persist(programSubject);

        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        SubjectSection section = new SubjectSection();
        section.setName("Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        SubjectSectionEnrollment approved = new SubjectSectionEnrollment();
        approved.setStudent(student);
        approved.setProgramEnrollment(enrollment);
        approved.setSubjectSection(section);
        approved.setStatus(EnrollmentStatus.APPROVED);
        approved.setFinalScore(Grade.of(BigDecimal.valueOf(8.0)));
        approved.setEnrolledAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        approved.setAttemptNumber(1);
        entityManager.persist(approved);
        return enrollment;
    }

    private Program persistProgramWithPendingSubject(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(1);
        program.setTotalCreditsRequired(10);
        entityManager.persist(program);
        persistMandatorySubject(program, name);
        return program;
    }

    private void persistMandatorySubject(Program program, String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia pendiente " + name);
        entityManager.persist(subject);

        ProgramSubject ps = new ProgramSubject();
        ps.setProgram(program);
        ps.setSubject(subject);
        ps.setCredits(10);
        ps.setCurricularYear(1);
        ps.setCurricularTerm(CurricularTerm.FIRST);
        ps.setStatus(StatusType.ACTIVE);
        entityManager.persist(ps);
    }

    private GraduationRequest requestAndVerify(ProgramEnrollment enrollment, String adminDni) {
        GraduationRequest request = requestGraduationUseCase.request(enrollment.getId());
        authenticateAsPerson(adminDni);
        verifyGraduationRequestDebtUseCase.verify(request.getId());
        return request;
    }

    private ProgramEnrollment persistProgramEnrollment(Student student, Program program) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private void persistInvoice(ProgramEnrollment enrollment, LocalDate dueDate) {
        Invoice invoice = BillingTestFixtures.invoice().build();
        invoice.setProgramEnrollment(enrollment);
        invoice.setFeeType(FeeType.MONTHLY_INSTALLMENT);
        invoice.setPeriod(java.time.YearMonth.from(dueDate));
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(ars(100));
        BillingTestFixtures.markInvoiceAsPending(invoice);
        entityManager.persist(invoice);
    }

    private void authenticateAsPerson(String dni) {
        Person persona = persistPersonBase(dni);
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(persona.getId().toString(), null));
    }

    @Test
    void requestWithoutBeingEligibleFails() {
        Student student = persistStudent("80000001");
        Program program = persistProgramWithPendingSubject("CarreraConPendientes");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        entityManager.flush();

        assertThatThrownBy(() -> requestGraduationUseCase.request(enrollment.getId()))
                .isInstanceOf(NotEligibleForGraduationException.class);
    }

    @Test
    void requestForAProgramWithAnEmptyStudyPlanFails() {
        Student student = persistStudent("80000011");
        Program program = new Program();
        program.setName("CarreraSinPlanCargado");
        program.setTotalYears(1);
        program.setTotalCreditsRequired(0);
        entityManager.persist(program);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        entityManager.flush();

        assertThatThrownBy(() -> requestGraduationUseCase.request(enrollment.getId()))
                .isInstanceOf(NotEligibleForGraduationException.class);
    }

    @Test
    void requestTwiceWithoutTheFirstOneBeingRejectedFails() {
        Student student = persistStudent("80000002");
        ProgramEnrollment enrollment = persistEnrollmentWithCompletedPlan(student, "CarreraSinRequisitos1");
        entityManager.flush();

        requestGraduationUseCase.request(enrollment.getId());

        assertThatThrownBy(() -> requestGraduationUseCase.request(enrollment.getId()))
                .isInstanceOf(GraduationRequestAlreadyActiveException.class);
    }

    @Test
    void verifyDebtWithAnOverdueInstallmentFailsAndTheRequestStaysRequested() {
        Student student = persistStudent("80000003");
        ProgramEnrollment enrollment = persistEnrollmentWithCompletedPlan(student, "CarreraSinRequisitos2");
        entityManager.flush();

        GraduationRequest request = requestGraduationUseCase.request(enrollment.getId());
        persistInvoice(enrollment, LocalDate.of(2026, 1, 10));
        entityManager.flush();

        assertThatThrownBy(() -> verifyGraduationRequestDebtUseCase.verify(request.getId()))
                .isInstanceOf(PendingDebtException.class);

        GraduationRequest reloaded = entityManager.find(GraduationRequest.class, request.getId());
        assertThat(reloaded.getStatus()).isEqualTo(GraduationRequestStatus.REQUESTED);
    }

    @Test
    void verifyDebtWithANotYetDueUnpaidInstallmentFails() {
        Student student = persistStudent("80000006");
        ProgramEnrollment enrollment = persistEnrollmentWithCompletedPlan(student, "CarreraSinRequisitos5");
        entityManager.flush();

        GraduationRequest request = requestGraduationUseCase.request(enrollment.getId());
        persistInvoice(enrollment, LocalDate.now().plusDays(7));
        authenticateAsPerson("ADMIN80000006");

        assertThatThrownBy(() -> verifyGraduationRequestDebtUseCase.verify(request.getId()))
                .isInstanceOf(PendingDebtException.class);
        assertThat(entityManager.find(GraduationRequest.class, request.getId()).getStatus())
                .isEqualTo(GraduationRequestStatus.REQUESTED);
    }

    @Test
    void approveFailsWhenTheStudentHasANotYetDueUnpaidInstallment() {
        Student student = persistStudent("80000010");
        ProgramEnrollment enrollment = persistEnrollmentWithCompletedPlan(student, "CarreraSinRequisitos9");
        entityManager.flush();
        GraduationRequest request = requestAndVerify(enrollment, "ADMIN80000010");

        persistInvoice(enrollment, LocalDate.now().plusDays(7));
        entityManager.flush();
        authenticateAsPerson("DIRECTOR80000010");

        assertThatThrownBy(() -> approveGraduationRequestUseCase.approve(request.getId()))
                .isInstanceOf(PendingDebtException.class);
        assertThat(entityManager.find(ProgramEnrollment.class, enrollment.getId()).getStatus())
                .isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void approveFailsWhenTheStudentNoLongerMeetsTheGraduationRequirements() {
        Student student = persistStudent("80000007");
        ProgramEnrollment enrollment = persistEnrollmentWithCompletedPlan(student, "CarreraSinRequisitos6");
        entityManager.flush();
        GraduationRequest request = requestAndVerify(enrollment, "ADMIN80000007");

        persistMandatorySubject(enrollment.getProgram(), "CarreraSinRequisitos6");
        entityManager.flush();
        authenticateAsPerson("DIRECTOR80000007");

        assertThatThrownBy(() -> approveGraduationRequestUseCase.approve(request.getId()))
                .isInstanceOf(GraduationRequirementsNotMetException.class);
        assertThat(entityManager.find(GraduationRequest.class, request.getId()).getStatus())
                .isEqualTo(GraduationRequestStatus.ADMIN_VERIFIED);
        assertThat(entityManager.find(ProgramEnrollment.class, enrollment.getId()).getStatus())
                .isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void approveFailsWhenTheProgramEnrollmentWasWithdrawn() {
        Student student = persistStudent("80000009");
        ProgramEnrollment enrollment = persistEnrollmentWithCompletedPlan(student, "CarreraSinRequisitos8");
        entityManager.flush();
        GraduationRequest request = requestAndVerify(enrollment, "ADMIN80000009");

        enrollment.setStatus(ProgramEnrollmentStatus.WITHDRAWN);
        entityManager.flush();
        authenticateAsPerson("DIRECTOR80000009");

        assertThatThrownBy(() -> approveGraduationRequestUseCase.approve(request.getId()))
                .isInstanceOf(ProgramEnrollmentRequiredException.class);
        assertThat(entityManager.find(ProgramEnrollment.class, enrollment.getId()).getStatus())
                .isEqualTo(ProgramEnrollmentStatus.WITHDRAWN);
        assertThat(entityManager.find(GraduationRequest.class, request.getId()).getStatus())
                .isEqualTo(GraduationRequestStatus.ADMIN_VERIFIED);
    }

    @Test
    void approveFailsWhenTheStudentHasOverdueDebt() {
        Student student = persistStudent("80000008");
        ProgramEnrollment enrollment = persistEnrollmentWithCompletedPlan(student, "CarreraSinRequisitos7");
        entityManager.flush();
        GraduationRequest request = requestAndVerify(enrollment, "ADMIN80000008");

        persistInvoice(enrollment, LocalDate.of(2026, 1, 10));
        entityManager.flush();
        authenticateAsPerson("DIRECTOR80000008");

        assertThatThrownBy(() -> approveGraduationRequestUseCase.approve(request.getId()))
                .isInstanceOf(PendingDebtException.class);
        assertThat(entityManager.find(ProgramEnrollment.class, enrollment.getId()).getStatus())
                .isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void approveGraduationCompletesTheProgramEnrollmentInTheSameTransaction() {
        Student student = persistStudent("80000004");
        ProgramEnrollment enrollment = persistEnrollmentWithCompletedPlan(student, "CarreraSinRequisitos3");
        entityManager.flush();

        GraduationRequest request = requestGraduationUseCase.request(enrollment.getId());
        authenticateAsPerson("ADMIN80000004");
        verifyGraduationRequestDebtUseCase.verify(request.getId());
        authenticateAsPerson("DIRECTOR80000004");
        approveGraduationRequestUseCase.approve(request.getId());

        GraduationRequest reloaded = entityManager.find(GraduationRequest.class, request.getId());
        assertThat(reloaded.getStatus()).isEqualTo(GraduationRequestStatus.APPROVED);
        ProgramEnrollment reloadedEnrollment = entityManager.find(ProgramEnrollment.class, enrollment.getId());
        assertThat(reloadedEnrollment.getStatus()).isEqualTo(ProgramEnrollmentStatus.COMPLETED);
        assertThat(reloadedEnrollment.getCompletionDate()).isNotNull();
    }

    @Test
    void rejectingARequestedRequestAllowsRequestingAgain() {
        Student student = persistStudent("80000005");
        ProgramEnrollment enrollment = persistEnrollmentWithCompletedPlan(student, "CarreraSinRequisitos4");
        entityManager.flush();

        GraduationRequest first = requestGraduationUseCase.request(enrollment.getId());
        authenticateAsPerson("ADMIN80000005");
        rejectGraduationRequestUseCase.reject(new RejectGraduationRequestCommand(first.getId(), "Datos incompletos"));

        GraduationRequest reloaded = entityManager.find(GraduationRequest.class, first.getId());
        assertThat(reloaded.getStatus()).isEqualTo(GraduationRequestStatus.REJECTED);

        GraduationRequest second = requestGraduationUseCase.request(enrollment.getId());
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getStatus()).isEqualTo(GraduationRequestStatus.REQUESTED);
    }
}

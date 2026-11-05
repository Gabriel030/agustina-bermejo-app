package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-204")
@Tag("D-246")
@Tag("D-296")
class LaterChargesCancellationOnWithdrawalIT extends AbstractInvoiceCancellationIT {
    private static final LocalDate WITHDRAWAL = LocalDate.of(2027, 5, 15);

    @Autowired
    private LaterChargesCancellationOnWithdrawal laterCharges;
    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Staff staff, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(staff.personId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private record Enrollment(Long studentId, Long enrollmentId, Invoice may, Invoice june, Invoice july) {
    }

    private ProgramEnrollment newProgramEnrollment(Long studentId) {
        Student student = studentRepository.findById(studentId).orElseThrow();
        Program program = new Program();
        program.setName("Carrera de baja " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        Program savedProgram = programRepository.save(program);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(savedProgram);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 3, 1, 10, 0));
        return programEnrollmentRepository.save(enrollment);
    }

    private Enrollment enrolledWithMayJuneJulyIssued() {
        Long studentId = newStudentId();
        ProgramEnrollment saved = newProgramEnrollment(studentId);
        return new Enrollment(studentId, saved.getId(), issue(saved, 5), issue(saved, 6), issue(saved, 7));
    }

    private Invoice issue(ProgramEnrollment enrollment, int month) {
        return BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.of(2027, month))
                .dueOn(LocalDate.of(2027, month, 10)).withBaseAmount(ars("80000.00").value()).build();
    }

    private WithdrawnEnrollmentFacts facts(Enrollment enrollment, WithdrawalCause cause) {
        return WithdrawnEnrollmentFacts.ofProgramEnrollment(enrollment.studentId(), enrollment.enrollmentId(), WITHDRAWAL, WITHDRAWAL, cause);
    }

    @Test
    @Tag("C-09")
    void anAdministrativeVoluntaryWithdrawalOpensOneRequestForJuneAndJulyAndLeavesMayAlone() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Enrollment enrollment = enrolledWithMayJuneJulyIssued();
        authenticateAs(newStaff("ADMINISTRATIVO"), "ADMINISTRATIVO");

        inTransaction(() -> {
            laterCharges.cancelLaterChargesOf(facts(enrollment, WithdrawalCause.VOLUNTARY));
            return null;
        });

        assertThat(jdbc.queryForList("select authorization_request_id from authorization_request_invoice where invoice_id = ?", Long.class, enrollment.june().getId()))
                .hasSize(1);
        assertThat(jdbc.queryForList("select invoice_id from authorization_request_invoice where authorization_request_id in "
                + "(select authorization_request_id from authorization_request_invoice where invoice_id = ?)", Long.class, enrollment.june().getId()))
                .containsExactlyInAnyOrder(enrollment.june().getId(), enrollment.july().getId());
        assertThat(paymentStatusOf(enrollment.may().getId())).isEqualTo("PENDING");
        assertThat(paymentStatusOf(enrollment.june().getId())).isEqualTo("PENDING");
        assertThat(creditNoteCountOf(enrollment.may().getId())).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-11")
    void aDirectorVoluntaryWithdrawalCancelsJuneAndJulyDirectly() {
        Enrollment enrollment = enrolledWithMayJuneJulyIssued();
        authenticateAs(newStaff("DIRECTOR"), "DIRECTOR");

        inTransaction(() -> {
            laterCharges.cancelLaterChargesOf(facts(enrollment, WithdrawalCause.VOLUNTARY));
            return null;
        });

        assertThat(paymentStatusOf(enrollment.june().getId())).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(enrollment.july().getId())).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(enrollment.may().getId())).isEqualTo("PENDING");
    }

    @Test
    @Tag("D-246")
    void aNonpaymentWithdrawalDoesNotOpenARequestUntilThereIsARequesterForProcesses() {
        Enrollment enrollment = enrolledWithMayJuneJulyIssued();

        inTransaction(() -> {
            laterCharges.cancelLaterChargesOf(facts(enrollment, WithdrawalCause.NONPAYMENT));
            return null;
        });

        assertThat(jdbc.queryForObject("select count(*) from authorization_request_invoice where invoice_id = ?", Long.class, enrollment.june().getId())).isZero();
        assertThat(paymentStatusOf(enrollment.june().getId())).isEqualTo("PENDING");
    }

    private Long nextYearEnrollmentFee(ProgramEnrollment enrollment, Long templateId) {
        return enrollmentFeeOf(enrollment, templateId, 2028);
    }

    @Test
    @Tag("C-34")
    @Tag("D-237")
    @Tag("D-300")
    @Tag("D-312")
    void theNextYearEnrollmentFeeIsCancelledByTheSystemWhenTheWithdrawalIsBeforeTheStartOfTheTerm() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = nextYearEnrollmentFee(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)));
        Long currentYearFee = enrollmentFeeOf(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2027, 3, 1)), 2027);
        authenticateAs(newStaff("ADMINISTRATIVO"), "ADMINISTRATIVO");

        inTransaction(() -> {
            laterCharges.cancelLaterChargesOf(WithdrawnEnrollmentFacts.ofProgramEnrollment(enrollment.getStudent().getId(), enrollment.getId(),
                    LocalDate.of(2027, 11, 20), LocalDate.of(2027, 11, 20), WithdrawalCause.VOLUNTARY));
            return null;
        });

        assertThat(paymentStatusOf(fee)).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(currentYearFee)).isEqualTo("PENDING");
        assertThat(jdbc.queryForMap("select issued_by_process, authorization_request_id from credit_note where invoice_id = ?", fee))
                .containsEntry("issued_by_process", "FUTURE_ENROLLMENT_FEE_CANCELLATION").containsEntry("authorization_request_id", null);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-34")
    @Tag("D-312")
    void theNextYearEnrollmentFeeIsStillCancelledTheDayBeforeTheStartOfTheTerm() {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = nextYearEnrollmentFee(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)));
        authenticateAs(newStaff("ADMINISTRATIVO"), "ADMINISTRATIVO");

        inTransaction(() -> {
            laterCharges.cancelLaterChargesOf(WithdrawnEnrollmentFacts.ofProgramEnrollment(enrollment.getStudent().getId(), enrollment.getId(),
                    LocalDate.of(2028, 2, 29), LocalDate.of(2028, 2, 29), WithdrawalCause.VOLUNTARY));
            return null;
        });

        assertThat(paymentStatusOf(fee)).isEqualTo("CANCELLED");
    }

    @Test
    @Tag("C-34")
    @Tag("D-312")
    void theNextYearEnrollmentFeeStaysAsDebtFromTheFirstDayOfTheTermInclusive() {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = nextYearEnrollmentFee(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)));

        inTransaction(() -> {
            laterCharges.cancelLaterChargesOf(WithdrawnEnrollmentFacts.ofProgramEnrollment(enrollment.getStudent().getId(), enrollment.getId(),
                    LocalDate.of(2028, 3, 1), LocalDate.of(2028, 3, 1), WithdrawalCause.VOLUNTARY));
            return null;
        });

        assertThat(paymentStatusOf(fee)).isEqualTo("PENDING");
        assertThat(creditNoteCountOf(fee)).isZero();
    }

    @Test
    @Tag("C-14")
    void draftsOfLaterMonthsAreDiscardedAndTheirChargeKeysAreReleased() {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long accountId = newAccountId(enrollment.getStudent().getId(), Currency.ARS);
        Long draftId = inTransaction(() -> {
            Long priceVersionId = PriceFixtures.persistPriceVersion(enrollment.getProgram().getId(), null, FeeType.MONTHLY_INSTALLMENT, "Borrador",
                    ars("80000.00").value(), LocalDate.of(2000, 1, 1), enrollment.getStudent().getPerson().getId());
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT,
                    InvoiceIssuingReason.MONTHLY_RUN, null);
            InvoiceLineDraft line = new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Cuota 6", YearMonth.of(2027, 6), enrollment.getId(), null, null, null,
                    priceVersionId, "PE:" + enrollment.getId() + ":MONTHLY_INSTALLMENT:2027-06", ars("80000.00"));
            return invoiceRepository.save(InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header, line, Optional.empty(), ACTOR)).getId();
        });

        inTransaction(() -> {
            laterCharges.cancelLaterChargesOf(WithdrawnEnrollmentFacts.ofProgramEnrollment(enrollment.getStudent().getId(), enrollment.getId(), WITHDRAWAL,
                    WITHDRAWAL, WithdrawalCause.VOLUNTARY));
            return null;
        });

        assertThat(jdbc.queryForObject("select document_status from invoice where id = ?", String.class, draftId)).isEqualTo("DISCARDED");
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where invoice_id = ? and charge_key_released_at is not null", Long.class, draftId)).isEqualTo(1L);
        assertThat(creditNoteCountOf(draftId)).isZero();
    }

    @Test
    void aFailureOfTheCancellationRollsBackTheWithdrawalItself() {
        Enrollment enrollment = enrolledWithMayJuneJulyIssued();
        Staff administrative = newStaff("ADMINISTRATIVO");
        jdbc.update("update program_enrollment set enrollment_date = ? where id = ?", LocalDateTime.now().minusDays(60), enrollment.enrollmentId());

        assertThatThrownBy(() -> programEnrollmentService.withdraw(enrollment.enrollmentId(), EnrollmentWithdrawal.voluntaryRegisteredBy(null, administrative.personId())))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("select status from program_enrollment where id = ?", String.class, enrollment.enrollmentId())).isEqualTo("ENROLLED");
        assertThat(jdbc.queryForObject("select count(*) from authorization_request_invoice where invoice_id = ?", Long.class, enrollment.june().getId())).isZero();
    }
}

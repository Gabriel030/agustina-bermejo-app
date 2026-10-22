package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.billing.collections.service.LateFeeAccrualService;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.note.exception.PendingBankTransferOnInvoiceException;
import com.red_escuela.backend.billing.service.LatePaymentTestFixtures;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-291")
@Tag("D-400")
@Tag("D-153")
@SlowTest(reason = "Seeds an overdue enrollment fee with its late fee and withdraws the program enrollment against Postgres in three scenarios")
class LateFeeUnderReviewOnWithdrawalCancellationIT extends AbstractInvoiceCancellationIT {
    private static final LocalDate TODAY = LocalDate.of(2028, 1, 5);

    @Autowired
    private LeftOutInvoiceCancellationOnReceiptRejectionListener cancellation;
    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;
    @Autowired
    private ConfigurationVersionRepository configurationVersionRepository;
    @Autowired
    private LateFeeAccrualService accrualService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        MutableClock.INSTANCE.reset();
    }

    private ProgramEnrollment newProgramEnrollment(Long studentId) {
        Student student = studentRepository.findById(studentId).orElseThrow();
        Program program = new Program();
        program.setName("Carrera de baja " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(programRepository.save(program));
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 3, 1, 10, 0));
        return programEnrollmentRepository.save(enrollment);
    }

    private Long overdueNextYearFeeWithItsLateFee(ProgramEnrollment enrollment) {
        Long fee = enrollmentFeeOf(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)), 2028);
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, studentPerson(enrollment.getStudent().getId()), 0, new BigDecimal("0.002"), 30, 1, 10, true,
                true, LocalDate.of(2027, 11, 15));
        BillingTestFixtures.markInvoiceAsOverdueOn(invoiceRepository.findById(fee).orElseThrow(), LocalDate.of(2027, 12, 2));
        MutableClock.INSTANCE.setDate(TODAY);
        assertThat(accrualService.reconcileLateFeesOfInvoice(fee, TODAY)).isEqualTo(1);
        return fee;
    }

    private Long lateFeeOf(Long fee) {
        return jdbc.queryForObject("select debit_note_id from late_fee_charge where origin_invoice_id = ?", Long.class, fee);
    }

    private void putUnderReview(Long invoiceId) {
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", invoiceId);
    }

    private void withdraw(ProgramEnrollment enrollment) {
        Long staffPersonId = newStaff("ADMINISTRATIVO").personId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(staffPersonId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATIVO"))));
        programEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(null, staffPersonId));
    }

    @Test
    @Tag("C3-16")
    void aWithdrawalCancellingTheNextYearFeeAnnulsItsLateFeeInTheSameTransaction() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = overdueNextYearFeeWithItsLateFee(enrollment);
        Long lateFee = lateFeeOf(fee);

        withdraw(enrollment);

        assertThat(paymentStatusOf(fee)).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(lateFee)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select reason from credit_note where invoice_id = ?", String.class, lateFee)).isEqualTo("LATE_FEE_ANNULMENT");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C3-17")
    void aLateFeeUnderPaymentReviewBlocksTheWithdrawalAndLeavesItUnregistered() {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = overdueNextYearFeeWithItsLateFee(enrollment);
        Long lateFee = lateFeeOf(fee);
        putUnderReview(lateFee);

        assertThatThrownBy(() -> withdraw(enrollment)).isInstanceOf(PendingBankTransferOnInvoiceException.class)
                .satisfies(thrown -> assertThat(((PendingBankTransferOnInvoiceException) thrown).violations().get(0).code()).isEqualTo("LATE_FEE_UNDER_PAYMENT_REVIEW"));

        assertThat(programEnrollmentRepository.findById(enrollment.getId()).orElseThrow().getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
        assertThat(paymentStatusOf(fee)).isEqualTo("OVERDUE");
        assertThat(creditNoteCountOf(fee) + creditNoteCountOf(lateFee)).isZero();
    }

    @Test
    @Tag("C3-18")
    void theRejectedReceiptListenerFailsRetriablyWhileALateFeeIsUnderReviewAndCancelsOnceItIsDecided() {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = overdueNextYearFeeWithItsLateFee(enrollment);
        Long lateFee = lateFeeOf(fee);
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", fee);
        withdraw(enrollment);
        assertThat(paymentStatusOf(fee)).isEqualTo("PAYMENT_UNDER_REVIEW");
        jdbc.update("update invoice set payment_status = 'OVERDUE' where id = ?", fee);
        putUnderReview(lateFee);

        assertThatThrownBy(() -> inTransaction(() -> cancellation.cancelIfLeftOutOfWithdrawal(fee))).isInstanceOf(PendingBankTransferOnInvoiceException.class);
        assertThat(paymentStatusOf(fee)).isEqualTo("OVERDUE");
        assertThat(creditNoteCountOf(fee) + creditNoteCountOf(lateFee)).isZero();

        jdbc.update("update invoice set payment_status = ? where id = ?", InvoicePaymentStatus.PENDING.name(), lateFee);
        assertThat(inTransaction(() -> cancellation.cancelIfLeftOutOfWithdrawal(fee))).isTrue();
        assertThat(paymentStatusOf(fee)).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(lateFee)).isEqualTo("CANCELLED");
    }
}

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

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-204")
@Tag("D-246")
class LaterChargesCancellationOnWithdrawalIT extends AbstractInvoiceCancellationIT {
    private static final LocalDate WITHDRAWAL = LocalDate.of(2027, 5, 15);

    @Autowired
    private LaterChargesCancellationOnWithdrawal laterCharges;
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

    private Enrollment enrolledWithMayJuneJulyIssued() {
        Long studentId = newStudentId();
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
        ProgramEnrollment saved = programEnrollmentRepository.save(enrollment);
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
    @Tag("C-15")
    void aNonpaymentWithdrawalDoesNotOpenARequestUntilThereIsARequesterForProcesses() {
        Enrollment enrollment = enrolledWithMayJuneJulyIssued();

        inTransaction(() -> {
            laterCharges.cancelLaterChargesOf(facts(enrollment, WithdrawalCause.NONPAYMENT));
            return null;
        });

        assertThat(jdbc.queryForObject("select count(*) from authorization_request_invoice where invoice_id = ?", Long.class, enrollment.june().getId())).isZero();
        assertThat(paymentStatusOf(enrollment.june().getId())).isEqualTo("PENDING");
    }
}

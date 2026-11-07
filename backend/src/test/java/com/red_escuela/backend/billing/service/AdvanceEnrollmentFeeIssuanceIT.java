package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.advanceenrollmentfee.exception.AdvanceEnrollmentFeeNotAvailableException;
import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeIssuer;
import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeIssuer.AdvanceEnrollmentFeeIssuance;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-523")
@Tag("D-530")
@Tag("D-532")
class AdvanceEnrollmentFeeIssuanceIT extends AbstractInvoiceSequenceTest {

    @Autowired
    private AdvanceEnrollmentFeeIssuer issuer;
    @Autowired
    private EnrollmentFeeRequirementService requirementService;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private record Setup(Career career, Year nextYear, Long enrollment, Long labeledVersion) {
    }

    private Setup september() {
        Career career = newCareer();
        newYear(career, 2027, 10, 1, 2, null);
        Year nextYear = newYear(career, 2028, 10, 1, 2, null);
        transactionTemplate.executeWithoutResult(status -> {
            Term term = entityManager.find(Term.class, nextYear.termId());
            term.setEnrollmentStart(LocalDateTime.of(2027, 12, 1, 0, 0));
            term.setEnrollmentEnd(LocalDateTime.of(2028, 2, 20, 23, 59));
        });
        configureYear(nextYear, template -> {
            var t = com.red_escuela.backend.billing.planning.TestTemplateTerms.termsOf(template);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.replace(template, new com.red_escuela.backend.billing.configuration.domain.YearBillingTerms(
                    t.recurringFeeInvoicesCount(), t.enrollmentFeeCount(), t.firstEnrollmentFeeMonth(), t.secondEnrollmentFeeMonth(), t.enrollmentFeeLeadMonths(),
                    t.lateEnrollmentFeeDueDays(), t.recurringFeeDueDays(), t.withdrawSuspendedAtTermEnd(), t.paysWhilePaused(), t.blocksEnrollment(),
                    t.blocksExamRegistration(), t.blocksCertificates(), t.requireEnrollmentFeeForFinalExamOnlyStudents(), date(2027, 9, 1)));
        });
        Term nextTerm = transactionTemplate.execute(status -> entityManager.find(Term.class, nextYear.termId()));
        newSection(career, nextTerm);
        today(date(2027, 3, 3));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 3), date(2027, 3, 2));
        Long labeled = labeledEnrollmentFeeVersion(career, 1500, 2028);
        today(date(2027, 9, 15));
        return new Setup(career, nextYear, enrollment, labeled);
    }

    private Long labeledEnrollmentFeeVersion(Career career, long amount, int lectiveYear) {
        Long versionId = com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(career.programId(), null, FeeType.ENROLLMENT_FEE, "Matrícula",
                ars(amount), date(2028, 2, 1), career.directorId());
        jdbcTemplate.update("update price_version set applies_to_lective_year = ? where id = ?", lectiveYear, versionId);
        return versionId;
    }

    private void actAsStaff(Career career) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(String.valueOf(career.directorId()), "n/a", "ROLE_DIRECTOR"));
    }

    private void actAsStudentOf(Long enrollment) {
        Long personId = jdbcTemplate.queryForObject("select s.person_id from student s join program_enrollment pe on pe.student_id = s.id where pe.id = ?", Long.class, enrollment);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(String.valueOf(personId), "n/a", "ROLE_ALUMNO"));
    }

    private List<Invoice> advanceInvoices(Long enrollment) {
        return invoices(enrollment, FeeType.ENROLLMENT_FEE).stream()
                .filter(invoice -> invoice.getIssuingReason() == InvoiceIssuingReason.ENROLLMENT_FEE_ADVANCE).toList();
    }

    @Test
    void studentGeneratesTheNextYearFeeWithTheLabeledPriceFrozenOnTheInvoice() {
        Setup setup = september();
        actAsStudentOf(setup.enrollment());

        AdvanceEnrollmentFeeIssuance issuance = issuer.issueForCurrentStudent(setup.enrollment());

        assertThat(issuance.created()).isTrue();
        Invoice invoice = invoiceRepository.findInvoiceWithLinesById(issuance.invoiceId()).orElseThrow();
        assertThat(invoice.getIssuingReason()).isEqualTo(InvoiceIssuingReason.ENROLLMENT_FEE_ADVANCE);
        assertThat(invoice.getTemplateId()).isEqualTo(setup.nextYear().templateId());
        assertThat(invoice.getEnrollmentFeeSlot()).isEqualTo(1);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(invoice.getPrimaryLine().getPriceVersionId()).isEqualTo(setup.labeledVersion());
        assertThat(amountDue(invoice)).isEqualTo(ars(1500));
        assertThat(invoice.getDueDate()).isEqualTo(date(2028, 2, 20));
    }

    @Test
    void aSecondRequestReturnsTheSameInvoiceWithoutCreatingAnother() {
        Setup setup = september();
        actAsStudentOf(setup.enrollment());

        AdvanceEnrollmentFeeIssuance first = issuer.issueForCurrentStudent(setup.enrollment());
        AdvanceEnrollmentFeeIssuance second = issuer.issueForCurrentStudent(setup.enrollment());

        assertThat(second.created()).isFalse();
        assertThat(second.invoiceId()).isEqualTo(first.invoiceId());
        assertThat(advanceInvoices(setup.enrollment())).hasSize(1);
    }

    @Test
    void staffGeneratesTheFeeForTheStudentAndTheStudentThenSeesTheSameInvoice() {
        Setup setup = september();
        actAsStaff(setup.career());

        AdvanceEnrollmentFeeIssuance byStaff = issuer.issueForStaff(setup.enrollment());
        actAsStudentOf(setup.enrollment());
        AdvanceEnrollmentFeeIssuance byStudent = issuer.issueForCurrentStudent(setup.enrollment());

        assertThat(byStaff.created()).isTrue();
        assertThat(byStudent.created()).isFalse();
        assertThat(byStudent.invoiceId()).isEqualTo(byStaff.invoiceId());
    }

    @Test
    void thePaidAdvanceFeeIsNotIssuedAgainByTheJanuaryRunAndTheJanuaryCertificateStaysUnblocked() {
        Setup setup = september();
        actAsStudentOf(setup.enrollment());
        AdvanceEnrollmentFeeIssuance issuance = issuer.issueForCurrentStudent(setup.enrollment());
        BillingTestFixtures.payInvoiceInFull(invoiceRepository.findInvoiceWithLinesById(issuance.invoiceId()).orElseThrow());

        today(date(2028, 1, 4));
        job.run();
        today(date(2028, 2, 3));
        job.run();

        assertThat(advanceInvoices(setup.enrollment())).hasSize(1);
        assertThat(invoices(setup.enrollment(), FeeType.ENROLLMENT_FEE).stream().filter(invoice -> setup.nextYear().templateId().equals(invoice.getTemplateId())))
                .hasSize(1);
        assertThat(invoiceRepository.findInvoiceWithLinesById(issuance.invoiceId()).orElseThrow().getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
    }

    @Test
    void anUnpaidAdvanceFeeOfNextYearDoesNotBlockCertificatesOfTheCurrentYear() {
        Setup setup = september();
        ProgramEnrollment enrollment = transactionTemplate.execute(status -> entityManager.find(ProgramEnrollment.class, setup.enrollment()));
        today(date(2027, 3, 3));
        assertThatThrownBy(() -> requirementService.requireForCurrentPeriod(enrollment, EnrollmentFeeRequirementScope.CERTIFICATE));
        markAllEnrollmentFees(setup.enrollment(), InvoicePaymentStatus.PAID);
        today(date(2027, 9, 15));
        actAsStudentOf(setup.enrollment());
        issuer.issueForCurrentStudent(setup.enrollment());
        today(date(2027, 10, 10));

        requirementService.requireForCurrentPeriod(enrollment, EnrollmentFeeRequirementScope.CERTIFICATE);

        assertThat(advanceInvoices(setup.enrollment())).hasSize(1);
    }

    @Test
    void aPriceChangedAfterwardsKeepsTheIssuedInvoiceAndTheNextOneTakesTheNewVersion() {
        Setup setup = september();
        actAsStudentOf(setup.enrollment());
        AdvanceEnrollmentFeeIssuance first = issuer.issueForCurrentStudent(setup.enrollment());
        jdbcTemplate.update("update price_version set status = 'SUPERSEDED' where id = ?", setup.labeledVersion());
        Long replacement = labeledEnrollmentFeeVersion(setup.career(), 1800, 2028);
        Long otherEnrollment = newEnrollment(setup.career(), null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 3), date(2027, 3, 2));
        actAsStudentOf(otherEnrollment);

        AdvanceEnrollmentFeeIssuance second = issuer.issueForCurrentStudent(otherEnrollment);

        Invoice kept = invoiceRepository.findInvoiceWithLinesById(first.invoiceId()).orElseThrow();
        Invoice fresh = invoiceRepository.findInvoiceWithLinesById(second.invoiceId()).orElseThrow();
        assertThat(amountDue(kept)).isEqualTo(ars(1500));
        assertThat(kept.getPrimaryLine().getPriceVersionId()).isEqualTo(setup.labeledVersion());
        assertThat(amountDue(fresh)).isEqualTo(ars(1800));
        assertThat(fresh.getPrimaryLine().getPriceVersionId()).isEqualTo(replacement);
    }

    @Test
    void anOverdueInstallmentBlocksTheRequestWithTheDebtReason() {
        Setup setup = september();
        transactionTemplate.executeWithoutResult(status -> {
            ProgramEnrollment enrollment = entityManager.find(ProgramEnrollment.class, setup.enrollment());
            BillingTestFixtures.markInvoiceAsOverdue(BillingTestFixtures.issueProgramMonthlyInstallment(enrollment, YearMonth.of(2027, 8), ars(10000)));
        });
        actAsStudentOf(setup.enrollment());

        assertThatThrownBy(() -> issuer.issueForCurrentStudent(setup.enrollment()))
                .isInstanceOf(AdvanceEnrollmentFeeNotAvailableException.class)
                .satisfies(error -> assertThat(((AdvanceEnrollmentFeeNotAvailableException) error).violations().get(0).code()).isEqualTo("ADVANCE_FEE_OVERDUE_DEBT"));
        assertThat(advanceInvoices(setup.enrollment())).isEmpty();
    }

    @Test
    void withoutALabeledPriceTheRequestIsRefused() {
        Setup setup = september();
        jdbcTemplate.update("update price_version set applies_to_lective_year = null where id = ?", setup.labeledVersion());
        actAsStudentOf(setup.enrollment());

        assertThatThrownBy(() -> issuer.issueForCurrentStudent(setup.enrollment()))
                .isInstanceOf(AdvanceEnrollmentFeeNotAvailableException.class)
                .satisfies(error -> assertThat(((AdvanceEnrollmentFeeNotAvailableException) error).violations().get(0).code()).isEqualTo("ADVANCE_FEE_NEXT_YEAR_NOT_CONFIGURED"));
    }
}

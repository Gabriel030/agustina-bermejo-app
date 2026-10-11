package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.ScholarshipScope;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceIssuedDomainEvent;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RecordApplicationEvents
@Tag("D-075")
@Tag("D-099")
@Tag("D-051")
class InvoiceIssuingOperationIT extends AbstractInvoiceIssuingIT {

    @Autowired
    private ApplicationEvents events;

    private Percentage percent(String points) {
        return Percentage.ofPoints(new BigDecimal(points));
    }

    private IssuingScenarioFactory.Offering program() {
        return factory.newProgram(Currency.ARS, 80000, 50000);
    }

    @Test
    @Tag("D-144")
    void aMonthlyInstallmentCreatesAnIssuedInvoiceWithLineChargeKeyDebitAndEvent() {
        var offering = program();
        var learner = factory.newLearner(offering, null);

        Invoice invoice = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1))));

        assertThat(invoice.getInvoiceType()).isEqualTo(InvoiceType.MONTHLY_INSTALLMENT);
        assertThat(invoice.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.ISSUED);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(invoice.getInvoiceNumber().value()).startsWith("F-");
        assertThat(invoice.getIssueDate()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(invoice.getLines()).hasSize(1);
        assertThat(invoice.getLines().get(0).getChargeKey()).isEqualTo("PE:" + learner.programEnrollmentId() + ":MONTHLY_INSTALLMENT:2027-03");
        assertThat(invoice.getLines().get(0).getPriceVersionId()).isNotNull();
        assertThat(invoice.getLines().get(0).getAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getAmounts().total()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getStudentSnapshot().fullName()).startsWith("Persona");
        List<Map<String, Object>> entries = entriesOf(learner.studentId(), Currency.ARS);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("direction")).isEqualTo("DEBIT");
        assertThat((BigDecimal) entries.get(0).get("running_balance_amount")).isEqualByComparingTo("80000.00");
        assertThat(entries.get(0).get("running_balance_side")).isEqualTo("OWES");
        assertThat(events.stream(InvoiceIssuedDomainEvent.class).filter(event -> event.invoiceId().equals(invoice.getId())).toList()).hasSize(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void everyAdHocInvoiceTypeIsIssuedWithItsLineDebitAndExactTotal() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        int index = 0;
        java.math.BigDecimal expectedBalance = BigDecimal.ZERO;
        for (FeeType feeType : List.of(FeeType.EXAM_FEE, FeeType.RETAKEN_SUBJECT_FEE, FeeType.DEGREE_ISSUANCE_FEE, FeeType.ADMINISTRATIVE_FEE)) {
            String amount = "1234.5" + index;
            Invoice invoice = issued(runner.issueInvoiceInNewTransaction(adHoc(offering, learner, feeType, amount, "k-" + feeType + learner.studentId())));
            expectedBalance = expectedBalance.add(new BigDecimal(amount));
            assertThat(invoice.getInvoiceType().name()).isEqualTo(feeType.name());
            assertThat(invoice.getLines().get(0).getChargeKey()).isEqualTo("AH:" + learner.studentId() + ":" + feeType + ":k-" + feeType + learner.studentId());
            assertThat(invoice.getLines().get(0).getPriceVersionId()).isNull();
            assertThat(invoice.getAmounts().total().value().amount()).isEqualByComparingTo(amount);
            List<Map<String, Object>> entries = entriesOf(learner.studentId(), Currency.ARS);
            assertThat(entries).hasSize(++index);
            assertThat((BigDecimal) entries.get(index - 1).get("running_balance_amount")).isEqualByComparingTo(expectedBalance);
        }
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-073")
    @Tag("D-074")
    void scholarshipHalfWinsOverPromotionAndOnlyTheWinnerConsumes() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        Long scholarshipId = factory.grantScholarship(offering, learner, percent("50"), 3, ScholarshipScope.INSTALLMENTS);
        Long benefitId = factory.redeemPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, percent("30"), 2);

        Invoice invoice = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1))));

        var discount = invoice.getLines().get(0).getDiscount();
        assertThat(discount.getDiscountAmount()).isEqualTo(ars("40000.00"));
        assertThat(discount.getBaseAmount()).isEqualTo(ars("80000.00"));
        assertThat(discount.describeInstallmentProgress()).contains("1 de 3");
        assertThat(discount.getStudentDiscountId()).isEqualTo(scholarshipId);
        assertThat(invoice.getAmounts().total()).isEqualTo(ars("40000.00"));
        assertThat(jdbc.queryForObject("select count(*) from scholarship_installment where scholarship_id = ? and status = 'APPLIED'", Long.class, scholarshipId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from invoice_discount where discount_type = 'PROMOTION' and student_discount_id = ?", Long.class, benefitId)).isZero();
        assertThat((BigDecimal) entriesOf(learner.studentId(), Currency.ARS).get(0).get("amount")).isEqualByComparingTo("40000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-074")
    void whenTheScholarshipEndsThePromotionStartsCounting() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        factory.grantScholarship(offering, learner, percent("50"), 1, ScholarshipScope.INSTALLMENTS);
        Long benefitId = factory.redeemPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, percent("30"), 2);

        Invoice march = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1))));
        Invoice april = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 4), LocalDate.of(2027, 4, 11), LocalDate.of(2027, 4, 1))));
        Invoice may = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 5), LocalDate.of(2027, 5, 11), LocalDate.of(2027, 5, 1))));
        Invoice june = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 6), LocalDate.of(2027, 6, 11), LocalDate.of(2027, 6, 1))));

        assertThat(march.getLines().get(0).getDiscount().getDiscountAmount()).isEqualTo(ars("40000.00"));
        assertThat(april.getLines().get(0).getDiscount().getStudentDiscountId()).isEqualTo(benefitId);
        assertThat(april.getLines().get(0).getDiscount().describeInstallmentProgress()).contains("1 de 2");
        assertThat(may.getLines().get(0).getDiscount().describeInstallmentProgress()).contains("2 de 2");
        assertThat(june.getLines().get(0).getDiscount()).isNull();
        assertThat(june.getAmounts().total()).isEqualTo(ars("80000.00"));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-150")
    void aFullScholarshipIssuesAZeroInvoiceAlreadyPaidWithoutAccountMovement() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        factory.grantScholarship(offering, learner, percent("100"), 3, ScholarshipScope.INSTALLMENTS);
        factory.redeemPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, percent("30"), 2);

        Invoice invoice = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1))));

        assertThat(invoice.getLines().get(0).getAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getLines().get(0).getDiscount().getDiscountAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getAmounts().total()).isEqualTo(ars("0.00"));
        assertThat(invoice.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.ISSUED);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(entriesOf(learner.studentId(), Currency.ARS)).isEmpty();
        assertThat(events.stream(com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent.class)
                .filter(event -> event.invoiceId().equals(invoice.getId())).toList()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from invoice_status_change where invoice_id = ?", Long.class, invoice.getId())).isGreaterThanOrEqualTo(3L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-157")
    void aScholarshipCoveringBothScopesNeverDiscountsTheEnrollmentFee() {
        var offering = program();
        Long template = factory.newTemplate(offering, 2027);
        var learner = factory.newLearner(offering, null);
        Long scholarshipId = factory.grantScholarship(offering, learner, percent("50"), 3, ScholarshipScope.BOTH);

        Invoice invoice = issued(runner.issueInvoiceInNewTransaction(enrollmentFee(offering, learner, template, 1, YearMonth.of(2027, 3),
                LocalDate.of(2027, 3, 11), null, false, InvoiceIssuingReason.MONTHLY_RUN)));

        assertThat(invoice.getInvoiceType()).isEqualTo(InvoiceType.ENROLLMENT_FEE);
        assertThat(invoice.getLines().get(0).getDiscount()).isNull();
        assertThat(invoice.getAmounts().total()).isEqualTo(ars("50000.00"));
        assertThat(invoice.getLines().get(0).getChargeKey()).isEqualTo("PE:" + learner.programEnrollmentId() + ":ENROLLMENT_FEE:T" + template + ":S1");
        assertThat(jdbc.queryForObject("select count(*) from scholarship_installment where scholarship_id = ? and status = 'APPLIED'", Long.class, scholarshipId)).isZero();
    }

    @Test
    @Tag("D-018")
    @Tag("D-019")
    void theEntryDiscountAppliesToTheEntryFeeButNotToTheAdmissionFee() {
        var offering = program();
        Long template = factory.newTemplate(offering, 2027);
        Long cohort = factory.newCohort(template, CohortIntake.SECOND, LocalDate.of(2027, 8, 3), percent("50"));
        var learner = factory.newLearner(offering, cohort);
        var applicant = factory.newLearner(offering, cohort);

        Invoice entry = issued(runner.issueInvoiceInNewTransaction(enrollmentFee(offering, learner, template, 2, YearMonth.of(2027, 8),
                LocalDate.of(2027, 8, 10), cohort, true, InvoiceIssuingReason.MONTHLY_RUN)));
        Invoice admission = issued(runner.issueInvoiceInNewTransaction(enrollmentFee(offering, applicant, template, 2, YearMonth.of(2027, 8),
                LocalDate.of(2027, 3, 10), cohort, true, InvoiceIssuingReason.PROGRAM_ADMISSION)));

        assertThat(entry.getLines().get(0).getAmount()).isEqualTo(ars("50000.00"));
        assertThat(entry.getLines().get(0).getDiscount().getDiscountAmount()).isEqualTo(ars("25000.00"));
        assertThat(entry.getAmounts().total()).isEqualTo(ars("25000.00"));
        assertThat(admission.getLines().get(0).getDiscount()).isNull();
        assertThat(admission.getLines().get(0).getChargeKey()).isEqualTo("PA:" + applicant.studentId() + ":ENROLLMENT_FEE:T" + template + ":S2");
        assertThat(admission.getLines().get(0).getProgramEnrollmentId()).isNull();
    }

    @Test
    @Tag("D-035")
    @Tag("D-161")
    void thePriceInForceOnThePricingDateIsUsedAndTravelsInTheLine() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        factory.newPrice(offering, FeeType.MONTHLY_INSTALLMENT, 90000, LocalDate.of(2027, 3, 3));
        today(LocalDate.of(2027, 3, 5));

        Invoice old = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1))));
        Invoice current = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 4), LocalDate.of(2027, 4, 11), LocalDate.of(2027, 3, 5))));

        assertThat(old.getAmounts().total()).isEqualTo(ars("80000.00"));
        assertThat(current.getAmounts().total()).isEqualTo(ars("90000.00"));
        assertThat(old.getLines().get(0).getPriceVersionId()).isNotEqualTo(current.getLines().get(0).getPriceVersionId());
        assertThat(old.getAmounts().total()).isEqualTo(ars("80000.00"));
    }

    @Test
    @Tag("D-057")
    void aDueDateBeforeTheIssueDateIsRejectedAndNothingIsPersisted() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        long invoices = countRows("invoice");
        long lines = countRows("invoice_line");
        long entries = countRows("current_account_entry");
        long published = events.stream(InvoiceIssuedDomainEvent.class).count();

        InvoiceIssuingResult result = runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 1)));

        assertThat(result).isInstanceOf(InvoiceIssuingResult.InvoiceIssuingRejected.class);
        assertThat(((InvoiceIssuingResult.InvoiceIssuingRejected) result).evaluationResult().firstDenial().orElseThrow().code()).isEqualTo("DUE_DATE_BEFORE_ISSUE_DATE");
        assertThat(countRows("invoice")).isEqualTo(invoices);
        assertThat(countRows("invoice_line")).isEqualTo(lines);
        assertThat(countRows("current_account_entry")).isEqualTo(entries);
        assertThat(events.stream(InvoiceIssuedDomainEvent.class).count()).isEqualTo(published);
        assertThat(entriesOf(learner.studentId(), Currency.ARS)).isEmpty();
    }

    @Test
    @Tag("D-009")
    @Tag("D-021")
    void theSameChargeRequestedTwiceReturnsTheExistingInvoice() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        InvoiceIssuingRequest request = installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1));

        Invoice first = issued(runner.issueInvoiceInNewTransaction(request));
        InvoiceIssuingResult second = runner.issueInvoiceInNewTransaction(request);

        assertThat(second).isEqualTo(new InvoiceIssuingResult.InvoiceAlreadyIssued(first.getId()));
        assertThat(entriesOf(learner.studentId(), Currency.ARS)).hasSize(1);
    }

    @Test
    @Tag("D-009")
    void anAdHocChargeWithTheSameIdempotencyKeyTwiceIsOneInvoice() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        InvoiceIssuingRequest request = adHoc(offering, learner, FeeType.EXAM_FEE, "5000.00", "idem-" + learner.studentId());

        Invoice first = issued(runner.issueInvoiceInNewTransaction(request));
        InvoiceIssuingResult second = runner.issueInvoiceInNewTransaction(request);

        assertThat(second).isEqualTo(new InvoiceIssuingResult.InvoiceAlreadyIssued(first.getId()));
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where charge_key = ?", Long.class, "AH:" + learner.studentId() + ":EXAM_FEE:idem-" + learner.studentId())).isEqualTo(1L);
    }

    @Test
    @Tag("D-033")
    @Tag("D-145")
    void enrollmentFeeAndInstallmentOfTheSameMonthAreSeparateInvoicesWithTheirOwnDebitAndRunningBalance() {
        var offering = program();
        Long template = factory.newTemplate(offering, 2027);
        var learner = factory.newLearner(offering, null);

        issued(runner.issueInvoiceInNewTransaction(enrollmentFee(offering, learner, template, 1, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), null, false,
                InvoiceIssuingReason.MONTHLY_RUN)));
        issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1))));
        issued(runner.issueInvoiceInNewTransaction(adHoc(offering, learner, FeeType.ADMINISTRATIVE_FEE, "1500.00", "adm-" + learner.studentId())));

        List<Map<String, Object>> entries = entriesOf(learner.studentId(), Currency.ARS);
        assertThat(entries).hasSize(3);
        assertThat(entries.stream().map(entry -> (BigDecimal) entry.get("running_balance_amount")).toList())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("50000.00"), new BigDecimal("130000.00"), new BigDecimal("131500.00"));
        assertThat(entries.stream().map(entry -> entry.get("invoice_id")).distinct().count()).isEqualTo(3L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-097")
    void arsAndUsdOfTheSamePersonLiveInDifferentAccounts() {
        var ars = program();
        var usd = factory.newProgram(Currency.USD, 100, 60);
        var arsLearner = factory.newLearner(ars, null);
        Long studentId = arsLearner.studentId();
        var usdRequest = new InvoiceIssuingRequest(studentId, null, null, usd.programId(), null, null, FeeType.ADMINISTRATIVE_FEE,
                YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1), InvoiceIssuingReason.AD_HOC_CHARGE, null, null, null, false,
                com.red_escuela.backend.shared.domain.NonNegativeMoney.of(new BigDecimal("100.00"), Currency.USD), "Tasa", null, null, "usd-" + studentId, ACTOR);

        issued(runner.issueInvoiceInNewTransaction(installment(ars, arsLearner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1))));
        issued(runner.issueInvoiceInNewTransaction(usdRequest));

        assertThat(accountOf(studentId, Currency.ARS).getId()).isNotEqualTo(accountOf(studentId, Currency.USD).getId());
        assertThat(accountOf(studentId, Currency.ARS).getBalance().amount().value().amount()).isEqualByComparingTo("80000.00");
        assertThat(accountOf(studentId, Currency.USD).getBalance().amount().value().amount()).isEqualByComparingTo("100.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theCallerTransactionVariantRequiresATransactionAndJoinsIt() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        InvoiceIssuingRequest request = installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1));

        assertThatThrownBy(() -> runner.issueInvoiceInCallerTransaction(request)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);

        InvoiceIssuingResult inside = transactionTemplate.execute(status -> runner.issueInvoiceInCallerTransaction(request));
        assertThat(inside).isInstanceOf(InvoiceIssuingResult.InvoiceIssued.class);
        InvoiceIssuingResult again = transactionTemplate.execute(status -> runner.issueInvoiceInCallerTransaction(request));
        assertThat(again).isInstanceOf(InvoiceIssuingResult.InvoiceAlreadyIssued.class);
    }

    @Test
    void aRollbackOfTheCallerTransactionLeavesNoInvoiceNoDebitAndNoScholarshipConsumption() {
        var offering = program();
        var learner = factory.newLearner(offering, null);
        Long scholarshipId = factory.grantScholarship(offering, learner, percent("50"), 3, ScholarshipScope.INSTALLMENTS);
        InvoiceIssuingRequest request = installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            runner.issueInvoiceInCallerTransaction(request);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("select count(*) from invoice_line where charge_key = ?", Long.class, "PE:" + learner.programEnrollmentId() + ":MONTHLY_INSTALLMENT:2027-03")).isZero();
        assertThat(entriesOf(learner.studentId(), Currency.ARS)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from scholarship_installment where scholarship_id = ? and status = 'APPLIED'", Long.class, scholarshipId)).isZero();
    }
}

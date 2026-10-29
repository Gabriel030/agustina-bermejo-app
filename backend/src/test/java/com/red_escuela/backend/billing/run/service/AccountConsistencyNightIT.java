package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.billing.events.InconsistentAccountDetectedEvent;
import com.red_escuela.backend.billing.run.domain.BillingRunItemStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus;
import com.red_escuela.backend.billing.run.domain.NightlyBillingTrigger;
import com.red_escuela.backend.billing.run.dto.BillingRunItemResponse;
import com.red_escuela.backend.billing.run.dto.NightlyBillingRunResponse;
import com.red_escuela.backend.billing.run.usecase.BillingRunQueryUseCase;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-356")
@Tag("D-459")
@RecordApplicationEvents
class AccountConsistencyNightIT extends AbstractCurrentAccountIT {
    private static final LocalDate DUE = LocalDate.of(2027, 3, 10);

    @Autowired
    private NightlyBillingRunner runner;
    @Autowired
    private BillingRunQueryUseCase queryUseCase;
    @Autowired
    private ApplicationEvents events;

    private record Student(Long accountId, Long invoiceId) {
    }

    private Student studentWithAnInvoiceAndItsPaymentWaiting() {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long invoiceId = issueAdministrativeFeeWithLedgerDebit(accountId, "50000.00", DUE);
        long paymentId = BillingTestFixtures.seedApprovedPaymentRow(accountId, ars("50000.00"));
        inTransaction(() -> service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), paymentId, ars("50000.00"), DUE, "Pago"));
        return new Student(accountId, invoiceId);
    }

    private NightlyBillingRunResponse runTheNight(LocalDate day) {
        Long id = runner.runNightlyBillingSteps(day, NightlyBillingTrigger.SCHEDULED).orElseThrow();
        return queryUseCase.findNightlyBillingRun(id);
    }

    private List<BillingRunItemResponse> itemsOf(NightlyBillingRunResponse night, BillingRunStep step) {
        return queryUseCase.listBillingRunItems(night.id(), step);
    }

    private String statusOf(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    private String unallocatedCreditOf(Long accountId) {
        return amount("select unallocated_credit_amount from student_current_account where id = ?", accountId).toPlainString();
    }

    @Test
    void aHealthyBookProducesNoConsistencyItemsAndNoAlertAndTheLaterStepsRunForEveryone() {
        Student first = studentWithAnInvoiceAndItsPaymentWaiting();
        Student second = studentWithAnInvoiceAndItsPaymentWaiting();

        NightlyBillingRunResponse night = runTheNight(LocalDate.of(2027, 6, 1));

        assertThat(night.status()).isEqualTo(NightlyBillingRunStatus.COMPLETED);
        assertThat(night.steps().get(0).step()).isEqualTo(BillingRunStep.ACCOUNT_CONSISTENCY);
        assertThat(night.steps().get(0).skipped()).isZero();
        assertThat(night.steps().get(0).status()).isEqualTo(BillingRunStatus.COMPLETED);
        assertThat(itemsOf(night, BillingRunStep.ACCOUNT_CONSISTENCY)).isEmpty();
        assertThat(events.stream(InconsistentAccountDetectedEvent.class).count()).isZero();
        assertThat(unallocatedCreditOf(first.accountId())).isEqualTo("0.00");
        assertThat(unallocatedCreditOf(second.accountId())).isEqualTo("0.00");
        assertThat(statusOf(first.invoiceId())).isEqualTo("PAID");
    }

    @Test
    void anAccountOffByThirtyFiveThousandIsMarkedAlertedAndLeftUntouchedByTheOverdueAndAllocationStepsWhileTheOtherAccountIsProcessed() {
        Student broken = studentWithAnInvoiceAndItsPaymentWaiting();
        Student healthy = studentWithAnInvoiceAndItsPaymentWaiting();
        jdbc.update("update student_current_account set balance_amount = 35000, balance_side = 'OWES' where id = ?", broken.accountId());

        NightlyBillingRunResponse night = runTheNight(LocalDate.of(2027, 6, 2));

        assertThat(night.status()).isNotEqualTo(NightlyBillingRunStatus.FAILED);
        assertThat(night.failedSteps()).isZero();
        assertThat(night.steps().get(0).skipped()).isEqualTo(1);
        assertThat(itemsOf(night, BillingRunStep.ACCOUNT_CONSISTENCY)).singleElement().satisfies(item -> {
            assertThat(item.subjectType()).isEqualTo(BillingRunSubjectType.CURRENT_ACCOUNT);
            assertThat(item.subjectId()).isEqualTo(broken.accountId());
            assertThat(item.status()).isEqualTo(BillingRunItemStatus.SKIPPED);
            assertThat(item.decisionCode()).isEqualTo("INCONSISTENT_ACCOUNT");
            assertThat(item.message()).contains("35000.00");
        });
        assertThat(events.stream(InconsistentAccountDetectedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.accountId()).isEqualTo(broken.accountId());
            assertThat(event.businessDate()).isEqualTo(LocalDate.of(2027, 6, 2));
            assertThat(event.findingCode()).isEqualTo("INCONSISTENT_ACCOUNT");
        });
        assertThat(itemsOf(night, BillingRunStep.OVERDUE_DETECTION).stream().filter(item -> "INCONSISTENT_ACCOUNT".equals(item.decisionCode())).toList()).extracting(BillingRunItemResponse::subjectType, BillingRunItemResponse::subjectId, BillingRunItemResponse::decisionCode)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(BillingRunSubjectType.INVOICE, broken.invoiceId(), "INCONSISTENT_ACCOUNT"));
        assertThat(itemsOf(night, BillingRunStep.ACCOUNT_CREDIT_ALLOCATION)).extracting(BillingRunItemResponse::subjectType, BillingRunItemResponse::subjectId,
                BillingRunItemResponse::decisionCode).containsExactly(org.assertj.core.groups.Tuple.tuple(BillingRunSubjectType.CURRENT_ACCOUNT, broken.accountId(), "INCONSISTENT_ACCOUNT"));
        assertThat(statusOf(broken.invoiceId())).isEqualTo("PENDING");
        assertThat(unallocatedCreditOf(broken.accountId())).isEqualTo("50000.00");
        assertThat(statusOf(healthy.invoiceId())).isEqualTo("PAID");
        assertThat(unallocatedCreditOf(healthy.accountId())).isEqualTo("0.00");

        jdbc.update("update student_current_account set balance_amount = 0, balance_side = 'ZERO' where id = ?", broken.accountId());
    }
}

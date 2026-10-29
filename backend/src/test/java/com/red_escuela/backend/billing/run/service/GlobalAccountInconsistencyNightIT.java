package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.billing.events.InconsistentAccountDetectedEvent;
import com.red_escuela.backend.billing.run.domain.BillingRunStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus;
import com.red_escuela.backend.billing.run.domain.NightlyBillingTrigger;
import com.red_escuela.backend.billing.run.dto.NightlyBillingRunResponse;
import com.red_escuela.backend.billing.run.usecase.BillingRunQueryUseCase;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDate;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-356")
@Tag("D-459")
@RecordApplicationEvents
class GlobalAccountInconsistencyNightIT extends AbstractCurrentAccountIT {
    @Autowired
    private NightlyBillingRunner runner;
    @Autowired
    private BillingRunQueryUseCase queryUseCase;
    @Autowired
    private ApplicationEvents events;

    private NightlyBillingRunResponse runTheNightWithBrokenAccounts(int accounts, int broken, LocalDate day) {
        IntStream.range(0, accounts).forEach(index -> {
            Long accountId = newAccountId(newStudentId(), Currency.ARS);
            issueAdministrativeFeeWithLedgerDebit(accountId, "50000.00", LocalDate.of(2027, 3, 10));
            if (index < broken) {
                jdbc.update("update student_current_account set balance_amount = balance_amount + 35000 where id = ?", accountId);
            }
        });
        return queryUseCase.findNightlyBillingRun(runner.runNightlyBillingSteps(day, NightlyBillingTrigger.SCHEDULED).orElseThrow());
    }

    @Test
    void whenHalfOrMoreOfTheAccountsDoNotCloseTheFirstStepFailsTheNightWithoutRunningTheOthersNorAlertingPerAccount() {
        NightlyBillingRunResponse night = runTheNightWithBrokenAccounts(6, 6, LocalDate.of(2027, 6, 3));

        assertThat(night.status()).isEqualTo(NightlyBillingRunStatus.FAILED);
        assertThat(night.steps()).singleElement().satisfies(step -> {
            assertThat(step.step()).isEqualTo(BillingRunStep.ACCOUNT_CONSISTENCY);
            assertThat(step.status()).isEqualTo(BillingRunStatus.FAILED);
        });
        assertThat(events.stream(InconsistentAccountDetectedEvent.class).count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from billing_run_item i join billing_run r on r.id = i.billing_run_id where r.nightly_billing_run_id = ?", Long.class, night.id())).isZero();
    }
}

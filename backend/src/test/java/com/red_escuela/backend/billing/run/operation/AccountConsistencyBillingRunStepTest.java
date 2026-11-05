package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.domain.AccountConsistencyFinding;
import com.red_escuela.backend.billing.run.domain.AccountConsistencyScan;
import com.red_escuela.backend.billing.run.domain.BillingRunItemStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import com.red_escuela.backend.billing.run.exception.GlobalAccountInconsistencyException;
import com.red_escuela.backend.billing.run.policy.InconsistencyStaysLocalContext;
import com.red_escuela.backend.billing.run.policy.InconsistencyStaysLocalPolicy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-356")
@Tag("D-459")
@Tag("D-489")
class AccountConsistencyBillingRunStepTest {
    private static final LocalDate NIGHT = LocalDate.of(2031, 10, 7);

    private final AccountConsistencyScanner scanner = mock(AccountConsistencyScanner.class);
    private final InconsistentAccountAlerter alerter = mock(InconsistentAccountAlerter.class);
    private final AccountConsistencyBillingRunStep step = new AccountConsistencyBillingRunStep(scanner,
            PolicySet.named("ACCOUNT_INCONSISTENCY_SCOPE", List.of(new InconsistencyStaysLocalPolicy())), alerter);

    private static AccountConsistencyFinding balanceFinding(long accountId) {
        return new AccountConsistencyFinding(accountId, "INCONSISTENT_ACCOUNT", "account " + accountId + " difference 35000.00");
    }

    @Test
    void anInconsistentAccountBecomesASkippedItemAndAlertsTheDirectorWithoutStoppingTheNight() {
        when(scanner.scanAllAccounts(NIGHT)).thenReturn(new AccountConsistencyScan(100, List.of(balanceFinding(7L))));

        BillingRunStepOutcome outcome = step.runStepForBusinessDate(NIGHT);

        assertThat(outcome.candidates()).isEqualTo(100);
        assertThat(outcome.skipped()).isEqualTo(1);
        assertThat(outcome.processed()).isEqualTo(99);
        assertThat(outcome.failed()).isZero();
        assertThat(outcome.items()).singleElement().satisfies(item -> {
            assertThat(item.subjectType()).isEqualTo(BillingRunSubjectType.CURRENT_ACCOUNT);
            assertThat(item.subjectId()).isEqualTo(7L);
            assertThat(item.status()).isEqualTo(BillingRunItemStatus.SKIPPED);
            assertThat(item.decisionCode()).isEqualTo("INCONSISTENT_ACCOUNT");
        });
        verify(alerter).alertDirectorOfInconsistentAccounts(List.of(balanceFinding(7L)), NIGHT);
    }

    @Test
    void aHealthyBookHasNoItemsNoSkipsAndNoAlert() {
        when(scanner.scanAllAccounts(NIGHT)).thenReturn(new AccountConsistencyScan(100, List.of()));

        BillingRunStepOutcome outcome = step.runStepForBusinessDate(NIGHT);

        assertThat(outcome.items()).isEmpty();
        assertThat(outcome.skipped()).isZero();
        assertThat(outcome.processed()).isEqualTo(100);
        assertThat(step.step()).isEqualTo(BillingRunStep.ACCOUNT_CONSISTENCY);
    }

    @Test
    void whenEveryAccountIsInconsistentTheStepFailsEntirelyInsteadOfSkippingThemAll() {
        List<AccountConsistencyFinding> all = IntStream.rangeClosed(1, 6).mapToObj(AccountConsistencyBillingRunStepTest::balanceFinding).toList();
        when(scanner.scanAllAccounts(NIGHT)).thenReturn(new AccountConsistencyScan(6, all));

        assertThatThrownBy(() -> step.runStepForBusinessDate(NIGHT)).isInstanceOf(GlobalAccountInconsistencyException.class);
        verify(alerter, never()).alertDirectorOfInconsistentAccounts(any(), any());
    }

    @Test
    void anAccountWithSeveralFindingsProducesOneItemWithTheFirstPriorityFinding() {
        AccountConsistencyScan scan = new AccountConsistencyScan(10, List.of(balanceFinding(3L),
                new AccountConsistencyFinding(3L, "DUPLICATE_LIVE_CHARGE_KEY", "charge key X")));

        assertThat(scan.findings()).containsExactly(balanceFinding(3L));
        assertThat(scan.inconsistentAccounts()).isEqualTo(1);
    }

    @Test
    void theScopePolicyNeedsBothFiveAccountsAndAFifthOfTheBookToBeGlobal() {
        InconsistencyStaysLocalPolicy policy = new InconsistencyStaysLocalPolicy();

        assertThat(policy.evaluateRule(new InconsistencyStaysLocalContext(4, 4)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new InconsistencyStaysLocalContext(25, 5)).allowed()).isFalse();
        assertThat(policy.evaluateRule(new InconsistencyStaysLocalContext(26, 5)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new InconsistencyStaysLocalContext(100, 30)).allowed()).isFalse();
        assertThat(policy.evaluateRule(new InconsistencyStaysLocalContext(100, 19)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new InconsistencyStaysLocalContext(1000, 4)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new InconsistencyStaysLocalContext(5, 5)).allowed()).isFalse();
        assertThat(policy.evaluateRule(new InconsistencyStaysLocalContext(1, 0)).allowed()).isTrue();
        assertThat(InconsistencyStaysLocalPolicy.MINIMUM_INCONSISTENT_ACCOUNTS_TO_BE_GLOBAL).isEqualTo(5);
        assertThat(policy.ruleName()).isEqualTo("InconsistencyStaysLocalPolicy");
    }

    @Test
    void theContextCountsTheInconsistentAccountsOfTheScan() {
        AccountConsistencyScan scan = new AccountConsistencyScan(9, List.of(balanceFinding(1L), balanceFinding(2L)));

        InconsistencyStaysLocalContext context = InconsistencyStaysLocalContext.of(scan);

        assertThat(context.totalAccounts()).isEqualTo(9);
        assertThat(context.inconsistentAccounts()).isEqualTo(2);
    }

    @Test
    void theAlerterPublishesOneEventPerFindingWithItsAccountDateAndCode() {
        org.springframework.context.ApplicationEventPublisher publisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        org.mockito.ArgumentCaptor<Object> published = org.mockito.ArgumentCaptor.forClass(Object.class);

        new InconsistentAccountAlerter(publisher).alertDirectorOfInconsistentAccounts(List.of(balanceFinding(7L), balanceFinding(8L)), NIGHT);

        verify(publisher, org.mockito.Mockito.times(2)).publishEvent(published.capture());
        assertThat(published.getAllValues()).extracting(event -> ((com.red_escuela.backend.billing.events.InconsistentAccountDetectedEvent) event).accountId()).containsExactly(7L, 8L);
        assertThat(published.getAllValues()).allSatisfy(event -> {
            assertThat(((com.red_escuela.backend.billing.events.InconsistentAccountDetectedEvent) event).businessDate()).isEqualTo(NIGHT);
            assertThat(((com.red_escuela.backend.billing.events.InconsistentAccountDetectedEvent) event).findingCode()).isEqualTo("INCONSISTENT_ACCOUNT");
        });
    }

    @Test
    void theScopePolicyTreatsFourInconsistentAccountsAsLocalAndFiveOfTenAsGlobal() {
        InconsistencyStaysLocalPolicy policy = new InconsistencyStaysLocalPolicy();

        assertThat(policy.evaluateRule(new InconsistencyStaysLocalContext(4, 4)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new InconsistencyStaysLocalContext(26, 5)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new InconsistencyStaysLocalContext(10, 5)).allowed()).isFalse();
        assertThat(policy.evaluateRule(new InconsistencyStaysLocalContext(10, 5)).code()).isEqualTo("GLOBAL_ACCOUNT_INCONSISTENCY");
    }
}

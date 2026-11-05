package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationCommand;
import com.red_escuela.backend.billing.account.repository.StudentCurrentAccountRepository;
import com.red_escuela.backend.billing.account.service.AccountCreditAllocationOperation;
import com.red_escuela.backend.billing.configuration.domain.AllocationSettings;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.collections.operation.HeldSubjectFinder;
import com.red_escuela.backend.billing.run.domain.BillingRunItemRecord;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AccountCreditAllocationBillingRunStep extends AbstractBillingRunStep {
    public static final String AUTO_APPLY_CREDIT_DISABLED = "AUTO_APPLY_CREDIT_DISABLED";
    private static final Logger log = LoggerFactory.getLogger(AccountCreditAllocationBillingRunStep.class);
    private static final int PAGE_SIZE = 200;
    private static final StatusChangeActor NIGHTLY_ACTOR = StatusChangeActor.ofProcess("NightlyAccountCreditAllocation");

    private final StudentCurrentAccountRepository accountRepository;
    private final BillingOperationPipeline pipeline;
    private final AccountCreditAllocationOperation operation;
    private final BillingConfigurationReader configurationReader;
    private final HeldAccountLookup heldAccounts;

    public AccountCreditAllocationBillingRunStep(StudentCurrentAccountRepository accountRepository, BillingOperationPipeline pipeline,
                                                 AccountCreditAllocationOperation operation, BillingConfigurationReader configurationReader, HeldAccountLookup heldAccounts) {
        this.accountRepository = accountRepository;
        this.pipeline = pipeline;
        this.operation = operation;
        this.configurationReader = configurationReader;
        this.heldAccounts = heldAccounts;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.ACCOUNT_CREDIT_ALLOCATION;
    }

    @Override
    public Optional<String> findNotApplicableCode(LocalDate businessDate) {
        boolean autoApply = configurationReader.findAllocationSettingsEffectiveOn(businessDate).map(AllocationSettings::autoApplyCreditBalance).orElse(false);
        return autoApply ? Optional.empty() : Optional.of(AUTO_APPLY_CREDIT_DISABLED);
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        List<BillingRunItemRecord> failures = new ArrayList<>();
        int candidates = 0;
        Map<Long, String> held = heldAccounts.findHeldAccountCodes(businessDate);
        List<Long> page = accountRepository.findIdsWithUnallocatedCreditAfter(0L, PageRequest.of(0, PAGE_SIZE));
        while (!page.isEmpty()) {
            candidates += page.size();
            page.forEach(accountId -> allocateCreditUnlessHeld(accountId, businessDate, held, failures));
            page = accountRepository.findIdsWithUnallocatedCreditAfter(page.get(page.size() - 1), PageRequest.of(0, PAGE_SIZE));
        }
        return BillingRunStepOutcome.ofCandidates(candidates, failures);
    }

    private void allocateCreditUnlessHeld(Long accountId, LocalDate businessDate, Map<Long, String> held, List<BillingRunItemRecord> failures) {
        if (held.containsKey(accountId)) {
            failures.add(BillingRunItemRecord.skippedSubject(BillingRunSubjectType.CURRENT_ACCOUNT, accountId, held.get(accountId),
                    HeldSubjectFinder.messageOf(held.get(accountId))));
            return;
        }
        allocateCreditOfAccount(accountId, businessDate, failures);
    }

    private void allocateCreditOfAccount(Long accountId, LocalDate businessDate, List<BillingRunItemRecord> failures) {
        try {
            pipeline.runOperation(operation, new AccountCreditAllocationCommand(accountId, businessDate, NIGHTLY_ACTOR))
                    .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
        } catch (RuntimeException e) {
            log.error("Falló la imputación del saldo a favor: cuenta={}", accountId, e);
            failures.add(BillingRunItemRecord.failedSubject(BillingRunSubjectType.CURRENT_ACCOUNT, accountId, SUBJECT_PROCESSING_FAILED, e.getMessage()));
        }
    }
}

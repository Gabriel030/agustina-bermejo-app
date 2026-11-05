package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.domain.AccountConsistencyFinding;
import com.red_escuela.backend.billing.run.domain.AccountConsistencyScan;
import com.red_escuela.backend.billing.run.domain.BillingRunItemRecord;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import com.red_escuela.backend.billing.run.exception.GlobalAccountInconsistencyException;
import com.red_escuela.backend.billing.run.policy.InconsistencyStaysLocalContext;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AccountConsistencyBillingRunStep extends AbstractBillingRunStep {
    private static final Logger log = LoggerFactory.getLogger(AccountConsistencyBillingRunStep.class);

    private final AccountConsistencyScanner scanner;
    private final PolicySet<InconsistencyStaysLocalContext> scopePolicySet;
    private final InconsistentAccountAlerter alerter;

    public AccountConsistencyBillingRunStep(AccountConsistencyScanner scanner, PolicySet<InconsistencyStaysLocalContext> accountInconsistencyScopePolicySet,
                                            InconsistentAccountAlerter alerter) {
        this.scanner = scanner;
        this.scopePolicySet = accountInconsistencyScopePolicySet;
        this.alerter = alerter;
    }

    @Override
    public BillingRunStep step() {
        return BillingRunStep.ACCOUNT_CONSISTENCY;
    }

    @Override
    protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
        AccountConsistencyScan scan = scanner.scanAllAccounts(businessDate);
        if (!scopePolicySet.evaluateAllRules(InconsistencyStaysLocalContext.of(scan)).allowed()) {
            throw new GlobalAccountInconsistencyException(scan.inconsistentAccounts(), scan.totalAccounts());
        }
        scan.findings().forEach(finding -> log.warn("Cuenta inconsistente omitida: cuenta={}, codigo={}, detalle={}", finding.accountId(), finding.findingCode(), finding.detail()));
        alerter.alertDirectorOfInconsistentAccounts(scan.findings(), businessDate);
        return BillingRunStepOutcome.ofCandidates(scan.totalAccounts(), scan.findings().stream().map(AccountConsistencyBillingRunStep::skippedItemOf).toList());
    }

    private static BillingRunItemRecord skippedItemOf(AccountConsistencyFinding finding) {
        return BillingRunItemRecord.skippedSubject(BillingRunSubjectType.CURRENT_ACCOUNT, finding.accountId(), finding.findingCode(), finding.messageForDirector());
    }
}

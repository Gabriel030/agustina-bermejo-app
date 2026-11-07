package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.api.EnrollmentDebtGuard;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.invoice.exception.OutstandingDebtBlocksReenrollmentException;
import com.red_escuela.backend.billing.collections.exception.OverdueDebtBlocksNewEnrollmentException;
import com.red_escuela.backend.billing.collections.policy.NewEnrollmentDebtCheck;
import com.red_escuela.backend.billing.invoice.policy.OutstandingDebtFacts;
import com.red_escuela.backend.billing.invoice.service.InvoiceFollowUpQueries;
import com.red_escuela.backend.billing.invoice.service.WithdrawnEnrollmentDebtFactsLoader;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class StudentDebtEnrollmentGuard implements EnrollmentDebtGuard {

    private final WithdrawnEnrollmentDebtFactsLoader withdrawnDebtFacts;
    private final InvoiceFollowUpQueries invoiceFollowUp;
    private final BillingConfigurationReader configurationReader;
    private final CurrentBillingModePort billingMode;
    private final PolicySet<OutstandingDebtFacts> reenrollmentDebtPolicySet;
    private final PolicySet<NewEnrollmentDebtCheck> newEnrollmentDebtPolicySet;
    private final Clock clock;

    public StudentDebtEnrollmentGuard(WithdrawnEnrollmentDebtFactsLoader withdrawnDebtFacts, InvoiceFollowUpQueries invoiceFollowUp, BillingConfigurationReader configurationReader,
                                      CurrentBillingModePort billingMode, PolicySet<OutstandingDebtFacts> reenrollmentDebtPolicySet,
                                      PolicySet<NewEnrollmentDebtCheck> newEnrollmentDebtPolicySet, Clock clock) {
        this.withdrawnDebtFacts = withdrawnDebtFacts;
        this.invoiceFollowUp = invoiceFollowUp;
        this.configurationReader = configurationReader;
        this.billingMode = billingMode;
        this.reenrollmentDebtPolicySet = reenrollmentDebtPolicySet;
        this.newEnrollmentDebtPolicySet = newEnrollmentDebtPolicySet;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public void verifyNoOutstandingDebt(Long studentId) {
        if (!billingMode.currentMode().isEnabled()) {
            return;
        }
        reenrollmentDebtPolicySet.evaluateAllRules(withdrawnDebtFacts.loadFactsOf(studentId)).throwIfDenied(OutstandingDebtBlocksReenrollmentException::new);
        LocalDate today = LocalDate.now(clock);
        boolean blockWhenOverdue = configurationReader.findLatePaymentSettingsEffectiveOn(today).map(LatePaymentTerms::blockNewEnrollmentsWhenOverdue).orElse(true);
        NewEnrollmentDebtCheck check = new NewEnrollmentDebtCheck(studentId, blockWhenOverdue,
                blockWhenOverdue && invoiceFollowUp.hasUnpaidIssuedDocumentOverdueOfStudent(studentId, today));
        newEnrollmentDebtPolicySet.evaluateAllRules(check).throwIfDenied(OverdueDebtBlocksNewEnrollmentException::new);
    }
}

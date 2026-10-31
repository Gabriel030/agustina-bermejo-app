package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.payment.domain.CashPaymentPreview;
import com.red_escuela.backend.billing.payment.policy.CashPaymentCheck;
import com.red_escuela.backend.billing.payment.policy.CashPaymentRejections;
import com.red_escuela.backend.identity.api.StudentExistenceChecker;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Service;

@Service
public class CashPaymentPreviewService {

    private final PolicySet<CashPaymentCheck> previewRequestPolicies;
    private final StudentExistenceChecker students;
    private final StudentCurrentAccountService accountService;
    private final CashPaymentPlanner planner;

    public CashPaymentPreviewService(PolicySet<CashPaymentCheck> cashPaymentPreviewRequestPolicySet, StudentExistenceChecker students,
                                     StudentCurrentAccountService accountService, CashPaymentPlanner planner) {
        this.previewRequestPolicies = cashPaymentPreviewRequestPolicySet;
        this.students = students;
        this.accountService = accountService;
        this.planner = planner;
    }

    public CashPaymentPreview previewCashPaymentOfStudent(Long studentId, Money amount) {
        previewRequestPolicies.evaluateRulesUntilFirstViolation(CashPaymentCheck.ofAmountOnly(amount)).throwIfDenied(CashPaymentRejections::rejectionOf);
        students.requireExistingStudent(studentId);
        NonNegativeMoney requested = NonNegativeMoney.of(amount);
        return accountService.findStudentCurrentAccount(studentId, amount.currency())
                .map(account -> planner.previewCashPayment(studentId, amount.currency(), planner.findCandidatesOfAccount(account.getId()),
                        accountService.creditAvailableForAllocation(account), requested))
                .orElseGet(() -> planner.previewCashPayment(studentId, amount.currency(), CashPaymentPlanner.CashPaymentCandidates.none(),
                        NonNegativeMoney.zero(amount.currency()), requested));
    }
}

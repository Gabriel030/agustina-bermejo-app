package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.exception.OverdueDebtBlocksExamFeeException;
import com.red_escuela.backend.billing.collections.policy.ExamFeeIssuanceCheck;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class ExamFeeOverdueDebtGuard {

    private final StudentOverdueDebtFinder overdueDebtFinder;
    private final PolicySet<ExamFeeIssuanceCheck> examFeeIssuanceDebtPolicySet;
    private final Clock clock;

    public ExamFeeOverdueDebtGuard(StudentOverdueDebtFinder overdueDebtFinder, PolicySet<ExamFeeIssuanceCheck> examFeeIssuanceDebtPolicySet, Clock clock) {
        this.overdueDebtFinder = overdueDebtFinder;
        this.examFeeIssuanceDebtPolicySet = examFeeIssuanceDebtPolicySet;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void verifyExamFeeMayBeIssued(Long studentId, FeeType feeType) {
        if (feeType != FeeType.EXAM_FEE) {
            return;
        }
        ExamFeeIssuanceCheck check = new ExamFeeIssuanceCheck(studentId, overdueDebtFinder.hasOverdueDebt(studentId, LocalDate.now(clock)));
        examFeeIssuanceDebtPolicySet.evaluateAllRules(check).throwIfDenied(OverdueDebtBlocksExamFeeException::new);
    }
}

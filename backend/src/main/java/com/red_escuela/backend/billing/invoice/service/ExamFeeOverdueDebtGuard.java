package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.exception.OverdueDebtBlocksExamFeeException;
import com.red_escuela.backend.billing.invoice.policy.ExamFeeIssuanceCheck;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class ExamFeeOverdueDebtGuard {

    private final InvoiceFollowUpQueries invoiceFollowUp;
    private final PolicySet<ExamFeeIssuanceCheck> examFeeIssuanceDebtPolicySet;
    private final Clock clock;

    public ExamFeeOverdueDebtGuard(InvoiceFollowUpQueries invoiceFollowUp, PolicySet<ExamFeeIssuanceCheck> examFeeIssuanceDebtPolicySet, Clock clock) {
        this.invoiceFollowUp = invoiceFollowUp;
        this.examFeeIssuanceDebtPolicySet = examFeeIssuanceDebtPolicySet;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void verifyExamFeeMayBeIssued(Long studentId, FeeType feeType) {
        if (feeType != FeeType.EXAM_FEE) {
            return;
        }
        ExamFeeIssuanceCheck check = new ExamFeeIssuanceCheck(studentId,
                invoiceFollowUp.hasUnpaidIssuedDocumentOverdueOfStudent(studentId, LocalDate.now(clock)));
        examFeeIssuanceDebtPolicySet.evaluateAllRules(check).throwIfDenied(OverdueDebtBlocksExamFeeException::new);
    }
}

package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateRanking;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountContest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "DISCOUNT_OUTBID_BY_A_BETTER_ONE", decisions = {"D-073", "D-295", "D-301", "D-384", "D-385"})
public final class BestDiscountPerInvoiceLinePolicy implements Policy<DiscountContest> {

    private final DiscountCandidateRanking ranking = new DiscountCandidateRanking();

    @Override
    public String ruleName() {
        return "BestDiscountPerInvoiceLinePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(DiscountContest contest) {
        boolean outbid = contest.rivals().stream()
                .filter(rival -> competesWith(contest, rival))
                .anyMatch(rival -> ranking.ranksAbove(rival, contest.candidate()));
        return outbid
                ? PolicyVerdict.deny(ruleName(), "DISCOUNT_OUTBID_BY_A_BETTER_ONE", "Otro descuento es más favorable para este renglón de la factura")
                : PolicyVerdict.allow(ruleName());
    }

    private boolean competesWith(DiscountContest contest, InvoiceDiscountCandidate rival) {
        return rival.lineNumber() == contest.lineNumber() && rival.applicableInvoiceType() == contest.invoiceType();
    }
}

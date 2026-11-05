package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.configuration.policy.ConfigurationViolationExceptions;
import com.red_escuela.backend.billing.configuration.exception.ProposalAlreadyOpenException;
import com.red_escuela.backend.billing.configuration.exception.ProposalExpiredException;
import com.red_escuela.backend.billing.configuration.policy.EffectiveDateNotRetroactivePolicy;
import com.red_escuela.backend.billing.configuration.policy.ProposalApprovalCheck;
import com.red_escuela.backend.billing.pricing.exception.CurrencyMismatchException;
import com.red_escuela.backend.billing.pricing.exception.InvalidMarketingDiscountException;
import com.red_escuela.backend.billing.pricing.exception.InvalidPriceAmountException;
import com.red_escuela.backend.billing.pricing.exception.RejectionReasonRequiredException;
import com.red_escuela.backend.billing.pricing.policy.PriceChangeCheck;
import com.red_escuela.backend.billing.pricing.policy.PriceProposalSlot;
import com.red_escuela.backend.billing.pricing.policy.PriceRejectionCheck;
import com.red_escuela.backend.billing.pricing.policy.PriceVersionCancellationCheck;
import com.red_escuela.backend.billing.pricing.policy.PriceVersionDraft;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceVersionPolicyEvaluator {
    private static final String CURRENCY_MISMATCH = "CURRENCY_MISMATCH";
    private static final String AMOUNT_TOO_LARGE = "PRICE_AMOUNT_TOO_LARGE";

    private final PolicySet<PriceVersionDraft> priceVersionPolicySet;
    private final PolicySet<PriceChangeCheck> priceChangePolicySet;
    private final PolicySet<PriceVersionCancellationCheck> priceVersionCancellationPolicySet;
    private final PolicySet<PriceProposalSlot> priceProposalPolicySet;
    private final PolicySet<ProposalApprovalCheck> priceProposalApprovalPolicySet;
    private final PolicySet<PriceRejectionCheck> priceProposalRejectionPolicySet;

    public PriceVersionPolicyEvaluator(PolicySet<PriceVersionDraft> priceVersionPolicySet, PolicySet<PriceChangeCheck> priceChangePolicySet,
                                       PolicySet<PriceVersionCancellationCheck> priceVersionCancellationPolicySet,
                                       PolicySet<PriceProposalSlot> priceProposalPolicySet, PolicySet<ProposalApprovalCheck> priceProposalApprovalPolicySet,
                                       PolicySet<PriceRejectionCheck> priceProposalRejectionPolicySet) {
        this.priceVersionPolicySet = priceVersionPolicySet;
        this.priceChangePolicySet = priceChangePolicySet;
        this.priceVersionCancellationPolicySet = priceVersionCancellationPolicySet;
        this.priceProposalPolicySet = priceProposalPolicySet;
        this.priceProposalApprovalPolicySet = priceProposalApprovalPolicySet;
        this.priceProposalRejectionPolicySet = priceProposalRejectionPolicySet;
    }

    public void requireValidPriceVersion(PriceVersionDraft draft) {
        priceVersionPolicySet.evaluateAllRules(draft).throwIfDenied(PriceVersionPolicyEvaluator::exceptionFor);
    }

    public void requireValidPriceChange(PriceChangeCheck check) {
        priceChangePolicySet.evaluateAllRules(check).throwIfDenied(ConfigurationViolationExceptions::forChangeViolations);
    }

    public void requireNotRetroactiveStart(PriceChangeCheck check) {
        PolicyVerdict verdict = new EffectiveDateNotRetroactivePolicy<PriceChangeCheck>().evaluateRule(check);
        if (!verdict.allowed()) {
            throw ConfigurationViolationExceptions.forChangeViolations(List.of(new PolicyViolation(verdict.code(), verdict.reason())));
        }
    }

    public void requireCancellablePriceVersion(PriceVersionCancellationCheck check) {
        priceVersionCancellationPolicySet.evaluateAllRules(check).throwIfDenied(ConfigurationViolationExceptions::forCancellationViolations);
    }

    public void requireNoOpenProposal(PriceProposalSlot slot) {
        priceProposalPolicySet.evaluateAllRules(slot).throwIfDenied(ProposalAlreadyOpenException::new);
    }

    public List<PolicyViolation> collectOpenProposalViolations(PriceProposalSlot slot) {
        return priceProposalPolicySet.evaluateAllRules(slot).violations();
    }

    public void requireProposalNotExpired(ProposalApprovalCheck check) {
        priceProposalApprovalPolicySet.evaluateAllRules(check).throwIfDenied(ProposalExpiredException::new);
    }

    public void requireRejectionReason(PriceRejectionCheck check) {
        priceProposalRejectionPolicySet.evaluateAllRules(check).throwIfDenied(RejectionReasonRequiredException::new);
    }

    private static RuntimeException exceptionFor(List<PolicyViolation> violations) {
        if (violations.stream().anyMatch(violation -> CURRENCY_MISMATCH.equals(violation.code()))) {
            return new CurrencyMismatchException(violations);
        }
        if (violations.stream().anyMatch(violation -> AMOUNT_TOO_LARGE.equals(violation.code()))) {
            return new InvalidPriceAmountException(violations);
        }
        return new InvalidMarketingDiscountException(violations);
    }
}

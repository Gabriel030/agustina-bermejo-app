package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.pricing.policy.ChangePriceCommand;
import com.red_escuela.backend.billing.pricing.policy.PriceProposalSlot;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceChangeValidator {
    private final ChangePriceOperation operation;
    private final PriceRepository priceRepository;
    private final PriceVersionPolicyEvaluator policyEvaluator;

    public PriceChangeValidator(ChangePriceOperation operation, PriceRepository priceRepository, PriceVersionPolicyEvaluator policyEvaluator) {
        this.operation = operation;
        this.priceRepository = priceRepository;
        this.policyEvaluator = policyEvaluator;
    }

    public List<PolicyViolation> collectViolations(ChangePriceCommand command) {
        try {
            operation.requireValidContext(command);
            return command.requestedByDirector() ? List.of() : collectOpenProposalViolations(command);
        } catch (RuntimeException rejection) {
            if (rejection instanceof ReportsPolicyViolations reporting) {
                return reporting.violations();
            }
            throw rejection;
        }
    }

    private List<PolicyViolation> collectOpenProposalViolations(ChangePriceCommand command) {
        boolean openProposalExists = priceRepository.findById(command.priceId()).map(price -> price.findOpenProposal().isPresent()).orElse(false);
        return policyEvaluator.collectOpenProposalViolations(new PriceProposalSlot(command.priceId(), openProposalExists));
    }
}

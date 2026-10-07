package com.red_escuela.backend.billing.usecase.collectionspolicy;

import com.red_escuela.backend.billing.configuration.policy.LatePaymentSettingsValues;
import com.red_escuela.backend.billing.configuration.service.ConfigurationPolicyEvaluator;
import com.red_escuela.backend.billing.dto.CreateCollectionsPolicyRequest;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@UseCase
public class CreateCollectionsPolicyService implements CreateCollectionsPolicyUseCase {

    private final CollectionsPolicyRepository collectionsPolicyRepository;
    private final CurrentPersonService currentPersonService;
    private final ConfigurationPolicyEvaluator policyEvaluator;
    private final Clock clock;

    public CreateCollectionsPolicyService(CollectionsPolicyRepository collectionsPolicyRepository, CurrentPersonService currentPersonService,
                                          ConfigurationPolicyEvaluator policyEvaluator, Clock clock) {
        this.collectionsPolicyRepository = collectionsPolicyRepository;
        this.currentPersonService = currentPersonService;
        this.policyEvaluator = policyEvaluator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CollectionsPolicy create(CreateCollectionsPolicyRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveFrom = request.effectiveFrom() != null ? request.effectiveFrom() : today;
        policyEvaluator.requireCoherentLatePaymentSettings(new LatePaymentSettingsValues(
                request.gracePeriodDays(), request.surchargePercent(), request.suspensionAfterDays(),
                request.suspensionAfterInvoiceCount(), effectiveFrom, today));
        CollectionsPolicy policy = CollectionsPolicy.builder()
                .gracePeriodDays(request.gracePeriodDays())
                .surchargeRate(request.surchargePercent())
                .suspensionAfterDays(request.suspensionAfterDays())
                .suspensionAfterInvoiceCount(request.suspensionAfterInvoiceCount())
                .effectiveFrom(effectiveFrom)
                .createdBy(currentPersonService.resolveCurrentPerson())
                .build();
        return collectionsPolicyRepository.save(policy);
    }
}

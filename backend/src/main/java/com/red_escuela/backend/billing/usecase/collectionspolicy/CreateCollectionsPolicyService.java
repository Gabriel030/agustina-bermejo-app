package com.red_escuela.backend.billing.usecase.collectionspolicy;

import java.time.Clock;
import com.red_escuela.backend.billing.dto.CreateCollectionsPolicyRequest;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@UseCase
public class CreateCollectionsPolicyService implements CreateCollectionsPolicyUseCase {

    private final CollectionsPolicyRepository collectionsPolicyRepository;
    private final CurrentPersonService currentPersonService;
    private final Clock clock;

    public CreateCollectionsPolicyService(CollectionsPolicyRepository collectionsPolicyRepository, CurrentPersonService currentPersonService, Clock clock) {
        this.collectionsPolicyRepository = collectionsPolicyRepository;
        this.currentPersonService = currentPersonService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CollectionsPolicy create(CreateCollectionsPolicyRequest request) {
        CollectionsPolicy policy = new CollectionsPolicy();
        policy.setGracePeriodDays(request.gracePeriodDays());
        policy.setSurchargeRate(request.surchargePercent());
        policy.setSuspensionAfterDays(request.suspensionAfterDays());
        policy.setSuspensionAfterInvoiceCount(request.suspensionAfterInvoiceCount());
        policy.setWithdrawalAfterDays(request.withdrawalAfterDays());
        policy.setWithdrawalAfterInvoiceCount(request.withdrawalAfterInvoiceCount());
        policy.setEffectiveFrom(request.effectiveFrom() != null ? request.effectiveFrom() : LocalDate.now(clock));
        policy.setCreatedBy(currentPersonService.resolveCurrentPerson());
        return collectionsPolicyRepository.save(policy);
    }
}

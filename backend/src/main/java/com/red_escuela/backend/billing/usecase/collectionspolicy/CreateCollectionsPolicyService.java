package com.red_escuela.backend.billing.usecase.collectionspolicy;

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

    public CreateCollectionsPolicyService(CollectionsPolicyRepository collectionsPolicyRepository, CurrentPersonService currentPersonService) {
        this.collectionsPolicyRepository = collectionsPolicyRepository;
        this.currentPersonService = currentPersonService;
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
        policy.setEffectiveFrom(request.effectiveFrom() != null ? request.effectiveFrom() : LocalDate.now());
        policy.setCreatedBy(currentPersonService.resolveCurrentPerson());
        return collectionsPolicyRepository.save(policy);
    }
}

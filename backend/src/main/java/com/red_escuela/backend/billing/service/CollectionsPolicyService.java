package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreateCollectionsPolicyRequest;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.billing.exception.NoActiveCollectionsPolicyException;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import com.red_escuela.backend.billing.exception.NoActiveCollectionsPolicyException;
import com.red_escuela.backend.service.CurrentPersonService;

@Service
public class CollectionsPolicyService {

    private final CollectionsPolicyRepository collectionsPolicyRepository;
    private final CurrentPersonService currentPersonService;

    public CollectionsPolicyService(CollectionsPolicyRepository collectionsPolicyRepository, CurrentPersonService currentPersonService) {
        this.collectionsPolicyRepository = collectionsPolicyRepository;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public CollectionsPolicy create(CreateCollectionsPolicyRequest request) {
        CollectionsPolicy policy = new CollectionsPolicy();
        policy.setGracePeriodDays(request.gracePeriodDays());
        policy.setSurchargePercentage(request.surchargePercentage());
        policy.setSuspensionAfterDays(request.suspensionAfterDays());
        policy.setSuspensionAfterInvoiceCount(request.suspensionAfterInvoiceCount());
        policy.setWithdrawalAfterDays(request.withdrawalAfterDays());
        policy.setWithdrawalAfterInvoiceCount(request.withdrawalAfterInvoiceCount());
        policy.setEffectiveFrom(request.effectiveFrom() != null ? request.effectiveFrom() : LocalDate.now());
        policy.setCreatedBy(currentPersonService.resolveCurrentPerson());
        return collectionsPolicyRepository.save(policy);
    }

    public CollectionsPolicy findCurrent() {
        return collectionsPolicyRepository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(LocalDate.now())
                .orElseThrow(NoActiveCollectionsPolicyException::new);
    }
}

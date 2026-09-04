package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateCollectionsPolicyRequest;
import com.red_escuela.backend.entity.CollectionsPolicy;
import com.red_escuela.backend.exception.NoActiveCollectionsPolicyException;
import com.red_escuela.backend.repository.CollectionsPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
        return collectionsPolicyRepository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(LocalDate.now())
                .orElseThrow(NoActiveCollectionsPolicyException::new);
    }
}

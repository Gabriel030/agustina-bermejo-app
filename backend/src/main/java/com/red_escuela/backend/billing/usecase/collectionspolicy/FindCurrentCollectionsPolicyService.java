package com.red_escuela.backend.billing.usecase.collectionspolicy;

import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.billing.exception.NoActiveCollectionsPolicyException;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDate;

@UseCase
public class FindCurrentCollectionsPolicyService implements FindCurrentCollectionsPolicyUseCase {

    private final CollectionsPolicyRepository collectionsPolicyRepository;

    public FindCurrentCollectionsPolicyService(CollectionsPolicyRepository collectionsPolicyRepository) {
        this.collectionsPolicyRepository = collectionsPolicyRepository;
    }

    @Override
    public CollectionsPolicy findCurrent() {
        return collectionsPolicyRepository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(LocalDate.now())
                .orElseThrow(NoActiveCollectionsPolicyException::new);
    }
}

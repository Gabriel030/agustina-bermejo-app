package com.red_escuela.backend.billing.usecase.collectionspolicy;

import java.time.Clock;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.billing.exception.NoActiveCollectionsPolicyException;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDate;

@UseCase
public class FindCurrentCollectionsPolicyService implements FindCurrentCollectionsPolicyUseCase {

    private final CollectionsPolicyRepository collectionsPolicyRepository;
    private final Clock clock;

    public FindCurrentCollectionsPolicyService(CollectionsPolicyRepository collectionsPolicyRepository, Clock clock) {
        this.collectionsPolicyRepository = collectionsPolicyRepository;
        this.clock = clock;
    }

    @Override
    public CollectionsPolicy findCurrent() {
        return collectionsPolicyRepository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(LocalDate.now(clock))
                .orElseThrow(NoActiveCollectionsPolicyException::new);
    }
}

package com.red_escuela.backend.platform.infrastructure;

import com.red_escuela.backend.platform.domain.FeatureSnapshot;
import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.platform.entity.PlatformFeature;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.repository.PlatformFeatureRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

@Component
public class FeatureSnapshotLoader {

    private final PlatformFeatureRepository featureRepository;
    private final TransactionTemplate isolatedReadTransaction;
    private final Clock clock;

    public FeatureSnapshotLoader(PlatformFeatureRepository featureRepository, PlatformTransactionManager transactionManager, Clock clock) {
        this.featureRepository = featureRepository;
        this.isolatedReadTransaction = new TransactionTemplate(transactionManager);
        this.isolatedReadTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.isolatedReadTransaction.setReadOnly(true);
        this.clock = clock;
    }

    public FeatureSnapshot loadCommittedSnapshot() {
        return isolatedReadTransaction.execute(status -> {
            Map<FeatureCode, FeatureState> states = new EnumMap<>(FeatureCode.class);
            for (PlatformFeature feature : featureRepository.findAll()) {
                states.put(feature.getCode(), feature.toState());
            }
            return new FeatureSnapshot(states, clock.instant());
        });
    }
}

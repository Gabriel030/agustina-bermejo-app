package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.platform.domain.EffectiveModules;
import com.red_escuela.backend.platform.domain.FeatureSnapshot;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import org.springframework.stereotype.Service;

@Service
public class EffectiveModulesReader {

    private final FeatureSnapshotHolder snapshotHolder;

    public EffectiveModulesReader(FeatureSnapshotHolder snapshotHolder) {
        this.snapshotHolder = snapshotHolder;
    }

    public EffectiveModules readEffectiveModules() {
        FeatureSnapshot snapshot = snapshotHolder.current();
        return new EffectiveModules(snapshot.billingMode(), snapshot);
    }
}

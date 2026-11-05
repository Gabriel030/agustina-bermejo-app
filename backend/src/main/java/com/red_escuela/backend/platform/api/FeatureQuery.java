package com.red_escuela.backend.platform.api;

import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.model.FeatureCode;
import org.springframework.stereotype.Component;

@Component
public class FeatureQuery {

    private final FeatureSnapshotHolder snapshotHolder;

    public FeatureQuery(FeatureSnapshotHolder snapshotHolder) {
        this.snapshotHolder = snapshotHolder;
    }

    public boolean isEnabled(FeatureCode code) {
        return snapshotHolder.current().isEnabled(code);
    }
}

package com.red_escuela.backend.platform.infrastructure;

import com.red_escuela.backend.platform.domain.FeatureSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class FeatureSnapshotHolder {

    public static final Duration TIME_TO_LIVE = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(FeatureSnapshotHolder.class);

    private final FeatureSnapshotLoader loader;
    private final Clock clock;
    private final AtomicReference<FeatureSnapshot> current = new AtomicReference<>();

    public FeatureSnapshotHolder(FeatureSnapshotLoader loader, Clock clock) {
        this.loader = loader;
        this.clock = clock;
    }

    public FeatureSnapshot current() {
        FeatureSnapshot snapshot = current.get();
        if (snapshot != null && !snapshot.isOlderThan(TIME_TO_LIVE, clock.instant())) {
            return snapshot;
        }
        return refreshExpired();
    }

    public FeatureSnapshot refresh() {
        FeatureSnapshot loaded = loader.loadCommittedSnapshot();
        current.set(loaded);
        return loaded;
    }

    private synchronized FeatureSnapshot refreshExpired() {
        FeatureSnapshot snapshot = current.get();
        if (snapshot != null && !snapshot.isOlderThan(TIME_TO_LIVE, clock.instant())) {
            return snapshot;
        }
        try {
            return refresh();
        } catch (RuntimeException failure) {
            if (snapshot == null) {
                throw failure;
            }
            log.warn("feature_snapshot_refresh_failed keeping_age_seconds={}", Duration.between(snapshot.loadedAt(), clock.instant()).toSeconds(), failure);
            return snapshot;
        }
    }
}

package com.red_escuela.backend.platform.infrastructure;

import com.red_escuela.backend.platform.domain.InstituteSettingsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class InstituteSettingsSnapshotHolder {

    public static final Duration TIME_TO_LIVE = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(InstituteSettingsSnapshotHolder.class);

    private final InstituteSettingsSnapshotLoader loader;
    private final Clock clock;
    private final AtomicReference<InstituteSettingsSnapshot> current = new AtomicReference<>();

    public InstituteSettingsSnapshotHolder(InstituteSettingsSnapshotLoader loader, Clock clock) {
        this.loader = loader;
        this.clock = clock;
    }

    public InstituteSettingsSnapshot current() {
        InstituteSettingsSnapshot snapshot = current.get();
        if (snapshot != null && !snapshot.isOlderThan(TIME_TO_LIVE, clock.instant())) {
            return snapshot;
        }
        return refreshExpired();
    }

    public InstituteSettingsSnapshot refresh() {
        InstituteSettingsSnapshot loaded = loader.loadCommittedSnapshot();
        current.set(loaded);
        return loaded;
    }

    private synchronized InstituteSettingsSnapshot refreshExpired() {
        InstituteSettingsSnapshot snapshot = current.get();
        if (snapshot != null && !snapshot.isOlderThan(TIME_TO_LIVE, clock.instant())) {
            return snapshot;
        }
        try {
            return refresh();
        } catch (RuntimeException failure) {
            if (snapshot == null) {
                throw failure;
            }
            log.warn("institute_settings_snapshot_refresh_failed keeping_age_seconds={}", Duration.between(snapshot.loadedAt(), clock.instant()).toSeconds(), failure);
            return snapshot;
        }
    }
}

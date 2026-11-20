package com.red_escuela.backend.platform.domain;

import java.util.Optional;

public interface BackupStatusSource {
    Optional<BackupStatusSnapshot> read();
}

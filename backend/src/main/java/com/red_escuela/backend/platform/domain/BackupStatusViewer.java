package com.red_escuela.backend.platform.domain;

import java.util.Collection;

public enum BackupStatusViewer {
    DIRECTOR, PLATFORM_ADMIN;

    private static final String PLATFORM_ADMIN_AUTHORITY = "ROLE_PLATFORM_ADMIN";

    public static BackupStatusViewer fromAuthorities(Collection<String> authorities) {
        return authorities.contains(PLATFORM_ADMIN_AUTHORITY) ? PLATFORM_ADMIN : DIRECTOR;
    }

    public boolean seesTechnicalFields() {
        return this == PLATFORM_ADMIN;
    }
}

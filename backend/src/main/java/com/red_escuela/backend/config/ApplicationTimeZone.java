package com.red_escuela.backend.config;

import java.time.ZoneId;
import java.util.TimeZone;

public final class ApplicationTimeZone {

    public static final String DEFAULT_ZONE_ID = "America/Argentina/Buenos_Aires";
    private static final String ENV_VARIABLE = "APP_TIMEZONE";

    private ApplicationTimeZone() {
    }

    public static ZoneId resolve() {
        String configured = System.getenv(ENV_VARIABLE);
        return ZoneId.of(configured == null || configured.isBlank() ? DEFAULT_ZONE_ID : configured);
    }

    public static void applyAsJvmDefault() {
        TimeZone.setDefault(TimeZone.getTimeZone(resolve()));
    }
}

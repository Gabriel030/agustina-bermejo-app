package com.red_escuela.backend.config;

import java.time.ZoneId;

public class MisconfiguredTimeZoneException extends RuntimeException {

    public MisconfiguredTimeZoneException(ZoneId configured, ZoneId jvmDefault) {
        super("app.timezone=" + configured + " no coincide con la zona por defecto de la JVM ("
                + jvmDefault + "). Seteá APP_TIMEZONE, que es lo que se aplica antes de arrancar Spring.");
    }
}

package com.red_escuela.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;

@Configuration
public class TimeZoneConfig {

    private static final Logger log = LoggerFactory.getLogger(TimeZoneConfig.class);

    private final ZoneId configuredZoneId;

    public TimeZoneConfig(@Value("${app.timezone}") String timezone) {
        this.configuredZoneId = ZoneId.of(timezone);
    }

    public ZoneId getConfiguredZoneId() {
        return configuredZoneId;
    }

    @PostConstruct
    public void applyAsJvmDefault() {
        ZoneId previous = TimeZone.getDefault().toZoneId();
        TimeZone.setDefault(TimeZone.getTimeZone(configuredZoneId));
        if (!previous.equals(configuredZoneId)) {
            log.info("Zona horaria de la JVM corregida de {} a {}", previous, configuredZoneId);
        }
        log.info("Zona horaria de la aplicación: {} (offset actual {})",
                configuredZoneId, ZonedDateTime.now(configuredZoneId).getOffset());
    }
}

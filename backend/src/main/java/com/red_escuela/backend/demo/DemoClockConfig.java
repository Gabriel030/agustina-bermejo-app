package com.red_escuela.backend.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.ZoneId;

@Configuration
@Profile("demo")
class DemoClockConfig {

    @Bean
    @Primary
    DemoClock demoClock(@Value("${app.timezone}") String timezone) {
        return new DemoClock(ZoneId.of(timezone));
    }
}

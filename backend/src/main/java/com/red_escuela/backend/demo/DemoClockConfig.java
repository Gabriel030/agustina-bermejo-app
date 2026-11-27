package com.red_escuela.backend.demo;

import com.red_escuela.backend.shared.time.ApplicationTime;
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
        DemoClock clock = new DemoClock(ZoneId.of(timezone));
        ApplicationTime.useClock(clock);
        return clock;
    }
}

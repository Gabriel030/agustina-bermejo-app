package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlainScopeHandlersConfiguration {

    @Bean
    public ConfigurationScopeHandler cohortEntryScopeHandler() {
        return new PlainScopeHandler(ConfigurationScope.COHORT_ENTRY);
    }

    @Bean
    public ConfigurationScopeHandler allocationScopeHandler() {
        return new PlainScopeHandler(ConfigurationScope.ALLOCATION);
    }

    @Bean
    public ConfigurationScopeHandler courseTermsScopeHandler() {
        return new PlainScopeHandler(ConfigurationScope.COURSE_TERMS);
    }

    @Bean
    public ConfigurationScopeHandler irregularTermsScopeHandler() {
        return new PlainScopeHandler(ConfigurationScope.IRREGULAR_TERMS);
    }
}

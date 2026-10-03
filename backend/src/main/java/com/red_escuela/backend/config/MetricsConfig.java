package com.red_escuela.backend.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    static final String HTTP_SERVER_REQUESTS = "http.server.requests";
    static final String URI_TAG = "uri";
    static final int MAX_URI_TAGS = 300;

    @Bean
    public MeterFilter uriCardinalityCeiling() {
        return MeterFilter.maximumAllowableTags(HTTP_SERVER_REQUESTS, URI_TAG, MAX_URI_TAGS, MeterFilter.deny());
    }
}

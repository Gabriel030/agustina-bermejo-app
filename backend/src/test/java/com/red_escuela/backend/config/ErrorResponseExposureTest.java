package com.red_escuela.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseExposureTest {

    @Test
    void errorResponsesNeverExposeStackTracesOrExceptionMessagesEvenWithDevtoolsDefaults() throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

        assertThat(properties.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(properties.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(properties.getProperty("server.error.include-binding-errors")).isEqualTo("never");
    }
}

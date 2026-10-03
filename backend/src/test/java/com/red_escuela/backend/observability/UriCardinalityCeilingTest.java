package com.red_escuela.backend.observability;

import com.red_escuela.backend.config.MetricsConfig;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UriCardinalityCeilingTest {

    @Test
    void stopsRegisteringNewUrisOnceTheCeilingIsReached() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new MetricsConfig().uriCardinalityCeiling());

        for (int i = 0; i < 500; i++) {
            registry.timer("http.server.requests", Tags.of("uri", "/no-existe/" + i)).record(java.time.Duration.ofMillis(1));
        }

        long uriSeries = registry.getMeters().stream()
                .filter(meter -> meter.getId().getName().equals("http.server.requests"))
                .count();
        assertThat(uriSeries).isEqualTo(300);
    }

    @Test
    void doesNotAffectOtherMeterNames() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new MetricsConfig().uriCardinalityCeiling());

        for (int i = 0; i < 400; i++) {
            registry.counter("custom.metric", "uri", "/x/" + i).increment();
        }

        long otras = registry.getMeters().stream()
                .filter(meter -> meter.getId().getName().equals("custom.metric"))
                .count();
        assertThat(otras).isEqualTo(400);
    }
}

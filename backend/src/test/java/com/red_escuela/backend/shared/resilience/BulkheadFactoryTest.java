package com.red_escuela.backend.shared.resilience;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BulkheadFactoryTest {

    private MockEnvironment defaults() {
        return new MockEnvironment()
                .withProperty("resilience.default.enabled", "true")
                .withProperty("resilience.default.max-concurrent", "8")
                .withProperty("resilience.default.max-wait", "200ms");
    }

    private Bulkhead bulkheadFor(Environment environment, String name) {
        return new BulkheadFactory(environment, new SimpleMeterRegistry()).bulkheadNamed(name);
    }

    private int concurrencyLimit(Bulkhead bulkhead) {
        return bulkhead.availablePermits();
    }

    @Test
    void aBulkheadWithoutSpecificPropertiesUsesTheDefaults() {
        assertThat(concurrencyLimit(bulkheadFor(defaults(), "other"))).isEqualTo(8);
    }

    @Test
    void specificPropertiesOverrideTheDefaults() {
        MockEnvironment environment = defaults().withProperty("resilience.mail.max-concurrent", "2");

        assertThat(concurrencyLimit(bulkheadFor(environment, "mail"))).isEqualTo(2);
        assertThat(concurrencyLimit(bulkheadFor(environment, "google"))).isEqualTo(8);
    }

    @Test
    void theSameNameReturnsTheSameBulkhead() {
        BulkheadFactory factory = new BulkheadFactory(defaults(), new SimpleMeterRegistry());

        assertThat(factory.bulkheadNamed("mail")).isSameAs(factory.bulkheadNamed("mail"));
    }

    @Test
    void missingDefaultsFailFast() {
        assertThatThrownBy(() -> bulkheadFor(new MockEnvironment(), "mail")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shippedPropertiesDefineTheLimitsPerIntegration() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    Environment environment = context.getEnvironment();
                    assertThat(concurrencyLimit(bulkheadFor(environment, "mail"))).isEqualTo(4);
                    assertThat(concurrencyLimit(bulkheadFor(environment, "whatsapp-meta"))).isEqualTo(4);
                    assertThat(concurrencyLimit(bulkheadFor(environment, "mercadopago"))).isEqualTo(4);
                    assertThat(concurrencyLimit(bulkheadFor(environment, "google"))).isEqualTo(8);
                    assertThat(org.springframework.boot.context.properties.bind.Binder.get(environment)
                            .bind("resilience.default.max-wait", Duration.class).get())
                            .isEqualTo(Duration.ofMillis(200));
                });
    }

    @Test
    void resilienceIsDisabledByDefaultAndEnabledInProdAndStaging() {
        assertThat(enabledFor()).isEqualTo("false");
        assertThat(enabledFor("prod")).isEqualTo("true");
        assertThat(enabledFor("prod", "staging")).isEqualTo("true");
    }

    private String enabledFor(String... profiles) {
        String[] holder = new String[1];
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(profiles.length == 0 ? new String[0]
                        : new String[]{"spring.profiles.active=" + String.join(",", profiles)})
                .run(context -> holder[0] = context.getEnvironment().getProperty("resilience.default.enabled"));
        return holder[0];
    }
}

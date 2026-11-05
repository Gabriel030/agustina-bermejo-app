package com.red_escuela.backend.shared.resilience;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerFactoryTest {

    private MockEnvironment defaults() {
        return new MockEnvironment()
                .withProperty("resilience.default.enabled", "false")
                .withProperty("resilience.default.window", "20")
                .withProperty("resilience.default.failure-threshold", "10")
                .withProperty("resilience.default.wait-open", "30s");
    }

    @Test
    void aBreakerWithoutSpecificPropertiesUsesTheDefaultsAndIsDisabled() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(defaults(), Clock.systemUTC(), new SimpleMeterRegistry());

        CircuitBreaker breaker = factory.breakerNamed("mercadopago");

        for (int i = 0; i < 50; i++) {
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class);
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void specificPropertiesOverrideTheDefaults() {
        MockEnvironment environment = defaults()
                .withProperty("resilience.mercadopago.enabled", "true")
                .withProperty("resilience.mercadopago.window", "2")
                .withProperty("resilience.mercadopago.failure-threshold", "1");
        CircuitBreaker breaker = new CircuitBreakerFactory(environment, Clock.systemUTC(), new SimpleMeterRegistry()).breakerNamed("mercadopago");

        assertThatThrownBy(() -> breaker.execute(() -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    void theSameNameReturnsTheSameBreaker() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(defaults(), Clock.systemUTC(), new SimpleMeterRegistry());

        assertThat(factory.breakerNamed("mail")).isSameAs(factory.breakerNamed("mail"));
    }
}

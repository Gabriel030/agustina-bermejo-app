package com.red_escuela.backend.billing.simulation;

import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestContextManager;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("simulation")
@SlowTest(reason = "spike: jqwik properties executed against the Spring context and Postgres")
class SimulationJqwikSpikeProperties extends AbstractSimulationTest {

    @BeforeProperty
    void injectSpringBeans() throws Exception {
        new TestContextManager(getClass()).prepareTestInstance(this);
    }

    @AfterProperty
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    @Provide
    Arbitrary<Integer> paymentDayOffsets() {
        return Arbitraries.integers().between(0, 8);
    }

    @Property(tries = 3, seed = "20261107")
    void onTimeOrLatePaymentKeepsEveryInvariant(@ForAll("paymentDayOffsets") int offset) {
        Scenario scenario = scenario(Timeline.Mode.SPARSE).enrollStudent(LocalDate.of(2027, 3, 1));

        scenario.advanceTo(LocalDate.of(2027, 3, 1).plusDays(offset)).payEverythingDue().finish();

        assertThat(scenario.ledger().rows()).isNotEmpty();
    }
}

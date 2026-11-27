package com.red_escuela.backend.shared.time;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.demo.DemoClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "demo"})
class SingleClockAuthorityIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;
    @Autowired
    private DemoClock demoClock;

    @AfterEach
    void resetClock() {
        demoClock.backToPresent();
    }

    @Test
    void demoClockIsTheOnlyClockBean() {
        assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
        assertThat(context.getBean(Clock.class)).isSameAs(demoClock);
    }

    @Test
    void applicationTimeFollowsTheDemoClock() {
        demoClock.travelTo(LocalDate.of(2031, 3, 17));

        assertThat(ApplicationTime.today()).isEqualTo(LocalDate.of(2031, 3, 17));
        assertThat(ApplicationTime.instant()).isEqualTo(demoClock.instant());
    }
}

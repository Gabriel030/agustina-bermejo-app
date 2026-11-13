package com.red_escuela.backend;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SlowTest(reason = "Absorbs the one-time start-up of the shared Spring context of a test JVM so that no real class pays it")
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextWarmUpFirstForkIT extends AbstractIntegrationTest {

    @Test
    void sharedContextStarts() {
    }
}

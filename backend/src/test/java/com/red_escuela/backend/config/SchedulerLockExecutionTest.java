package com.red_escuela.backend.config;

import com.red_escuela.backend.enrollment.service.CourseEnrollmentWaitlistExpiryJob;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SchedulerLockExecutionTest extends AbstractIntegrationTest {

    private static final String START_MESSAGE = "Vencimiento de listas de espera iniciado";

    @Autowired
    private CourseEnrollmentWaitlistExpiryJob job;

    @Autowired
    private LockProvider lockProvider;

    @Test
    void aJobRunsWhenNobodyHoldsItsLock(CapturedOutput output) {
        job.expireWaitlists();

        assertThat(output.getOut()).contains(START_MESSAGE);
    }

    @Test
    void aJobIsSkippedWhileAnotherInstanceHoldsItsLock(CapturedOutput output) {
        Optional<SimpleLock> heldByOtherInstance = lockProvider.lock(new LockConfiguration(
                Instant.now(), "CourseEnrollmentWaitlistExpiryJob", Duration.ofMinutes(5), Duration.ZERO));
        assertThat(heldByOtherInstance).isPresent();
        try {
            job.expireWaitlists();
        } finally {
            heldByOtherInstance.get().unlock();
        }

        assertThat(output.getOut()).doesNotContain(START_MESSAGE);
    }
}

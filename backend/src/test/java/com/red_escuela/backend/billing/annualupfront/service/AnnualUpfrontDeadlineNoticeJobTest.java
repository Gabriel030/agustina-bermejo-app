package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("D-349")
class AnnualUpfrontDeadlineNoticeJobTest {

    @Test
    void theJobRunsOnlyWithBillingInFullModeAndUnderAClusterLock() throws Exception {
        Method job = AnnualUpfrontDeadlineNoticeJob.class.getMethod("planDeadlineNotices");

        RequiresModule requirement = job.getAnnotation(RequiresModule.class);
        assertThat(requirement).isNotNull();
        assertThat(requirement.value()).isEqualTo(RequiresModule.BILLING);
        assertThat(requirement.minMode()).isEqualTo(BillingMode.FULL);
        assertThat(job.getAnnotation(Scheduled.class)).isNotNull();
        assertThat(job.getAnnotation(SchedulerLock.class)).isNotNull();
    }

    @Test
    void theJobDelegatesToThePlanner() {
        AnnualUpfrontDeadlineNoticePlanner planner = mock(AnnualUpfrontDeadlineNoticePlanner.class);

        new AnnualUpfrontDeadlineNoticeJob(planner).planDeadlineNotices();

        verify(planner).planDeadlineNotices();
    }
}

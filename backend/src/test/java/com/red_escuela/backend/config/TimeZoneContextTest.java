package com.red_escuela.backend.config;

import com.red_escuela.backend.billing.service.MonthlyInstallmentIssuanceJob;
import com.red_escuela.backend.shared.time.ClockConfig;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class TimeZoneContextTest extends AbstractIntegrationTest {

    @Autowired
    private TimeZoneConfig timeZoneConfig;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Clock clock;

    @MockitoSpyBean
    private MonthlyInstallmentIssuanceJob monthlyJob;

    @Test
    void theLoadedContextAppliesTheConfiguredZoneWithoutGoingThroughMain() {
        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(timeZoneConfig.getConfiguredZoneId());
    }

    @Test
    void localDateTimeNowFollowsTheConfiguredZoneEvenOnAnUtcRunner() {
        assertThat(LocalDateTime.now())
                .isCloseTo(LocalDateTime.now(timeZoneConfig.getConfiguredZoneId()), within(5, SECONDS));
    }

    @Test
    void everyScheduledMethodDeclaresTheApplicationZone() {
        List<String> found = new ArrayList<>();
        List<String> withoutZone = new ArrayList<>();

        for (String beanName : context.getBeanDefinitionNames()) {
            if (!context.getAutowireCapableBeanFactory().containsBean(beanName)) {
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(context.getBean(beanName));
            for (Method method : targetClass.getDeclaredMethods()) {
                Scheduled scheduled = method.getAnnotation(Scheduled.class);
                if (scheduled == null) {
                    continue;
                }
                String where = targetClass.getSimpleName() + "." + method.getName();
                found.add(where);
                if (scheduled.zone().isBlank()) {
                    withoutZone.add(where);
                }
            }
        }

        assertThat(found).hasSizeGreaterThanOrEqualTo(5);
        assertThat(withoutZone).isEmpty();
    }

    @Test
    void clockBeanUsesTheApplicationZone() {
        Clock productionClock = ClockConfig.systemClock(timeZoneConfig.getConfiguredZoneId().getId());

        assertThat(productionClock.getZone()).isEqualTo(timeZoneConfig.getConfiguredZoneId());
    }

    @Test
    void clockAndSchedulerAgreeOnTheZone() {
        List<String> disagreements = new ArrayList<>();
        int checked = 0;

        for (String beanName : context.getBeanDefinitionNames()) {
            if (!context.getAutowireCapableBeanFactory().containsBean(beanName)) {
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(context.getBean(beanName));
            for (Method method : targetClass.getDeclaredMethods()) {
                Scheduled scheduled = method.getAnnotation(Scheduled.class);
                if (scheduled == null) {
                    continue;
                }
                checked++;
                ZoneId schedulerZone = ZoneId.of(context.getEnvironment().resolvePlaceholders(scheduled.zone()));
                if (!schedulerZone.equals(clock.getZone())) {
                    disagreements.add(targetClass.getSimpleName() + "." + method.getName() + " -> " + schedulerZone);
                }
            }
        }

        assertThat(checked).isGreaterThanOrEqualTo(5);
        assertThat(disagreements).isEmpty();
    }

    @Test
    void monthlyJobUsesTheClockNotTheJvmZone() {
        doNothing().when(monthlyJob).run(any(YearMonth.class));
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            MutableClock.INSTANCE.setInstant(Instant.parse("2027-03-01T02:30:00Z"));
            monthlyJob.run();
            verify(monthlyJob, never()).run(any(YearMonth.class));

            MutableClock.INSTANCE.setInstant(Instant.parse("2027-03-01T07:00:00Z"));
            monthlyJob.run();
            verify(monthlyJob).run(YearMonth.of(2027, 3));
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}

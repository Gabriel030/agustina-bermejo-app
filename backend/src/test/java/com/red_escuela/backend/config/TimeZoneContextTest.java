package com.red_escuela.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
class TimeZoneContextTest extends AbstractIntegrationTest {

    @Autowired
    private TimeZoneConfig timeZoneConfig;

    @Autowired
    private ApplicationContext context;

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
}

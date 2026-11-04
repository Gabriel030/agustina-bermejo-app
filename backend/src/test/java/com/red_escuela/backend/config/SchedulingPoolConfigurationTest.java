package com.red_escuela.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingPoolConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(Scheduling.class);

    @Test
    void theSchedulerPoolHasMoreThanOneThreadByDefault() {
        runner.run(context -> {
            ThreadPoolTaskScheduler scheduler = context.getBean(ThreadPoolTaskScheduler.class);
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(4);
        });
    }

    @Test
    void theSchedulerPoolSizeIsOverridableByProperty() {
        runner.withPropertyValues("spring.task.scheduling.pool.size=6").run(context -> {
            ThreadPoolTaskScheduler scheduler = context.getBean(ThreadPoolTaskScheduler.class);
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(6);
        });
    }

    @Configuration
    @EnableScheduling
    static class Scheduling {
    }
}

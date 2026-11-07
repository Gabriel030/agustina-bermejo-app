package com.red_escuela.backend.demo;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DemoProfileAbsentIT extends AbstractIntegrationTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void demoClockIsNotLoadedWithoutDemoProfile() {
        assertThat(context.getBeansOfType(DemoClock.class)).isEmpty();
        assertThat(context.getBeansOfType(Clock.class).values()).noneMatch(DemoClock.class::isInstance);
    }

    @Test
    void noDemoBeanIsLoadedWithoutDemoProfile() {
        String demoPackage = DemoClock.class.getPackageName();
        String[] beanNames = ((ConfigurableApplicationContext) context).getBeanFactory().getBeanDefinitionNames();

        assertThat(Arrays.stream(beanNames)
                .map(context::getType)
                .filter(type -> type != null && type.getPackageName().startsWith(demoPackage)))
                .isEmpty();
    }
}

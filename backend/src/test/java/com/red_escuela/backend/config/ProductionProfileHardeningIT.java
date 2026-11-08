package com.red_escuela.backend.config;

import com.red_escuela.backend.identity.specialist.DemoLearnerAccountsSeeder;
import com.red_escuela.backend.identity.specialist.LocalSeedRunner;
import com.red_escuela.backend.notification.delivery.controller.SimulatedEmailController;
import com.red_escuela.backend.testsupport.SharedPropertyOverrides;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@ActiveProfiles("test")
@SharedPropertyOverrides
class ProductionProfileHardeningIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Test
    void seedRunnersAreNotRegisteredWithoutLocalProfile() {
        assertThat(applicationContext.getBeanNamesForType(LocalSeedRunner.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(DemoLearnerAccountsSeeder.class)).isEmpty();
    }

    @Test
    void swaggerAndApiDocsAreDisabledByDefault() throws Exception {
        Properties defaults = new Properties();
        try (var input = new ClassPathResource("application.properties").getInputStream()) {
            defaults.load(input);
        }
        assertThat(defaults.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(defaults.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");

        assertThat(environment.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(status("/v3/api-docs")).isIn(401, 403, 404);
        assertThat(status("/swagger-ui.html")).isIn(401, 403, 404);
    }

    @Test
    void actuatorExposesOnlyHealthWithoutCredentials() throws Exception {
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,metrics,prometheus");
        assertThat(status("/actuator/health")).isEqualTo(200);
        assertThat(status("/actuator/prometheus")).isEqualTo(401);
        assertThat(status("/actuator/metrics")).isEqualTo(401);
        assertThat(status("/actuator/env")).isIn(401, 404);
        assertThat(status("/actuator/heapdump")).isIn(401, 404);
        int envWithCredentials = mockMvc.perform(get("/actuator/env").with(httpBasic("scraper", "local-test")))
                .andReturn().getResponse().getStatus();
        int heapdumpWithCredentials = mockMvc.perform(get("/actuator/heapdump").with(httpBasic("scraper", "local-test")))
                .andReturn().getResponse().getStatus();
        assertThat(envWithCredentials).isIn(401, 403, 404);
        assertThat(heapdumpWithCredentials).isIn(401, 403, 404);
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    void simulatedEmailEndpointDoesNotExistWithoutLocalOrDemo() throws Exception {
        assertThat(applicationContext.getBeanNamesForType(SimulatedEmailController.class)).isEmpty();
        assertThat(status("/notification-deliveries/simulated")).isEqualTo(404);
    }

    private int status(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
    }
}

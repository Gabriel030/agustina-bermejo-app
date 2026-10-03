package com.red_escuela.backend.observability;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "management.security.user=scraper",
        "management.security.password=local-test"
})
class ActuatorEndpointsTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublicAndHidesComponentDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void prometheusRequiresCredentials() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prometheusWithCredentialsExposesTheExpectedMeterFamilies() throws Exception {
        String body = mockMvc.perform(get("/actuator/prometheus").with(httpBasic("scraper", "local-test")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .contains("jvm_memory_used_bytes")
                .contains("hikaricp_connections_active");
    }

    @Test
    void requestsAreCountedByTemplatedUriNotByConcreteId() throws Exception {
        mockMvc.perform(get("/students/1/program-enrollments"));
        mockMvc.perform(get("/students/2/program-enrollments"));

        String body = mockMvc.perform(get("/actuator/prometheus").with(httpBasic("scraper", "local-test")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .contains("uri=\"/students/{id}/program-enrollments\"")
                .doesNotContain("uri=\"/students/1/program-enrollments\"")
                .doesNotContain("uri=\"/students/2/program-enrollments\"");
    }
}

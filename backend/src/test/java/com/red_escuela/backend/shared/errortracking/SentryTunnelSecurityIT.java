package com.red_escuela.backend.shared.errortracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@ActiveProfiles("test")
class SentryTunnelSecurityIT extends AbstractIntegrationTest {

    private static final String PATH = "/public/monitoring/tunnel";

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.rate-limit.refresh.capacity:30}")
    private int capacity;

    @Test
    void anonymousPostReachesTheControllerAndAnswersNotFoundWhileTheDsnIsEmpty() throws Exception {
        mockMvc.perform(post(PATH).with(request -> {
            request.setRemoteAddr("198.51.100.201");
            return request;
        }).content("x")).andExpect(status().isNotFound());
    }

    @Test
    void otherMethodsAndSiblingPathsStayProtected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH + "/other").content("x")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/public/monitoring").content("x")).andExpect(status().isUnauthorized());
    }

    @Test
    void exhaustingTheBucketAnswersTooManyRequestsWithRetryAfterAndOnlyForThatIp() throws Exception {
        for (int attempt = 0; attempt < capacity; attempt++) {
            mockMvc.perform(post(PATH).with(request -> {
                request.setRemoteAddr("198.51.100.202");
                return request;
            }).content("x")).andExpect(status().isNotFound());
        }

        MvcResult limited = mockMvc.perform(post(PATH).with(request -> {
            request.setRemoteAddr("198.51.100.202");
            return request;
        }).content("x")).andExpect(status().isTooManyRequests()).andReturn();

        assertThat(Long.parseLong(limited.getResponse().getHeader("Retry-After"))).isPositive();
        mockMvc.perform(post(PATH).with(request -> {
            request.setRemoteAddr("198.51.100.203");
            return request;
        }).content("x")).andExpect(status().isNotFound());
    }
}

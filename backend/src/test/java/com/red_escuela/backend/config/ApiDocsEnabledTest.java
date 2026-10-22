package com.red_escuela.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ApiDocsEnabledTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsArePublicOnlyWhenPropertyEnabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

        int protectedStatus = mockMvc.perform(get("/programs")).andReturn().getResponse().getStatus();
        int writeStatus = mockMvc.perform(post("/v3/api-docs")).andReturn().getResponse().getStatus();

        assertThat(protectedStatus).isIn(401, 403);
        assertThat(writeStatus).isIn(401, 403);
    }
}

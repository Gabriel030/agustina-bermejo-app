package com.red_escuela.backend.config;

import com.red_escuela.backend.testsupport.SharedPropertyOverrides;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@ActiveProfiles("test")
@SharedPropertyOverrides
class ApiDocsDisabledTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsAreNotExposedWithoutTheProperty() throws Exception {
        int apiDocsStatus = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getStatus();
        int swaggerStatus = mockMvc.perform(get("/swagger-ui.html")).andReturn().getResponse().getStatus();
        int swaggerIndexStatus = mockMvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse().getStatus();

        assertThat(apiDocsStatus).isIn(401, 403, 404);
        assertThat(swaggerStatus).isIn(401, 403, 404);
        assertThat(swaggerIndexStatus).isIn(401, 403, 404);
    }
}

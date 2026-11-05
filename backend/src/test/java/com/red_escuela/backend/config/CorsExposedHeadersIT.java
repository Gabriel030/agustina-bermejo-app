package com.red_escuela.backend.config;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsExposedHeadersIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void crossOriginResponsesExposeTheHeadersTheFrontendReads() throws Exception {
        mockMvc.perform(get("/institutions").header("Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Expose-Headers", Matchers.containsString("Retry-After")))
                .andExpect(header().string("Access-Control-Expose-Headers", Matchers.containsString("X-Request-Id")))
                .andExpect(header().string("Access-Control-Expose-Headers", Matchers.containsString("Content-Disposition")))
                .andExpect(header().string("Access-Control-Expose-Headers", Matchers.containsString("X-Total-Count")));
    }
}

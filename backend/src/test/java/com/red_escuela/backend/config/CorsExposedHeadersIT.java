package com.red_escuela.backend.config;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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

    @Test
    void preflightFromAllowedOriginAcceptsIfMatchHeader() throws Exception {
        mockMvc.perform(options("/institute-settings")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "if-match"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", Matchers.containsStringIgnoringCase("If-Match")))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void preflightFromAllowedOriginIsCachedByTheBrowserForOneHour() throws Exception {
        mockMvc.perform(options("/institute-settings")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "if-match"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    void preflightFromDisallowedOriginIsRejected() throws Exception {
        mockMvc.perform(options("/institute-settings")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "if-match"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}

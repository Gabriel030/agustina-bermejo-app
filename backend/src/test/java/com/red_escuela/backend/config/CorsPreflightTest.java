package com.red_escuela.backend.config;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsPreflightTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightForPatchIsAllowedFromTheFrontendOrigin() throws Exception {
        mockMvc.perform(options("/year-billing-templates/1/configuration")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", Matchers.containsString("PATCH")));
    }

    @Test
    void preflightAllowsTheIdempotencyKeyHeaderForTheTransferReceiptUpload() throws Exception {
        mockMvc.perform(options("/me/bank-transfer-receipts")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Headers", Matchers.containsStringIgnoringCase("Idempotency-Key")));
    }

    @Test
    void preflightKeepsRejectingHeadersOutsideTheAllowList() throws Exception {
        mockMvc.perform(options("/me/bank-transfer-receipts")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-forwarded-host"))
                .andExpect(status().isForbidden());
    }

    @Test
    void preflightFromAnUnlistedOriginIsRejectedEvenWithTheIdempotencyKeyHeader() throws Exception {
        mockMvc.perform(options("/me/bank-transfer-receipts")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "idempotency-key"))
                .andExpect(status().isForbidden());
    }
}

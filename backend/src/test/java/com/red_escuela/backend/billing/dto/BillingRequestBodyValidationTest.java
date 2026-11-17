package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.annualupfront.dto.RequestAnnualUpfrontBody;
import com.red_escuela.backend.shared.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingRequestBodyValidationTest {

    @RestController
    static class EchoController {
        @PostMapping("/echo/annual-upfront")
        void annualUpfront(@Valid @RequestBody RequestAnnualUpfrontBody body) {
        }

        @PostMapping("/echo/gateway-config")
        void gatewayConfig(@Valid @RequestBody UpdatePaymentGatewayConfigRequest body) {
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EchoController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private static Stream<Arguments> limits() {
        return Stream.of(
                Arguments.of("/echo/annual-upfront", "{\"termsAccepted\":true,\"termsVersion\":\"%s\"}", 20),
                Arguments.of("/echo/gateway-config", "{\"bankAlias\":\"%s\"}", 50),
                Arguments.of("/echo/gateway-config", "{\"bankCbu\":\"%s\"}", 22),
                Arguments.of("/echo/gateway-config", "{\"accountHolder\":\"%s\"}", 100));
    }

    @ParameterizedTest
    @MethodSource("limits")
    void valueOneAboveTheColumnLimitIsRejectedWithBadRequest(String path, String template, int limit) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(template.formatted("x".repeat(limit + 1))))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @MethodSource("limits")
    void valueAtTheColumnLimitIsAccepted(String path, String template, int limit) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(template.formatted("x".repeat(limit))))
                .andExpect(status().isOk());
    }
}

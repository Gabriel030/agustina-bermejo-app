package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.annualupfront.dto.RequestAnnualUpfrontBody;
import com.red_escuela.backend.billing.discount.dto.CloseDiscountDefinitionRequest;
import com.red_escuela.backend.billing.discount.dto.CreateDiscountDefinitionRequest;
import com.red_escuela.backend.billing.discount.dto.RevokeStudentDiscountRequest;
import com.red_escuela.backend.billing.payment.dto.ApproveBankTransferReceiptRequest;
import com.red_escuela.backend.shared.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
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

    private static final String LONG_TEXT = "x".repeat(256);
    private static final String LONG_REASON = "x".repeat(501);

    @RestController
    static class EchoController {
        @PostMapping("/echo/annual-upfront")
        void annualUpfront(@Valid @RequestBody RequestAnnualUpfrontBody body) {
        }

        @PostMapping("/echo/create-discount")
        void createDiscount(@Valid @RequestBody CreateDiscountDefinitionRequest body) {
        }

        @PostMapping("/echo/close-discount")
        void closeDiscount(@Valid @RequestBody CloseDiscountDefinitionRequest body) {
        }

        @PostMapping("/echo/revoke-discount")
        void revokeDiscount(@Valid @RequestBody RevokeStudentDiscountRequest body) {
        }

        @PostMapping("/echo/gateway-config")
        void gatewayConfig(@Valid @RequestBody UpdatePaymentGatewayConfigRequest body) {
        }

        @PostMapping("/echo/approve-receipt")
        void approveReceipt(@Valid @RequestBody ApproveBankTransferReceiptRequest body) {
        }

        @PostMapping("/echo/reject-receipt")
        void rejectReceipt(@Valid @RequestBody RejectPaymentReceiptRequest body) {
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EchoController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private static Stream<Arguments> oversizedBodies() {
        return Stream.of(
                Arguments.of("/echo/annual-upfront", "{\"termsAccepted\":true,\"termsVersion\":\"" + "x".repeat(51) + "\"}"),
                Arguments.of("/echo/create-discount", "{\"name\":\"" + LONG_TEXT + "\"}"),
                Arguments.of("/echo/close-discount", "{\"reason\":\"" + LONG_REASON + "\"}"),
                Arguments.of("/echo/revoke-discount", "{\"reason\":\"" + LONG_REASON + "\"}"),
                Arguments.of("/echo/gateway-config", "{\"bankAlias\":\"" + LONG_TEXT + "\"}"),
                Arguments.of("/echo/gateway-config", "{\"bankCbu\":\"" + LONG_TEXT + "\"}"),
                Arguments.of("/echo/gateway-config", "{\"accountHolder\":\"" + LONG_TEXT + "\"}"),
                Arguments.of("/echo/approve-receipt", "{\"reviewNote\":\"" + LONG_REASON + "\"}"),
                Arguments.of("/echo/reject-receipt", "{\"rejectionReason\":\"" + LONG_REASON + "\"}"));
    }

    @ParameterizedTest
    @MethodSource("oversizedBodies")
    void oversizedBillingBodyIsRejectedWithBadRequest(String path, String body) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void closeReasonAtTheLimitIsAccepted() throws Exception {
        mockMvc.perform(post("/echo/close-discount").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(500) + "\"}"))
                .andExpect(status().isOk());
    }
}

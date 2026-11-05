package com.red_escuela.backend.shared.exception;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    static class BrokenLinkException extends DataIntegrityBrokenException {
        BrokenLinkException() {
            super("Payment 404 referenced by an allocation source does not exist");
        }
    }

    static class RateLimitedException extends TooManyRequestsException {
        RateLimitedException() {
            super("too many");
        }

        @Override
        public java.time.Duration retryAfter() {
            return java.time.Duration.ofSeconds(45);
        }
    }

    static class GatewayDownException extends ExternalServiceException {
        GatewayDownException() {
            super("jdbc:postgresql://internal-host/secret failed");
        }
    }

    static class BusinessConflictException extends ConflictException {
        BusinessConflictException() {
            super("conflict");
        }
    }

    static class ForgedConflictException extends ConflictException {
        ForgedConflictException() {
            super("bad value\nWARN forged entry");
        }
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/rate-limited")
        void rateLimited() {
            throw new RateLimitedException();
        }

        @GetMapping("/gateway-down")
        void gatewayDown() {
            throw new GatewayDownException();
        }

        @GetMapping("/circuit-open")
        void circuitOpen() {
            throw new CircuitOpenException("mercadopago", java.time.Duration.ofSeconds(12));
        }

        @GetMapping("/business-conflict")
        void businessConflict() {
            throw new BusinessConflictException();
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new RuntimeException("select * from secret_table where id = 7");
        }

        @GetMapping("/denied")
        void denied() {
            throw new org.springframework.security.access.AccessDeniedException("denied");
        }

        @GetMapping("/broken-integrity")
        void brokenIntegrity() {
            throw new BrokenLinkException();
        }

        @GetMapping("/illegal-state")
        void illegalState() {
            throw new IllegalStateException("Invoice 7 en estado PAID no puede cancelarse");
        }

        @GetMapping("/data-integrity")
        void dataIntegrity() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint uk_person_email");
        }

        @GetMapping("/forged-conflict")
        void forgedConflict() {
            throw new ForgedConflictException();
        }

        @GetMapping("/optimistic-lock")
        void optimisticLock() {
            throw new ObjectOptimisticLockingFailureException("Invoice", 7L);
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void aBrokenDataIntegrityIsAServerErrorLoggedAtErrorLevel(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/broken-integrity")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(content().string(not(containsString("Payment 404"))));

        assertThat(output).contains("ERROR").contains("Payment 404 referenced by an allocation source does not exist");
    }

    @Test
    void anIllegalStateIsAConflictWithItsCodeInsteadOfAServerError(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));

        assertThat(output).contains("Invoice 7 en estado PAID no puede cancelarse");
    }

    @Test
    void aDataIntegrityViolationIsLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/data-integrity")).andExpect(status().isConflict());

        assertThat(output).contains("uk_person_email");
    }

    @Test
    void lineBreaksInAnExceptionMessageCannotForgeALogLine(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/forged-conflict")).andExpect(status().isConflict());

        assertThat(output).contains("bad value_WARN forged entry").doesNotContain("\nWARN forged entry");
    }

    @Test
    void anOptimisticLockingFailureIsLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/optimistic-lock")).andExpect(status().isConflict());

        assertThat(output).contains("ObjectOptimisticLockingFailureException");
    }

    @Test
    void aRateLimitAnswersWithRetryAfterAndARetryableBody() throws Exception {
        mockMvc.perform(get("/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "45"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void anExternalServiceFailureIsARetryableBadGatewayWithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/gateway-down"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("GATEWAY_DOWN"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(content().string(not(containsString("jdbc"))));
    }

    @Test
    void anOpenCircuitIsARetryableServiceUnavailableWithRetryAfter() throws Exception {
        mockMvc.perform(get("/circuit-open"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "12"))
                .andExpect(jsonPath("$.code").value("EXTERNAL_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void aSubSecondRetryAfterIsRoundedUpToOneSecond() throws Exception {
        assertThat(Math.max(1, new CircuitOpenException("x", java.time.Duration.ofMillis(300)).retryAfter().toSeconds())).isEqualTo(1);
    }

    @Test
    void aBusinessConflictIsNotRetryable() throws Exception {
        mockMvc.perform(get("/business-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void anUnexpectedFailureIsAGenericNonRetryableServerErrorThatLeaksNothing(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(content().string(not(containsString("secret_table"))));

        assertThat(output).contains("secret_table");
    }

    @Test
    void anAccessDeniedFailureIsLeftToTheSecurityFilterChain() {
        assertThatThrownBy(() -> mockMvc.perform(get("/denied")))
                .hasRootCauseInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void aDataIntegrityViolationBodyDoesNotLeakTheConstraint() throws Exception {
        mockMvc.perform(get("/data-integrity"))
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(content().string(not(containsString("uk_person_email"))));
    }

    private MockMvc countingMockMvc(SimpleMeterRegistry registry) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        handler.setApiErrorCounter(new ApiErrorCounter(registry));
        return MockMvcBuilders.standaloneSetup(new ThrowingController()).setControllerAdvice(handler).build();
    }

    @Test
    void unexpectedExceptionIncrementsTheApiErrorCounterWithStatus500() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        countingMockMvc(registry).perform(get("/unexpected")).andExpect(status().isInternalServerError());

        assertThat(registry.counter("api.errors", "status", "500", "code", "INTERNAL_ERROR").count()).isEqualTo(1.0);
    }

    @Test
    void illegalStateIsCountedAs409WithItsCode() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        countingMockMvc(registry).perform(get("/illegal-state")).andExpect(status().isConflict());

        assertThat(registry.counter("api.errors", "status", "409", "code", "ILLEGAL_STATE").count()).isEqualTo(1.0);
    }

    @Test
    void aHandlerWithoutCounterStillAnswersNormally() throws Exception {
        mockMvc.perform(get("/illegal-state")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
    }

    @Test
    void clientErrorsOutsideTheSuspiciousFamiliesAreNotCounted() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        countingMockMvc(registry).perform(get("/business-conflict")).andExpect(status().isConflict());

        assertThat(registry.getMeters()).isEmpty();
    }
}

package com.red_escuela.backend.shared.exception;

import com.red_escuela.backend.shared.errortracking.SentryEventScrubber;
import com.red_escuela.backend.shared.errortracking.SentryEventThrottle;
import com.red_escuela.backend.shared.errortracking.SentryServerErrorReporter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.sentry.JsonSerializer;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.protocol.User;
import java.io.IOException;
import java.io.StringWriter;
import io.sentry.SentryEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerSentryTest {

    static class BrokenLinkException extends DataIntegrityBrokenException {
        BrokenLinkException() {
            super("Payment 40123456 referenced by an allocation does not exist");
        }
    }

    static class GatewayDownException extends ExternalServiceException {
        GatewayDownException() {
            super("gateway failed for ana@example.com");
        }
    }

    static class BusinessConflictException extends ConflictException {
        BusinessConflictException() {
            super("conflict");
        }
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/unexpected")
        void unexpected() {
            throw new RuntimeException("select failed for ana@example.com");
        }

        @GetMapping("/gateway-down")
        void gatewayDown() {
            throw new GatewayDownException();
        }

        @GetMapping("/broken-integrity")
        void brokenIntegrity() {
            throw new BrokenLinkException();
        }

        @GetMapping("/business-conflict")
        void businessConflict() {
            throw new BusinessConflictException();
        }

        @GetMapping("/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("bad");
        }

        @GetMapping("/illegal-state")
        void illegalState() {
            throw new IllegalStateException("bad state");
        }
    }

    private static final String REQUEST_ID = "7d1c3a52-0f5e-4a9b-9a53-6b1f7c2d8e10";

    private final List<SentryEvent> sent = new CopyOnWriteArrayList<>();

    private final List<String> sentJson = new CopyOnWriteArrayList<>();

    private MockMvc mockMvc;

    @BeforeEach
    void enableSentryCapturingScrubbedEvents() {
        SentryEventScrubber scrubber = new SentryEventScrubber();
        Sentry.init(options -> {
            options.setDsn("https://publickey@localhost.invalid/1");
            options.setBeforeSend((event, hint) -> {
                SentryEvent scrubbed = scrubber.execute(event, hint);
                sent.add(scrubbed);
                StringWriter writer = new StringWriter();
                try {
                    new JsonSerializer(new SentryOptions()).serialize(scrubbed, writer);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
                sentJson.add(writer.toString());
                return null;
            });
        });
        User user = new User();
        user.setEmail("ana@example.com");
        user.setUsername("ana.perez");
        Sentry.setUser(user);
        Sentry.setExtra("student", "Juan Pérez");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        handler.setServerErrorReporter(new SentryServerErrorReporter(new SentryEventThrottle(1000, 0, new SimpleMeterRegistry())));
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController()).setControllerAdvice(handler).build();
        MDC.put("requestId", REQUEST_ID);
    }

    @AfterEach
    void closeSentry() {
        MDC.clear();
        Sentry.close();
    }

    @Test
    void anUnexpectedErrorIsCapturedExactlyOnceWithTheRequestIdAndMaskedMessage() throws Exception {
        mockMvc.perform(get("/unexpected")).andExpect(status().isInternalServerError());

        assertThat(sent).hasSize(1);
        SentryEvent event = sent.get(0);
        assertThat(event.getTag("requestId")).isEqualTo(REQUEST_ID);
        assertThat(event.getExceptions().get(0).getValue()).doesNotContain("ana@example.com");
    }

    @Test
    void theSerializedEventCarriesNoContextsUserRequestOrSensitiveData() throws Exception {
        mockMvc.perform(get("/unexpected").header("Authorization", "Bearer secret-token-value").queryParam("token", "abc"))
                .andExpect(status().isInternalServerError());

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getContexts().isEmpty()).isTrue();
        assertThat(sentJson).hasSize(1);
        assertThat(sentJson.get(0))
                .doesNotContain("\"contexts\":{\"", "\"os\"", "\"runtime\"", "\"device\"", "\"user\"", "\"request\"", "\"extra\"", "\"breadcrumbs\"")
                .doesNotContain("ana@example.com", "ana.perez", "Juan", "Pérez", "secret-token-value");
    }

    @Test
    void anExternalServiceFailureIsCaptured() throws Exception {
        mockMvc.perform(get("/gateway-down")).andExpect(status().isBadGateway());

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getExceptions().get(0).getValue()).doesNotContain("ana@example.com");
    }

    @Test
    void aBrokenDataIntegrityIsCapturedWithoutTheDni() throws Exception {
        mockMvc.perform(get("/broken-integrity")).andExpect(status().isInternalServerError());

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getExceptions().get(0).getValue()).doesNotContain("40123456");
    }

    @Test
    void clientAndConflictErrorsAreNotCaptured() throws Exception {
        mockMvc.perform(get("/business-conflict")).andExpect(status().isConflict());
        mockMvc.perform(get("/illegal-argument")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/illegal-state")).andExpect(status().isConflict());

        assertThat(new ArrayList<>(sent)).isEmpty();
    }
}

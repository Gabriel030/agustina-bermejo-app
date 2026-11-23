package com.red_escuela.backend.shared.errortracking;

import com.red_escuela.backend.shared.exception.ExternalServiceException;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.JsonSerializer;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;

import static org.assertj.core.api.Assertions.assertThat;

class SentryEventScrubberTest {

    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String ACTIVATION_TOKEN = "Qm9zc0FjdGl2YXRpb25Ub2tlbl9BYmNkRWZnaF8xMjM0NTY3OA";

    private final SentryEventScrubber scrubber = new SentryEventScrubber();

    static class PayerGatewayException extends ExternalServiceException {
        PayerGatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private SentryEvent scrub(SentryEvent event) {
        return scrubber.execute(event, new Hint());
    }

    private static String serialize(SentryEvent event) throws Exception {
        StringWriter writer = new StringWriter();
        new JsonSerializer(new SentryOptions()).serialize(event, writer);
        return writer.toString();
    }

    private static SentryEvent eventFor(Throwable throwable) {
        SentryEvent event = new SentryEvent(throwable);
        List<SentryException> exceptions = new java.util.ArrayList<>();
        java.util.List<Throwable> chain = new java.util.ArrayList<>();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            chain.add(current);
        }
        java.util.Collections.reverse(chain);
        for (Throwable current : chain) {
            SentryException exception = new SentryException();
            exception.setType(current.getClass().getSimpleName());
            exception.setValue(current.getMessage());
            exceptions.add(exception);
        }
        event.setExceptions(exceptions);
        return event;
    }

    private static PSQLException postgresViolation() {
        String serverError = "SERROR\0C23502\0Mnull value in column\0DFailing row contains (17, María, Pérez, 2013-05-02).\0nperson_first_name_nn\0";
        return new PSQLException(new ServerErrorMessage(serverError));
    }

    @Test
    void requestUserBreadcrumbsMessageExtrasAndContextsAreDropped() {
        Request request = new Request();
        request.setData("{\"password\":\"hunter2\"}");
        request.setCookies("session=abc");
        request.setQueryString("token=" + ACTIVATION_TOKEN);
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + JWT);
        request.setHeaders(headers);
        SentryEvent event = new SentryEvent();
        event.setRequest(request);
        User user = new User();
        user.setId("42");
        event.setUser(user);
        event.addBreadcrumb(new Breadcrumb("clicked ana@example.com"));
        Message message = new Message();
        message.setFormatted("Alumno ana@example.com");
        event.setMessage(message);
        event.setExtra("student", "Juan Pérez");
        event.getContexts().put("custom", Map.of("name", "Juan"));
        event.setTransaction("POST /students/Juan");
        event.setFingerprints(List.of("Juan"));
        event.setModule("lib", "1.0");

        SentryEvent scrubbed = scrub(event);

        assertThat(scrubbed.getRequest()).isNull();
        assertThat(scrubbed.getUser()).isNull();
        assertThat(scrubbed.getBreadcrumbs()).isNull();
        assertThat(scrubbed.getMessage()).isNull();
        assertThat(scrubbed.getExtras()).isNull();
        assertThat(scrubbed.getContexts().isEmpty()).isTrue();
        assertThat(scrubbed.getTransaction()).isNull();
        assertThat(scrubbed.getFingerprints()).isNull();
    }

    @Test
    void onlyAUuidRequestIdTagIsKept() {
        String requestId = UUID.randomUUID().toString();
        SentryEvent valid = new SentryEvent();
        valid.setTags(Map.of("requestId", requestId, "other", "x"));
        SentryEvent forged = new SentryEvent();
        forged.setTags(Map.of("requestId", "ana@example.com"));

        assertThat(scrub(valid).getTags()).containsOnly(Map.entry("requestId", requestId));
        assertThat(scrub(forged).getTags()).isEmpty();
    }

    @Test
    void personalDataInPersistenceAndExternalExceptionsNeverReachTheSerializedEvent() throws Exception {
        DataIntegrityViolationException integrity = new DataIntegrityViolationException(
                "could not execute statement; Detail: Key (first_name)=(Juan Pérez) already exists. CUIL 20-12345678-9");
        JpaSystemException jpa = new JpaSystemException(new RuntimeException("Failing row contains (17, María, Pérez, 2013-05-02)", postgresViolation()));
        PayerGatewayException gateway = new PayerGatewayException(
                "payer Carlos Gómez CUIL 20-12345678-9 rejected", new IllegalStateException("payer Carlos Gómez", integrity));

        for (Throwable throwable : List.of(integrity, jpa, gateway)) {
            String json = serialize(scrub(eventFor(throwable)));

            assertThat(json).doesNotContain("Juan", "María", "Pérez", "Carlos", "Gómez", "20-12345678-9", "12345678", "Failing row", "Detail");
        }
    }

    @Test
    void persistenceExceptionKeepsOnlySqlStateAndConstraint() throws Exception {
        JpaSystemException jpa = new JpaSystemException(new RuntimeException("Failing row contains (17, María)", postgresViolation()));

        String json = serialize(scrub(eventFor(jpa)));

        assertThat(json).contains("code=JPA_SYSTEM", "sqlState=23502", "constraint=person_first_name_nn");
    }

    @Test
    void everyCauseInTheChainGetsTheSafeValueNotItsMessage() {
        PayerGatewayException gateway = new PayerGatewayException("payer Carlos", new IllegalStateException("inner Carlos"));

        List<SentryException> exceptions = scrub(eventFor(gateway)).getExceptions();

        assertThat(exceptions).extracting(SentryException::getValue)
                .containsExactly("code=ILLEGAL_STATE", "code=PAYER_GATEWAY");
    }

    @Test
    void emailDniCuilPhoneAndTokensAreMaskedByTheBelt() {
        assertThat(SentryEventScrubber.maskText("mail ana.pérez@example.com")).isEqualTo("mail [redacted]");
        assertThat(SentryEventScrubber.maskText("dni 40123456")).isEqualTo("dni [redacted]");
        assertThat(SentryEventScrubber.maskText("dni 40.123.456")).isEqualTo("dni [redacted]");
        assertThat(SentryEventScrubber.maskText("dni 40 123 456")).isEqualTo("dni [redacted]");
        assertThat(SentryEventScrubber.maskText("dni 40,123,456")).isEqualTo("dni [redacted]");
        assertThat(SentryEventScrubber.maskText("cuil 20-40123456-9")).isEqualTo("cuil [redacted]");
        assertThat(SentryEventScrubber.maskText("cuit 30712345678")).isEqualTo("cuit [redacted]");
        assertThat(SentryEventScrubber.maskText("tel +54 (11) 4567-8901")).isEqualTo("tel [redacted]");
        assertThat(SentryEventScrubber.maskText("jwt " + JWT)).isEqualTo("jwt [redacted]");
        assertThat(SentryEventScrubber.maskText("token " + ACTIVATION_TOKEN)).isEqualTo("token [redacted]");
    }

    @Test
    void textIsTruncatedBeforeMasking() {
        String masked = SentryEventScrubber.maskText("ab ".repeat(SentryEventScrubber.MAX_MASKED_LENGTH));

        assertThat(masked).hasSize(SentryEventScrubber.MAX_MASKED_LENGTH);
    }

    @Test
    void shortNumbersAndPlainWordsAreKept() {
        assertThat(SentryEventScrubber.maskText("Invoice 12345 in status PAID for installment 6"))
                .isEqualTo("Invoice 12345 in status PAID for installment 6");
        assertThat(SentryEventScrubber.maskText("amount 123456789")).isEqualTo("amount 123456789");
    }

    @Test
    void aNullTextStaysNull() {
        assertThat(SentryEventScrubber.maskText(null)).isNull();
    }

    @Test
    void anEventWithoutExceptionsIsReturnedSameInstance() {
        SentryEvent event = new SentryEvent();

        assertThat(scrub(event)).isSameAs(event);
    }
}

package com.red_escuela.backend.shared.errortracking;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentryEventScrubberTest {

    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String ACTIVATION_TOKEN = "Qm9zc0FjdGl2YXRpb25Ub2tlbl9BYmNkRWZnaF8xMjM0NTY3OA";

    private final SentryEventScrubber scrubber = new SentryEventScrubber();

    private SentryEvent scrub(SentryEvent event) {
        return scrubber.execute(event, new Hint());
    }

    @Test
    void requestBodyCookiesAndQueryStringAreRemoved() {
        Request request = new Request();
        request.setData("{\"password\":\"hunter2\"}");
        request.setCookies("session=abc");
        request.setQueryString("token=" + ACTIVATION_TOKEN);
        SentryEvent event = new SentryEvent();
        event.setRequest(request);

        Request scrubbed = scrub(event).getRequest();

        assertThat(scrubbed.getData()).isNull();
        assertThat(scrubbed.getCookies()).isNull();
        assertThat(scrubbed.getQueryString()).isNull();
    }

    @Test
    void sensitiveHeadersAreRemovedRegardlessOfCase() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + JWT);
        headers.put("cookie", "session=abc");
        headers.put("Set-Cookie", "session=abc");
        headers.put("User-Agent", "Mozilla");
        Request request = new Request();
        request.setHeaders(headers);
        SentryEvent event = new SentryEvent();
        event.setRequest(request);

        Map<String, String> kept = scrub(event).getRequest().getHeaders();

        assertThat(kept).containsOnlyKeys("User-Agent");
    }

    @Test
    void urlLosesItsQueryAndAnyTokenInThePath() {
        Request request = new Request();
        request.setUrl("https://app.example/auth/activate/" + ACTIVATION_TOKEN + "?token=" + ACTIVATION_TOKEN);
        SentryEvent event = new SentryEvent();
        event.setRequest(request);

        String url = scrub(event).getRequest().getUrl();

        assertThat(url).isEqualTo("https://app.example/auth/activate/" + SentryEventScrubber.MASK);
    }

    @Test
    void userAndBreadcrumbsAreRemoved() {
        SentryEvent event = new SentryEvent();
        User user = new User();
        user.setId("42");
        user.setEmail("ana@example.com");
        event.setUser(user);

        assertThat(scrub(event).getUser()).isNull();
    }

    @Test
    void emailDniAndJwtAreMaskedInTheMessage() {
        SentryEvent event = new SentryEvent();
        Message message = new Message();
        message.setFormatted("Alumno ana.perez@example.com DNI 40123456 y 12.345.678 token " + JWT);
        event.setMessage(message);

        String formatted = scrub(event).getMessage().getFormatted();

        assertThat(formatted).doesNotContain("ana.perez@example.com", "40123456", "12.345.678", JWT);
        assertThat(formatted).isEqualTo("Alumno [redacted] DNI [redacted] y [redacted] token [redacted]");
    }

    @Test
    void longUrlSafeTokensAreMaskedInExceptionValues() {
        SentryException exception = new SentryException();
        exception.setValue("Invalid activation token " + ACTIVATION_TOKEN + " for ana@example.com");
        SentryEvent event = new SentryEvent();
        event.setExceptions(List.of(exception));

        String value = scrub(event).getExceptions().get(0).getValue();

        assertThat(value).isEqualTo("Invalid activation token [redacted] for [redacted]");
    }

    @Test
    void shortNumbersAndPlainWordsAreKept() {
        assertThat(SentryEventScrubber.maskText("Invoice 12345 in status PAID for installment 6"))
                .isEqualTo("Invoice 12345 in status PAID for installment 6");
    }

    @Test
    void nineDigitNumbersAreNotTreatedAsDni() {
        assertThat(SentryEventScrubber.maskText("amount 123456789")).isEqualTo("amount 123456789");
    }

    @Test
    void aNullTextStaysNull() {
        assertThat(SentryEventScrubber.maskText(null)).isNull();
    }

    @Test
    void anEventWithoutRequestMessageOrExceptionsIsReturnedUntouched() {
        SentryEvent event = new SentryEvent();

        assertThat(scrub(event)).isSameAs(event);
    }
}

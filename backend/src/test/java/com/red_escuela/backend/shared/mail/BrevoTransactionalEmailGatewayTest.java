package com.red_escuela.backend.shared.mail;

import com.red_escuela.backend.shared.domain.EmailAddress;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class BrevoTransactionalEmailGatewayTest {

    private static final String FAKE_API_KEY = "xkeysib-fake-key-for-tests-only";

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final CopyOnWriteArrayList<String> requestBodies = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> apiKeyHeaders = new CopyOnWriteArrayList<>();
    private volatile Consumer<HttpExchange> handler;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            requestCount.incrementAndGet();
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKeyHeaders.add(exchange.getRequestHeaders().getFirst("api-key"));
            handler.accept(exchange);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private BrevoTransactionalEmailGateway gateway(long readTimeoutMillis) {
        return new BrevoTransactionalEmailGateway("http://127.0.0.1:" + server.getAddress().getPort(), FAKE_API_KEY,
                "no-reply@example.com", "Escuela", 2000, readTimeoutMillis);
    }

    private static TransactionalEmail email(String unsubscribeUrl) {
        return new TransactionalEmail(EmailAddress.of("maria.lopez@example.com"), "Asunto", "Texto", "<p>Texto</p>", unsubscribeUrl);
    }

    private static void respond(HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Test
    void successfulSendReturnsTheBrevoMessageIdAsARealReceipt() {
        handler = exchange -> respond(exchange, 201, "{\"messageId\":\"<202611171300.1@smtp-relay.mailin.fr>\"}");

        EmailDeliveryReceipt receipt = gateway(2000).send(email(null));

        assertThat(receipt.providerMessageId()).isEqualTo("<202611171300.1@smtp-relay.mailin.fr>");
        assertThat(receipt.simulated()).isFalse();
        assertThat(apiKeyHeaders).containsExactly(FAKE_API_KEY);
        assertThat(requestBodies.getFirst()).contains("\"subject\":\"Asunto\"", "maria.lopez@example.com", "no-reply@example.com", "\"textContent\":\"Texto\"")
                .doesNotContain("List-Unsubscribe");
    }

    @Test
    void clientErrorFailsWithoutRetryableTypeAndIsSentOnlyOnce() {
        handler = exchange -> respond(exchange, 400, "{\"code\":\"invalid_parameter\"}");

        assertThatThrownBy(() -> gateway(2000).send(email(null)))
                .isInstanceOf(TransactionalEmailRejectedException.class)
                .satisfies(failure -> assertThat(((TransactionalEmailRejectedException) failure).statusCode()).isEqualTo(400));
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void clientErrorDoesNotCountAsAProviderFailureForTheCircuit() {
        handler = exchange -> respond(exchange, 401, "{}");

        assertThatThrownBy(() -> gateway(2000).send(email(null)))
                .satisfies(failure -> assertThat(MailCircuit.countsAsProviderFailure(failure)).isFalse());
    }

    @Test
    void serverErrorIsARetryableDeliveryFailureThatCountsForTheCircuit() {
        handler = exchange -> respond(exchange, 503, "{}");

        assertThatThrownBy(() -> gateway(2000).send(email(null)))
                .isInstanceOf(TransactionalEmailDeliveryException.class)
                .isNotInstanceOf(TransactionalEmailRejectedException.class)
                .satisfies(failure -> assertThat(MailCircuit.countsAsProviderFailure(failure)).isTrue());
    }

    @Test
    void timeoutIsARetryableDeliveryFailure() {
        handler = exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };

        assertThatThrownBy(() -> gateway(200).send(email(null)))
                .isInstanceOf(TransactionalEmailDeliveryException.class)
                .isNotInstanceOf(TransactionalEmailRejectedException.class);
    }

    @Test
    void unsubscribeLinkIsSentAsListUnsubscribeHeader() {
        handler = exchange -> respond(exchange, 201, "{\"messageId\":\"abc\"}");

        gateway(2000).send(email("https://escuela.example.com/baja/token123"));

        assertThat(requestBodies.getFirst()).contains("\"headers\":{\"List-Unsubscribe\":\"<https://escuela.example.com/baja/token123>\"}");
    }

    @Test
    void responseWithoutMessageIdIsARetryableDeliveryFailure() {
        handler = exchange -> respond(exchange, 201, "{}");

        assertThatThrownBy(() -> gateway(2000).send(email(null)))
                .isInstanceOf(TransactionalEmailDeliveryException.class);
    }

    @Test
    void overlongMessageIdIsTruncatedToTheReceiptLimit() {
        handler = exchange -> respond(exchange, 201, "{\"messageId\":\"" + "m".repeat(200) + "\"}");

        EmailDeliveryReceipt receipt = gateway(2000).send(email(null));

        assertThat(receipt.providerMessageId()).hasSize(EmailDeliveryReceipt.MAX_PROVIDER_MESSAGE_ID_LENGTH);
    }

    @Test
    void neitherTheApiKeyNorTheFullRecipientAreLogged(CapturedOutput output) {
        handler = exchange -> respond(exchange, 500, "{}");

        assertThatThrownBy(() -> gateway(2000).send(email(null))).isInstanceOf(TransactionalEmailDeliveryException.class);

        assertThat(output.getAll()).contains("ma***@example.com").doesNotContain(FAKE_API_KEY).doesNotContain("maria.lopez");
    }

    @Test
    void startupFailsWhenBrevoIsSelectedWithoutAnApiKey() {
        assertThatThrownBy(() -> new BrevoTransactionalEmailGateway("http://127.0.0.1:1", " ", "no-reply@example.com", "", 1000, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BREVO_API_KEY")
                .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(FAKE_API_KEY));
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(MockTransactionalEmailGateway.class, BrevoTransactionalEmailGateway.class))
            .withPropertyValues("app.mail.from-address=no-reply@example.com", "app.mail.brevo.api-url=http://127.0.0.1:1",
                    "app.mail.brevo.connect-timeout-ms=1000", "app.mail.brevo.read-timeout-ms=1000");

    @Test
    void mockIsTheDefaultProviderWhenNothingIsConfigured() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(TransactionalEmailGateway.class).hasSingleBean(MockTransactionalEmailGateway.class));
    }

    @Test
    void brevoProviderWithAnApiKeyRegistersOnlyTheBrevoGateway() {
        contextRunner.withPropertyValues("app.mail.transactional.provider=brevo", "app.mail.brevo.api-key=" + FAKE_API_KEY)
                .run(context -> assertThat(context).hasSingleBean(BrevoTransactionalEmailGateway.class).doesNotHaveBean(MockTransactionalEmailGateway.class));
    }

    @Test
    void brevoProviderWithoutAnApiKeyPreventsStartup() {
        contextRunner.withPropertyValues("app.mail.transactional.provider=brevo")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void unknownProviderLeavesNoGatewayBeanSoStartupFailsForConsumers() {
        contextRunner.withPropertyValues("app.mail.transactional.provider=ses")
                .run(context -> assertThat(context).doesNotHaveBean(TransactionalEmailGateway.class));
    }
}

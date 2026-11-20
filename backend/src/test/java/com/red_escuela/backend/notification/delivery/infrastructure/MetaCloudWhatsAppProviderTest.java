package com.red_escuela.backend.notification.delivery.infrastructure;

import com.red_escuela.backend.notification.delivery.specialist.WhatsAppProviderRejectedException;
import com.red_escuela.backend.notification.delivery.specialist.WhatsAppProviderUnavailableException;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetaCloudWhatsAppProviderTest {

    private HttpServer server;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final CountDownLatch releaseHangingRequest = new CountDownLatch(1);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        releaseHangingRequest.countDown();
        server.stop(0);
    }

    private MetaCloudWhatsAppProvider provider() {
        return new MetaCloudWhatsAppProvider("http://127.0.0.1:" + server.getAddress().getPort(), "v21.0", "12345", "secret-token", 500, 300);
    }

    private void respond(int status, String body) {
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    void successfulSendReturnsTheProviderMessageIdAndBuildsTheTemplateRequest() {
        respond(200, "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.ABC\"}]}");

        var receipt = provider().sendTemplate("+5491112345678", "aviso_general", "es_AR", List.of("Ana", "Instituto"));

        assertThat(receipt.providerMessageId()).isEqualTo("wamid.ABC");
        assertThat(requestPath.get()).isEqualTo("/v21.0/12345/messages");
        assertThat(authorization.get()).isEqualTo("Bearer secret-token");
        assertThat(requestBody.get()).contains("\"to\":\"5491112345678\"", "\"name\":\"aviso_general\"", "\"code\":\"es_AR\"",
                "\"type\":\"template\"", "\"text\":\"Ana\"", "\"text\":\"Instituto\"");
    }

    @Test
    void policyErrorIsRejectedWithTheProviderErrorCode() {
        respond(400, "{\"error\":{\"message\":\"Template not approved\",\"type\":\"OAuthException\",\"code\":132001}}");

        assertThatThrownBy(() -> provider().sendTemplate("+5491112345678", "aviso", "es_AR", List.of()))
                .isInstanceOfSatisfying(WhatsAppProviderRejectedException.class, rejection -> {
                    assertThat(rejection.statusCode()).isEqualTo(400);
                    assertThat(rejection.errorCode()).isEqualTo("132001");
                });
    }

    @Test
    void rejectionWithoutReadableBodyFallsBackToTheHttpStatus() {
        respond(403, "not json");

        assertThatThrownBy(() -> provider().sendTemplate("+5491112345678", "aviso", "es_AR", List.of()))
                .isInstanceOfSatisfying(WhatsAppProviderRejectedException.class, rejection -> assertThat(rejection.errorCode()).isEqualTo("HTTP_403"));
    }

    @Test
    void serverErrorIsTransient() {
        respond(503, "{}");

        assertThatThrownBy(() -> provider().sendTemplate("+5491112345678", "aviso", "es_AR", List.of()))
                .isInstanceOf(WhatsAppProviderUnavailableException.class);
    }

    @Test
    void rateLimitIsTransient() {
        respond(429, "{\"error\":{\"code\":130429}}");

        assertThatThrownBy(() -> provider().sendTemplate("+5491112345678", "aviso", "es_AR", List.of()))
                .isInstanceOf(WhatsAppProviderUnavailableException.class);
    }

    @Test
    void timeoutIsTransient() {
        server.createContext("/", exchange -> {
            try {
                releaseHangingRequest.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });

        assertThatThrownBy(() -> provider().sendTemplate("+5491112345678", "aviso", "es_AR", List.of()))
                .isInstanceOf(WhatsAppProviderUnavailableException.class);
    }

    @Test
    void responseWithoutMessageIdIsTransient() {
        respond(200, "{\"messages\":[]}");

        assertThatThrownBy(() -> provider().sendTemplate("+5491112345678", "aviso", "es_AR", List.of()))
                .isInstanceOf(WhatsAppProviderUnavailableException.class);
    }

    @Test
    void missingCredentialsFailFast() {
        assertThatThrownBy(() -> new MetaCloudWhatsAppProvider("http://localhost", "v21.0", "", "token", 500, 300))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MetaCloudWhatsAppProvider("http://localhost", "v21.0", "123", " ", 500, 300))
                .isInstanceOf(IllegalStateException.class);
    }
}

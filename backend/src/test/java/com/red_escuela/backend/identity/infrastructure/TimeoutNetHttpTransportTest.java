package com.red_escuela.backend.identity.infrastructure;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeoutNetHttpTransportTest {

    private ServerSocket silentServer;
    private HttpServer okServer;

    @AfterEach
    void stopServers() throws IOException {
        if (silentServer != null) {
            silentServer.close();
        }
        if (okServer != null) {
            okServer.stop(0);
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    void aServerThatNeverRespondsFailsAtTheConfiguredReadTimeoutInsteadOfTheLibraryDefault() throws IOException {
        silentServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        TimeoutNetHttpTransport transport = new TimeoutNetHttpTransport(Duration.ofMillis(500), Duration.ofMillis(300));
        HttpRequest request = transport.createRequestFactory()
                .buildGetRequest(new GenericUrl("http://127.0.0.1:" + silentServer.getLocalPort() + "/certs"));
        request.setNumberOfRetries(0);
        request.setConnectTimeout(60_000).setReadTimeout(60_000);

        long start = System.nanoTime();
        assertThatThrownBy(request::execute).isInstanceOf(SocketTimeoutException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void aRespondingServerIsReadNormally() throws IOException {
        okServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        okServer.createContext("/certs", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        okServer.start();
        TimeoutNetHttpTransport transport = new TimeoutNetHttpTransport(Duration.ofSeconds(2), Duration.ofSeconds(2));

        HttpResponse response = transport.createRequestFactory()
                .buildGetRequest(new GenericUrl("http://127.0.0.1:" + okServer.getAddress().getPort() + "/certs")).execute();

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.parseAsString()).isEqualTo("{\"ok\":true}");
    }
}

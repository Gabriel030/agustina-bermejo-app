package com.red_escuela.backend.shared.errortracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.shared.errortracking.infrastructure.SentryEnvelopeGateway;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SentryTunnelTest {

    private HttpServer upstream;
    private HttpServer decoy;
    private final List<String> upstreamPaths = new CopyOnWriteArrayList<>();
    private final List<String> upstreamBodies = new CopyOnWriteArrayList<>();
    private final List<String> decoyPaths = new CopyOnWriteArrayList<>();
    private String dsn;

    @BeforeEach
    void startServers() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        upstream.createContext("/", exchange -> {
            upstreamPaths.add(exchange.getRequestURI().getPath());
            upstreamBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        upstream.start();
        decoy = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        decoy.createContext("/", exchange -> {
            decoyPaths.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        decoy.start();
        dsn = "http://key@127.0.0.1:" + upstream.getAddress().getPort() + "/5";
    }

    @AfterEach
    void stopServers() {
        upstream.stop(0);
        decoy.stop(0);
    }

    private SentryTunnel tunnel(String configuredDsn, int maxBytes) {
        return new SentryTunnel(configuredDsn, maxBytes, new SentryEnvelopeGateway(1000, 1000));
    }

    private static byte[] envelope(String headerDsn) {
        return ("{\"dsn\":\"" + headerDsn + "\",\"event_id\":\"1\"}\n{\"type\":\"event\"}\n{\"message\":\"boom\"}\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void forwardsEnvelopeToTheHostDerivedFromTheConfiguredDsn() {
        SentryTunnelOutcome outcome = tunnel(dsn, 10_000).forward(envelope(dsn));

        assertThat(outcome).isEqualTo(SentryTunnelOutcome.FORWARDED);
        assertThat(upstreamPaths).containsExactly("/api/5/envelope/");
        assertThat(upstreamBodies.get(0)).contains("\"message\":\"boom\"");
    }

    @Test
    void ignoresAnyHostCarriedInTheEnvelopeBody() {
        String attackerDsn = "http://key@127.0.0.1:" + decoy.getAddress().getPort() + "/9";

        SentryTunnelOutcome outcome = tunnel(dsn, 10_000).forward(envelope(attackerDsn));

        assertThat(outcome).isEqualTo(SentryTunnelOutcome.REJECTED_ENVELOPE);
        assertThat(decoyPaths).isEmpty();
        assertThat(upstreamPaths).isEmpty();
    }

    @Test
    void neverContactsTheDecoyEvenWhenTheBodyEmbedsItsUrl() {
        byte[] body = ("{\"dsn\":\"" + dsn + "\",\"url\":\"http://127.0.0.1:" + decoy.getAddress().getPort() + "/\"}\n{\"type\":\"event\"}\n{}\n")
                .getBytes(StandardCharsets.UTF_8);

        tunnel(dsn, 10_000).forward(body);

        assertThat(decoyPaths).isEmpty();
        assertThat(upstreamPaths).hasSize(1);
    }

    @Test
    void rejectsEnvelopeBiggerThanTheLimitWithoutForwarding() {
        byte[] body = envelope(dsn);

        SentryTunnelOutcome outcome = tunnel(dsn, body.length - 1).forward(body);

        assertThat(outcome).isEqualTo(SentryTunnelOutcome.PAYLOAD_TOO_LARGE);
        assertThat(upstreamPaths).isEmpty();
    }

    @Test
    void acceptsEnvelopeExactlyAtTheLimit() {
        byte[] body = envelope(dsn);

        assertThat(tunnel(dsn, body.length).forward(body)).isEqualTo(SentryTunnelOutcome.FORWARDED);
    }

    @Test
    void rejectsEnvelopeWithoutDsnHeaderOrWithGarbageHeader() {
        SentryTunnel tunnel = tunnel(dsn, 10_000);

        assertThat(tunnel.forward("{\"event_id\":\"1\"}\n{}\n".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(SentryTunnelOutcome.REJECTED_ENVELOPE);
        assertThat(tunnel.forward("garbage\n{}\n".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(SentryTunnelOutcome.REJECTED_ENVELOPE);
        assertThat(tunnel.forward("no newline".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(SentryTunnelOutcome.REJECTED_ENVELOPE);
        assertThat(upstreamPaths).isEmpty();
    }

    @Test
    void isInertWhenDsnIsBlank() {
        SentryTunnel tunnel = tunnel("", 10_000);

        assertThat(tunnel.isEnabled()).isFalse();
        assertThat(tunnel.forward(envelope(dsn))).isEqualTo(SentryTunnelOutcome.DISABLED);
        assertThat(upstreamPaths).isEmpty();
    }

    @Test
    void doesNotFollowRedirectsToAnotherHost() throws IOException {
        upstream.removeContext("/");
        upstream.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + decoy.getAddress().getPort() + "/stolen");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        SentryTunnelOutcome outcome = tunnel(dsn, 10_000).forward(envelope(dsn));

        assertThat(outcome).isEqualTo(SentryTunnelOutcome.UPSTREAM_REJECTED);
        assertThat(decoyPaths).isEmpty();
    }

    @Test
    void reportsUpstreamRejectedForClientAndServerErrorStatuses() {
        upstream.removeContext("/");
        upstream.createContext("/", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        assertThat(tunnel(dsn, 10_000).forward(envelope(dsn))).isEqualTo(SentryTunnelOutcome.UPSTREAM_REJECTED);

        upstream.removeContext("/");
        upstream.createContext("/", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        assertThat(tunnel(dsn, 10_000).forward(envelope(dsn))).isEqualTo(SentryTunnelOutcome.UPSTREAM_REJECTED);
    }

    @Test
    void acceptsEventTransactionAndSessionItems() {
        String body = "{\"dsn\":\"" + dsn + "\"}\n{\"type\":\"event\"}\n{}\n{\"type\":\"transaction\"}\n{}\n{\"type\":\"session\"}\n{}\n";

        assertThat(tunnel(dsn, 10_000).forward(body.getBytes(StandardCharsets.UTF_8))).isEqualTo(SentryTunnelOutcome.FORWARDED);
    }

    @Test
    void rejectsEnvelopeWithAnyItemTypeOutsideTheAllowlist() {
        for (String type : List.of("attachment", "profile", "replay_event", "client_report", "check_in", "unknown")) {
            String body = "{\"dsn\":\"" + dsn + "\"}\n{\"type\":\"event\"}\n{}\n{\"type\":\"" + type + "\"}\n{}\n";

            assertThat(tunnel(dsn, 10_000).forward(body.getBytes(StandardCharsets.UTF_8)))
                    .as(type).isEqualTo(SentryTunnelOutcome.REJECTED_ENVELOPE);
        }
        assertThat(upstreamPaths).isEmpty();
    }

    @Test
    void rejectsDisallowedItemHiddenAfterAPayloadDeclaringItsLength() {
        String payload = "{\"a\":\"x\\ny\"}";
        String body = "{\"dsn\":\"" + dsn + "\"}\n{\"type\":\"event\",\"length\":" + payload.length() + "}\n" + payload
                + "\n{\"type\":\"attachment\",\"length\":2}\nzz\n";

        assertThat(tunnel(dsn, 10_000).forward(body.getBytes(StandardCharsets.UTF_8))).isEqualTo(SentryTunnelOutcome.REJECTED_ENVELOPE);
    }

    @Test
    void rejectsItemsDeclaringAnOversizedOrNegativeLength() {
        for (String length : List.of("2147483647", "2147483640", "9999999999", "-1", "999")) {
            String body = "{\"dsn\":\"" + dsn + "\"}\n{\"type\":\"event\",\"length\":" + length + "}\n{}\n";

            assertThat(tunnel(dsn, 10_000).forward(body.getBytes(StandardCharsets.UTF_8)))
                    .as(length).isEqualTo(SentryTunnelOutcome.REJECTED_ENVELOPE);
        }
        assertThat(upstreamPaths).isEmpty();
    }

    @Test
    void rejectsEnvelopeWithoutItemsOrWithUntypedItem() {
        assertThat(tunnel(dsn, 10_000).forward(("{\"dsn\":\"" + dsn + "\"}\n").getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(SentryTunnelOutcome.REJECTED_ENVELOPE);
        assertThat(tunnel(dsn, 10_000).forward(("{\"dsn\":\"" + dsn + "\"}\n{}\n{}\n").getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(SentryTunnelOutcome.REJECTED_ENVELOPE);
    }

    @Test
    void reportsUpstreamUnavailableWhenTheHostIsDown() {
        upstream.stop(0);

        assertThat(tunnel(dsn, 10_000).forward(envelope(dsn))).isEqualTo(SentryTunnelOutcome.UPSTREAM_UNAVAILABLE);
    }
}

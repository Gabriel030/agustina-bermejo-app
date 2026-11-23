package com.red_escuela.backend.identity.infrastructure;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.red_escuela.backend.identity.exception.InvalidGoogleTokenException;
import com.red_escuela.backend.shared.resilience.Bulkhead;
import com.red_escuela.backend.shared.resilience.BulkheadSettings;
import com.red_escuela.backend.shared.resilience.CircuitBreaker;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenServiceMalformedTokenTest {

    private final AtomicInteger networkCalls = new AtomicInteger();
    private GoogleIdTokenService service;

    @BeforeEach
    void setUp() {
        HttpTransport offlineTransport = new HttpTransport() {
            @Override
            protected LowLevelHttpRequest buildRequest(String method, String url) {
                networkCalls.incrementAndGet();
                throw new IllegalStateException("no network allowed");
            }
        };
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(offlineTransport, GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList("expected-client-id"))
                .build();
        MockEnvironment environment = new MockEnvironment().withProperty("resilience.default.enabled", "true")
                .withProperty("resilience.default.window", "10")
                .withProperty("resilience.default.failure-threshold", "5")
                .withProperty("resilience.default.wait-open", "30s");
        CircuitBreaker breaker = new CircuitBreakerFactory(environment, Clock.systemUTC(), new SimpleMeterRegistry())
                .breakerNamed(GoogleCircuit.NAME);
        Bulkhead bulkhead = new Bulkhead(GoogleCircuit.NAME, new BulkheadSettings(true, 4, Duration.ofMillis(50)),
                new SimpleMeterRegistry());
        service = new GoogleIdTokenService(verifier, breaker, bulkhead);
    }

    private static final String PUBLISHED_KID = "published-key";
    private static final String AUDIENCE = "expected-client-id";
    private static PrivateKey publishedKey;
    private static PrivateKey foreignKey;
    private static String publishedCertificatePem;

    @BeforeAll
    static void generateKeys(@TempDir Path directory) throws Exception {
        publishedKey = generateKeyPair(directory, "published");
        publishedCertificatePem = Files.readString(directory.resolve("published.pem"));
        foreignKey = generateKeyPair(directory, "foreign");
    }

    private static PrivateKey generateKeyPair(Path directory, String alias) throws Exception {
        Path keystore = directory.resolve(alias + ".p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        run(keytool, "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=" + alias,
                "-validity", "30", "-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass", "changeit");
        Path pem = directory.resolve(alias + ".pem");
        run(keytool, "-exportcert", "-rfc", "-alias", alias, "-keystore", keystore.toString(), "-storepass", "changeit",
                "-file", pem.toString());
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(keystore)) {
            store.load(in, "changeit".toCharArray());
        }
        return (PrivateKey) store.getKey(alias, "changeit".toCharArray());
    }

    private static void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertThat(process.waitFor()).isZero();
    }

    private GoogleIdTokenService serviceWithPublishedCertificates() {
        MockHttpTransport transport = new MockHttpTransport.Builder().setLowLevelHttpResponse(new MockLowLevelHttpResponse()
                .setContentType("application/json")
                .setContent("{\"" + PUBLISHED_KID + "\":" + jsonString(publishedCertificatePem) + "}"))
                .build();
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(AUDIENCE)).build();
        MockEnvironment environment = new MockEnvironment().withProperty("resilience.default.enabled", "true")
                .withProperty("resilience.default.window", "10").withProperty("resilience.default.failure-threshold", "5")
                .withProperty("resilience.default.wait-open", "30s");
        return new GoogleIdTokenService(verifier,
                new CircuitBreakerFactory(environment, Clock.systemUTC(), new SimpleMeterRegistry()).breakerNamed(GoogleCircuit.NAME),
                new Bulkhead(GoogleCircuit.NAME, new BulkheadSettings(true, 4, Duration.ofMillis(50)), new SimpleMeterRegistry()));
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\r", "").replace("\n", "\\n") + "\"";
    }

    private static String signedToken(PrivateKey key, String audience, String issuer, long expiresInSeconds, boolean emailVerified)
            throws Exception {
        long now = System.currentTimeMillis() / 1000;
        String header = segment("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + PUBLISHED_KID + "\"}");
        String payload = segment("{\"iss\":\"" + issuer + "\",\"aud\":\"" + audience + "\",\"sub\":\"google-sub-1\","
                + "\"email\":\"someone@example.com\",\"email_verified\":" + emailVerified + ",\"iat\":" + now
                + ",\"exp\":" + (now + expiresInSeconds) + "}");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        return header + "." + payload + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    @Test
    void aTokenSignedWithThePublishedKeyAndTheExpectedAudienceIsAccepted() throws Exception {
        GoogleIdTokenService.GoogleProfile profile = serviceWithPublishedCertificates()
                .verify(signedToken(publishedKey, AUDIENCE, "https://accounts.google.com", 3600, true));

        assertThat(profile.subject()).isEqualTo("google-sub-1");
    }

    @Test
    void tokensSignedWithAnotherKeyOfAnotherAudienceExpiredOfAnotherIssuerOrWithUnverifiedEmailAreRejected() throws Exception {
        GoogleIdTokenService service = serviceWithPublishedCertificates();
        String google = "https://accounts.google.com";

        assertThatThrownBy(() -> service.verify(signedToken(foreignKey, AUDIENCE, google, 3600, true)))
                .as("signed by another key").isInstanceOf(InvalidGoogleTokenException.class);
        assertThatThrownBy(() -> service.verify(signedToken(publishedKey, "other-client-id", google, 3600, true)))
                .as("another audience").isInstanceOf(InvalidGoogleTokenException.class);
        assertThatThrownBy(() -> service.verify(signedToken(publishedKey, AUDIENCE, google, -3600, true)))
                .as("expired").isInstanceOf(InvalidGoogleTokenException.class);
        assertThatThrownBy(() -> service.verify(signedToken(publishedKey, AUDIENCE, "https://evil.example.com", 3600, true)))
                .as("another issuer").isInstanceOf(InvalidGoogleTokenException.class);
        assertThatThrownBy(() -> service.verify(signedToken(publishedKey, AUDIENCE, google, 3600, false)))
                .as("unverified email").isInstanceOf(InvalidGoogleTokenException.class);
    }

    private static String segment(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }

    @Test
    void structurallyInvalidTokensAreRejectedAsInvalidWithoutCallingGoogle() {
        List<String> tokens = List.of("", "garbage", "a.b.c", "a.b", "....", "' OR 1=1--", "A".repeat(10_000),
                segment("{}") + "." + segment("{}") + "." + segment("sig"),
                segment("{\"alg\":\"none\"}") + "." + segment("{\"sub\":\"1\",\"email\":\"x@example.com\"}") + ".");
        for (String token : tokens) {
            assertThatThrownBy(() -> service.verify(token))
                    .as(token.substring(0, Math.min(15, token.length())))
                    .isInstanceOf(InvalidGoogleTokenException.class);
        }
        assertThat(networkCalls).hasValue(0);
    }

    @Test
    void anUnsignedTokenWithoutIssuedAtCurrentlyEscapesAsNullPointerInsteadOfInvalidToken() {
        String unsigned = segment("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "."
                + segment("{\"iss\":\"https://accounts.google.com\",\"aud\":\"expected-client-id\",\"sub\":\"1\","
                + "\"email\":\"victim@example.com\",\"email_verified\":true,\"exp\":4102444800}") + ".";

        assertThatThrownBy(() -> service.verify(unsigned))
                .as("KNOWN GAP (pentest L4): the expected behaviour is InvalidGoogleTokenException; fix is to catch RuntimeException "
                        + "from the verifier and map it to InvalidGoogleTokenException (remediation 24.7); today it escapes as NPE (HTTP 500)")
                .isInstanceOf(NullPointerException.class);
        assertThat(networkCalls).hasValue(0);
    }
}

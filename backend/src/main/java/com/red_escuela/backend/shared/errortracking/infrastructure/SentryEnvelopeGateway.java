package com.red_escuela.backend.shared.errortracking.infrastructure;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SentryEnvelopeGateway {

    private static final String ENVELOPE_CONTENT_TYPE = "application/x-sentry-envelope";

    private final RestClient restClient;

    @Autowired
    public SentryEnvelopeGateway(
            @Value("${sentry.tunnel.connect-timeout-ms:2000}") long connectTimeoutMillis,
            @Value("${sentry.tunnel.read-timeout-ms:3000}") long readTimeoutMillis) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public int postEnvelope(URI envelopeUri, byte[] envelope) {
        return restClient.post()
                .uri(envelopeUri)
                .header(HttpHeaders.CONTENT_TYPE, ENVELOPE_CONTENT_TYPE)
                .body(envelope)
                .exchange((request, response) -> response.getStatusCode().value());
    }
}

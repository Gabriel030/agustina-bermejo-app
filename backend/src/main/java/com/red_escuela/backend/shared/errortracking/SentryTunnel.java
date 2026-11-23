package com.red_escuela.backend.shared.errortracking;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.red_escuela.backend.shared.errortracking.infrastructure.SentryEnvelopeGateway;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SentryTunnel {

    private static final Logger log = LoggerFactory.getLogger(SentryTunnel.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Optional<SentryTunnelTarget> target;
    private final int maxBytes;
    private final SentryEnvelopeGateway envelopeGateway;

    @Autowired
    public SentryTunnel(
            @Value("${sentry.dsn:}") String dsn,
            @Value("${sentry.tunnel.max-bytes:204800}") int maxBytes,
            SentryEnvelopeGateway envelopeGateway) {
        this.target = SentryTunnelTarget.fromDsn(dsn);
        this.maxBytes = maxBytes;
        this.envelopeGateway = envelopeGateway;
    }

    public boolean isEnabled() {
        return target.isPresent();
    }

    public int maxBytes() {
        return maxBytes;
    }

    public SentryTunnelOutcome forward(byte[] envelope) {
        if (target.isEmpty()) {
            return SentryTunnelOutcome.DISABLED;
        }
        if (envelope.length > maxBytes) {
            return SentryTunnelOutcome.PAYLOAD_TOO_LARGE;
        }
        if (!envelopeBelongsTo(envelope, target.get())) {
            return SentryTunnelOutcome.REJECTED_ENVELOPE;
        }
        try {
            int status = envelopeGateway.postEnvelope(target.get().envelopeUri(), envelope);
            if (status >= 400) {
                log.warn("Sentry tunnel upstream answered status={}", status);
            }
            return SentryTunnelOutcome.FORWARDED;
        } catch (RuntimeException unavailable) {
            log.warn("Sentry tunnel upstream unavailable: {}", unavailable.getClass().getSimpleName());
            return SentryTunnelOutcome.UPSTREAM_UNAVAILABLE;
        }
    }

    private static boolean envelopeBelongsTo(byte[] envelope, SentryTunnelTarget target) {
        int headerEnd = indexOfNewline(envelope);
        if (headerEnd <= 0) {
            return false;
        }
        try {
            JsonNode header = JSON.readTree(new String(Arrays.copyOf(envelope, headerEnd), StandardCharsets.UTF_8));
            JsonNode dsn = header == null ? null : header.get("dsn");
            return dsn != null && dsn.isTextual() && target.dsn().equals(dsn.asText().trim());
        } catch (JacksonException invalidHeader) {
            return false;
        }
    }

    private static int indexOfNewline(byte[] envelope) {
        for (int index = 0; index < envelope.length; index++) {
            if (envelope[index] == '\n') {
                return index;
            }
        }
        return -1;
    }
}

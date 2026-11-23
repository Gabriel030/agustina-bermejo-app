package com.red_escuela.backend.shared.errortracking;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
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
    private static final Set<String> ALLOWED_ITEM_TYPES = Set.of("event", "transaction", "session");
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
            if (status >= 300) {
                log.warn("Sentry tunnel upstream answered status={}", status);
                return SentryTunnelOutcome.UPSTREAM_REJECTED;
            }
            return SentryTunnelOutcome.FORWARDED;
        } catch (RuntimeException unavailable) {
            log.warn("Sentry tunnel upstream unavailable: {}", unavailable.getClass().getSimpleName());
            return SentryTunnelOutcome.UPSTREAM_UNAVAILABLE;
        }
    }

    private static boolean envelopeBelongsTo(byte[] envelope, SentryTunnelTarget target) {
        int headerEnd = indexOfNewline(envelope, 0);
        if (headerEnd <= 0) {
            return false;
        }
        JsonNode header = parse(envelope, 0, headerEnd);
        JsonNode dsn = header == null ? null : header.get("dsn");
        if (dsn == null || !dsn.isTextual() || !target.dsn().equals(dsn.asText().trim())) {
            return false;
        }
        return onlyAllowedItems(envelope, headerEnd + 1);
    }

    private static boolean onlyAllowedItems(byte[] envelope, int start) {
        int position = start;
        int items = 0;
        while (position < envelope.length) {
            int headerEnd = indexOfNewline(envelope, position);
            if (headerEnd < 0) {
                headerEnd = envelope.length;
            }
            if (headerEnd == position) {
                position++;
                continue;
            }
            JsonNode itemHeader = parse(envelope, position, headerEnd);
            JsonNode type = itemHeader == null ? null : itemHeader.get("type");
            if (type == null || !type.isTextual() || !ALLOWED_ITEM_TYPES.contains(type.asText())) {
                return false;
            }
            items++;
            int payloadStart = Math.min(headerEnd + 1, envelope.length);
            JsonNode length = itemHeader.get("length");
            if (length != null && length.isIntegralNumber() && length.asInt() >= 0) {
                position = payloadStart + length.asInt();
                if (position > envelope.length) {
                    return false;
                }
                if (position < envelope.length && envelope[position] == '\n') {
                    position++;
                }
            } else {
                int payloadEnd = indexOfNewline(envelope, payloadStart);
                position = payloadEnd < 0 ? envelope.length : payloadEnd + 1;
            }
        }
        return items > 0;
    }

    private static JsonNode parse(byte[] envelope, int from, int to) {
        try {
            return JSON.readTree(new String(Arrays.copyOfRange(envelope, from, to), StandardCharsets.UTF_8));
        } catch (JacksonException invalidJson) {
            return null;
        }
    }

    private static int indexOfNewline(byte[] envelope, int from) {
        for (int index = from; index < envelope.length; index++) {
            if (envelope[index] == '\n') {
                return index;
            }
        }
        return -1;
    }
}

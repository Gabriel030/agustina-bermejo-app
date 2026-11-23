package com.red_escuela.backend.shared.errortracking;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/monitoring/tunnel")
public class SentryTunnelController {

    private final SentryTunnel sentryTunnel;

    public SentryTunnelController(SentryTunnel sentryTunnel) {
        this.sentryTunnel = sentryTunnel;
    }

    @PostMapping(consumes = "*/*")
    public ResponseEntity<Void> forwardEnvelope(HttpServletRequest request) throws IOException {
        if (!sentryTunnel.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        int maxBytes = sentryTunnel.maxBytes();
        if (request.getContentLengthLong() > maxBytes) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        byte[] envelope = request.getInputStream().readNBytes(maxBytes + 1);
        return switch (sentryTunnel.forward(envelope)) {
            case FORWARDED -> ResponseEntity.noContent().build();
            case DISABLED -> ResponseEntity.notFound().build();
            case PAYLOAD_TOO_LARGE -> ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
            case REJECTED_ENVELOPE -> ResponseEntity.badRequest().build();
            case UPSTREAM_UNAVAILABLE -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        };
    }
}

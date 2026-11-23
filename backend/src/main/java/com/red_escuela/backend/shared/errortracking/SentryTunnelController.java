package com.red_escuela.backend.shared.errortracking;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Envelope reenviado", content = @Content),
        @ApiResponse(responseCode = "400", description = "Envelope inválido, de otro DSN o con items no permitidos", content = @Content),
        @ApiResponse(responseCode = "404", description = "Túnel inactivo (SENTRY_DSN vacío)", content = @Content),
        @ApiResponse(responseCode = "413", description = "Envelope mayor al tope", content = @Content),
        @ApiResponse(responseCode = "429", description = "Demasiados envíos desde la misma IP"),
        @ApiResponse(responseCode = "502", description = "El proveedor no respondió o rechazó el envelope", content = @Content)
    })
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
            case UPSTREAM_REJECTED, UPSTREAM_UNAVAILABLE -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        };
    }
}

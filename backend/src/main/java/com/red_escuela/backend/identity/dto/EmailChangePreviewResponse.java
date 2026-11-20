package com.red_escuela.backend.identity.dto;

import java.time.LocalDateTime;

public record EmailChangePreviewResponse(
        String currentEmailMasked,
        String newEmailMasked,
        String requestedByRole,
        LocalDateTime requestedAt,
        LocalDateTime expiresAt) {
}

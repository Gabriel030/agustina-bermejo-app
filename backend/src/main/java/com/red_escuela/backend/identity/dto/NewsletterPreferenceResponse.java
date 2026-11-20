package com.red_escuela.backend.identity.dto;

import java.time.LocalDateTime;

public record NewsletterPreferenceResponse(boolean optedIn, LocalDateTime updatedAt) {
}

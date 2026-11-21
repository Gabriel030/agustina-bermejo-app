package com.red_escuela.backend.academicrecord.dto;

import java.time.LocalDateTime;

public record CalendarFeedTokenResponse(String url, String token, LocalDateTime createdAt) {
}

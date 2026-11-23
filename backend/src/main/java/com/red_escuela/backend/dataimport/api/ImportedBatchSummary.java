package com.red_escuela.backend.dataimport.api;

import java.time.LocalDateTime;

public record ImportedBatchSummary(Long id, String name, LocalDateTime committedAt, long studentsPendingActivation) {
}

package com.red_escuela.backend.platform.domain;

import java.time.Instant;

public record RestoreTestStatus(Instant ranAt, RestoreTestResult result, String detail) {
}

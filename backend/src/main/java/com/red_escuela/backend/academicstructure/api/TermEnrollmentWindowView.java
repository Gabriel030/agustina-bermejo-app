package com.red_escuela.backend.academicstructure.api;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record TermEnrollmentWindowView(Long termId, boolean closedByOverride, boolean windowInverted, boolean open) {
}

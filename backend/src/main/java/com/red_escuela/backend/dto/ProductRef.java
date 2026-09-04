package com.red_escuela.backend.dto;

// Composite-identity Value Object — resolves the ambiguity of "Course #7" vs. "Program #7"
// without inventing a new global catalog id. type: COURSE|PROGRAM|SUBSCRIPTION_TIER.
public record ProductRef(String type, Long catalogId, Long enrollmentId) {
}

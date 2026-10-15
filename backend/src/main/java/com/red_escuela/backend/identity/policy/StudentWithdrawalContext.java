package com.red_escuela.backend.identity.policy;

public record StudentWithdrawalContext(Long studentId, boolean hasActiveEnrollments) {
}

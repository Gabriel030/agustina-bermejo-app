package com.red_escuela.backend.enrollment.dto;

import java.time.LocalDate;

public record WithdrawEnrollmentRequest(LocalDate effectiveDate) {
}

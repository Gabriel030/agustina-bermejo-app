package com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ApprovedSubjectLine(String name, Integer year, BigDecimal grade, LocalDate approvedOn) {
}

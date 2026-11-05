package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

import java.time.Instant;
import java.util.List;

public record RegularityVerdict(RegularityKind kind, boolean regular, List<RegularityReason> reasons,
                                boolean attendanceControlled, Instant evaluatedAt) {
}

package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

import java.util.List;

public record RegularityResponse(RegularityKind kind, Long enrollmentId, boolean regular, List<RegularityReason> reasons,
                                 boolean attendanceControlled, String billingMode) {
}

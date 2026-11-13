package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

import java.time.LocalDateTime;
import java.util.List;

public record RegularCertificateRequestResponse(Long id, Long studentId, RegularityKind kind, Long enrollmentId,
                                                RegularCertificateChannel channel, RegularRequestOutcome outcome,
                                                List<String> reasons, LocalDateTime requestedAt, Long certificateId,
                                                String rejectionReason) {
}

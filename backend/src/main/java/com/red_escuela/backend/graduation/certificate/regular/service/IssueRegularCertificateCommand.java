package com.red_escuela.backend.graduation.certificate.regular.service;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

public record IssueRegularCertificateCommand(RegularityKind kind, Long enrollmentId, boolean standingConfirmed,
                                             String standingEvidence, RegularCertificateChannel channel) {
}

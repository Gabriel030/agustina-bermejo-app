package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.OrganismKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

public record RequestCertificateExceptionCommand(RegularityKind kind, Long enrollmentId, OrganismKind organismKind,
                                                 String organismReference, String reason) {
}

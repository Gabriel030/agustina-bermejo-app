package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.regular.entity.OrganismKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestCertificateExceptionRequest(@NotNull RegularityKind kind, @NotNull Long enrollmentId, @NotNull OrganismKind organismKind,
                                                 @NotNull @Size(max = 200) String organismReference, @NotNull @Size(max = 1000) String reason) {
}

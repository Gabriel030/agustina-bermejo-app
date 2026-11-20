package com.red_escuela.backend.graduation.certificate.regular.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

import java.util.List;

public record StudentRegularCertificateStateResponse(RegularityKind kind, Long enrollmentId, StudentRegularCertificateState state,
                                                     boolean staffConfirmationRequired, @Schema(nullable = true) RegularCertificateResponse certificate,
                                                     @Schema(nullable = true) RegularCertificateRequestResponse pendingRequest,
                                                     @Schema(nullable = true) RegularUnavailabilityType unavailableType,
                                                     @Schema(nullable = true) String rejectionReason, List<String> reasons,
                                                     List<RegularCertificateResponse> history) {
}

package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

import java.util.List;

public record StudentRegularCertificateStateResponse(RegularityKind kind, Long enrollmentId, StudentRegularCertificateState state,
                                                     boolean staffConfirmationRequired, RegularCertificateResponse certificate,
                                                     RegularCertificateRequestResponse pendingRequest,
                                                     RegularUnavailabilityType unavailableType, String rejectionReason, List<String> reasons,
                                                     List<RegularCertificateResponse> history) {
}

package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.operation.DirectorApprovable;
import com.red_escuela.backend.billing.operation.ResumedFromAuthorizationRequest;

import java.time.LocalDate;
import java.util.Objects;

public record GrantStudentDiscountCommand(EnrollmentReference enrollment, StudentDiscountGrantRequest request, Long requestedByPersonId,
                                          boolean requestedByDirector, Long authorizationRequestId)
        implements DirectorApprovable, ResumedFromAuthorizationRequest<GrantStudentDiscountCommand> {

    public GrantStudentDiscountCommand {
        Objects.requireNonNull(enrollment, "enrollment");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(requestedByPersonId, "requestedByPersonId");
    }

    public static GrantStudentDiscountCommand byPerson(EnrollmentReference enrollment, StudentDiscountGrantRequest request, Long requestedByPersonId,
                                                       boolean requestedByDirector) {
        return new GrantStudentDiscountCommand(enrollment, request, requestedByPersonId, requestedByDirector, null);
    }

    @Override
    public String authorizationReason() {
        return "Descuento " + request.discountType() + " sobre la inscripción " + enrollment.id() + ": " + (request.reason() == null ? "" : request.reason().strip());
    }

    @Override
    public LocalDate authorizationExpiresOn() {
        return request.fromPeriod() == null ? null : request.fromPeriod().atDay(1);
    }

    @Override
    public GrantStudentDiscountCommand resumedFromAuthorizationRequest(Long authorizationRequestId) {
        return new GrantStudentDiscountCommand(enrollment, request, requestedByPersonId, requestedByDirector, authorizationRequestId);
    }
}

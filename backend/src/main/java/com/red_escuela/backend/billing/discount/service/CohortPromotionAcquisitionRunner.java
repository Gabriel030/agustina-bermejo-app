package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.operation.AcquireCohortPromotionCommand;
import com.red_escuela.backend.billing.discount.operation.AcquireCohortPromotionOperation;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.operation.CohortPromotionAcquisitionPort;
import com.red_escuela.backend.billing.operation.CohortPromotionAcquisitionRequest;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import org.springframework.stereotype.Component;

@Component
public class CohortPromotionAcquisitionRunner implements CohortPromotionAcquisitionPort {
    private final BillingOperationPipeline pipeline;
    private final AcquireCohortPromotionOperation operation;

    public CohortPromotionAcquisitionRunner(BillingOperationPipeline pipeline, AcquireCohortPromotionOperation operation) {
        this.pipeline = pipeline;
        this.operation = operation;
    }

    @Override
    public void acquireCohortPromotionWhenEligible(CohortPromotionAcquisitionRequest request) {
        boolean forProgram = request.programEnrollmentId() != null;
        EnrollmentReference enrollment = forProgram ? EnrollmentReference.ofProgram(request.programEnrollmentId()) : EnrollmentReference.ofCourse(request.courseEnrollmentId());
        OfferingReference offering = forProgram ? OfferingReference.ofProgram(request.programId()) : OfferingReference.ofCourse(request.courseId());
        pipeline.runOperation(operation, new AcquireCohortPromotionCommand(enrollment, offering, request.programCohortId(), request.enrollmentDate(), request.actorPersonId()));
    }
}

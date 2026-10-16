package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.operation.GrantStudentDiscountCommand;
import com.red_escuela.backend.billing.discount.operation.GrantStudentDiscountOperation;
import com.red_escuela.backend.billing.discount.operation.RevokeStudentDiscountCommand;
import com.red_escuela.backend.billing.discount.operation.RevokeStudentDiscountOperation;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class StudentDiscountOperationRunner {
    private final BillingOperationPipeline pipeline;
    private final GrantStudentDiscountOperation grantOperation;
    private final RevokeStudentDiscountOperation revokeOperation;
    private final DiscountActorResolver actorResolver;
    private final Clock clock;

    public StudentDiscountOperationRunner(BillingOperationPipeline pipeline, GrantStudentDiscountOperation grantOperation,
                                          RevokeStudentDiscountOperation revokeOperation, DiscountActorResolver actorResolver, Clock clock) {
        this.pipeline = pipeline;
        this.grantOperation = grantOperation;
        this.revokeOperation = revokeOperation;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    public BillingOperationOutcome<StudentDiscount> runGrant(EnrollmentReference enrollment, StudentDiscountGrantRequest request) {
        return pipeline.runOperation(grantOperation,
                GrantStudentDiscountCommand.byPerson(enrollment, request, actorResolver.currentPersonId(), actorResolver.currentActorIsDirector()));
    }

    public BillingOperationOutcome<StudentDiscount> runRevocation(Long studentDiscountId, String reason) {
        return pipeline.runOperation(revokeOperation,
                new RevokeStudentDiscountCommand(studentDiscountId, reason, actorResolver.currentPersonId(), actorResolver.currentActorIsDirector(), LocalDate.now(clock)));
    }
}

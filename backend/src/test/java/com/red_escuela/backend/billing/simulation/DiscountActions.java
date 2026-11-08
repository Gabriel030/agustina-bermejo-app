package com.red_escuela.backend.billing.simulation;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.usecase.GrantStudentDiscountUseCase;
import com.red_escuela.backend.billing.discount.usecase.RevokeStudentDiscountUseCase;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicReference;

public final class DiscountActions {

    private final Scenario scenario;
    private final GrantStudentDiscountUseCase grantStudentDiscount;
    private final RevokeStudentDiscountUseCase revokeStudentDiscount;

    public DiscountActions(Scenario scenario, GrantStudentDiscountUseCase grantStudentDiscount, RevokeStudentDiscountUseCase revokeStudentDiscount) {
        this.scenario = scenario;
        this.grantStudentDiscount = grantStudentDiscount;
        this.revokeStudentDiscount = revokeStudentDiscount;
    }

    public Long grantScholarship(String points, YearMonth from, YearMonth to) {
        return grant(DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, points, from, to, "Situacion economica");
    }

    public Long grantIndividual(InvoiceType invoiceType, String points, YearMonth from, YearMonth to) {
        return grant(DiscountType.INDIVIDUAL, invoiceType, points, from, to, "Hermano en la institucion");
    }

    public void revoke(Long studentDiscountId, String reason) {
        scenario.asDirector(() -> revokeStudentDiscount.revokeStudentDiscount(studentDiscountId, reason));
        scenario.assertInvariantsAfter("revokeDiscount " + studentDiscountId);
    }

    private Long grant(DiscountType type, InvoiceType invoiceType, String points, YearMonth from, YearMonth to, String reason) {
        AtomicReference<Long> id = new AtomicReference<>();
        StudentDiscountGrantRequest request = new StudentDiscountGrantRequest(type, null, new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal(points))),
                invoiceType, from, to, reason);
        scenario.asDirector(() -> {
            BillingOperationOutcome<StudentDiscount> outcome = grantStudentDiscount.grantStudentDiscount(EnrollmentReference.ofProgram(scenario.enrollmentId()), request);
            id.set(((BillingOperationOutcome.Processed<StudentDiscount>) outcome).result().getId());
        });
        scenario.assertInvariantsAfter("grantDiscount " + type + " " + points + "% " + from + ".." + to);
        return id.get();
    }
}

package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import com.red_escuela.backend.billing.advanceenrollmentfee.exception.AdvanceEnrollmentFeeNotAvailableException;
import com.red_escuela.backend.billing.api.AdvanceEnrollmentFeeView;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingRequestFactory;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotPlanningContext;
import com.red_escuela.backend.billing.planning.service.EnrollmentFeeChargeFactory;
import com.red_escuela.backend.billing.service.InvoicePlanningContextLoader;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

@Component
public class AdvanceEnrollmentFeeRequestAssembler {
    private static final Logger log = LoggerFactory.getLogger(AdvanceEnrollmentFeeRequestAssembler.class);
    private static final String ALREADY_ISSUED_CODE = "ADVANCE_FEE_ALREADY_ISSUED";
    private static final String NOT_CONFIGURED_CODE = "ADVANCE_FEE_NEXT_YEAR_NOT_CONFIGURED";
    private static final int FIRST_SLOT = 1;

    private final InvoicePlanningContextLoader contextLoader;
    private final AdvanceEnrollmentFeeAvailabilityReader availabilityReader;
    private final EnrollmentFeeChargeFactory chargeFactory = new EnrollmentFeeChargeFactory();
    private final ProgramEnrollmentFinder enrollmentFinder;
    private final Clock clock;

    public AdvanceEnrollmentFeeRequestAssembler(InvoicePlanningContextLoader contextLoader, AdvanceEnrollmentFeeAvailabilityReader availabilityReader,
                                                ProgramEnrollmentFinder enrollmentFinder, Clock clock) {
        this.contextLoader = contextLoader;
        this.availabilityReader = availabilityReader;
        this.enrollmentFinder = enrollmentFinder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdvanceEnrollmentFeeRequest assembleForStudent(Long enrollmentId, Long studentId, StatusChangeActor requestedBy) {
        return assemble(enrollmentFinder.requireOwnedBy(enrollmentId, studentId), requestedBy);
    }

    @Transactional(readOnly = true)
    public AdvanceEnrollmentFeeRequest assembleForStaff(Long enrollmentId, StatusChangeActor requestedBy) {
        return assemble(enrollmentFinder.find(enrollmentId), requestedBy);
    }

    private AdvanceEnrollmentFeeRequest assemble(ProgramEnrollment enrollment, StatusChangeActor requestedBy) {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        InvoicePlanningContextLoader.PlanningContext context = contextLoader.load(enrollment, currentMonth, today);
        ProgramInvoicePlanningFacts facts = context.facts();
        YearCycle nextCycle = facts.cycles().all().stream().filter(cycle -> cycle.start().isAfter(currentMonth)).findFirst()
                .orElseThrow(() -> new AdvanceEnrollmentFeeNotAvailableException(NOT_CONFIGURED_CODE, NOT_CONFIGURED_CODE));
        AdvanceEnrollmentFeeView availability = availabilityReader.availabilityOf(enrollment, facts, nextCycle);
        if (ALREADY_ISSUED_CODE.equals(availability.reasonCode())) {
            return AdvanceEnrollmentFeeRequest.alreadyIssued(availability.invoiceId());
        }
        if (!availability.available()) {
            throw new AdvanceEnrollmentFeeNotAvailableException(availability.reasonCode(), availability.reasonCode());
        }
        PlannedInvoice charge = chargeFactory.createEnrollmentFeeCharge(EnrollmentFeeSlotPlanningContext.of(facts, nextCycle, FIRST_SLOT, false));
        InvoiceIssuingRequest request = InvoiceIssuingRequestFactory.createAdvanceEnrollmentFeeRequest(enrollment, context.selection(), charge, requestedBy);
        log.info("Matrícula anticipada solicitada: inscripcion={}, plantilla={}, matricula={}, periodo={}", enrollment.getId(), nextCycle.templateId(), FIRST_SLOT, charge.period());
        return AdvanceEnrollmentFeeRequest.toIssue(request);
    }

    public record AdvanceEnrollmentFeeRequest(Optional<Long> existingInvoiceId, Optional<InvoiceIssuingRequest> issuing) {

        static AdvanceEnrollmentFeeRequest alreadyIssued(Long invoiceId) {
            return new AdvanceEnrollmentFeeRequest(Optional.of(invoiceId), Optional.empty());
        }

        static AdvanceEnrollmentFeeRequest toIssue(InvoiceIssuingRequest request) {
            return new AdvanceEnrollmentFeeRequest(Optional.empty(), Optional.of(request));
        }
    }
}

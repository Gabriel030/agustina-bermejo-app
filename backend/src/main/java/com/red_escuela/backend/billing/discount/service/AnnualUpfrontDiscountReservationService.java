package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.AnnualUpfrontReservation;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.LiveInvoiceDiscount;
import com.red_escuela.backend.billing.discount.domain.ScholarshipPeriodRange;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountOrigin;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.repository.AppliedStudentDiscountRepository;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

@Service
public class AnnualUpfrontDiscountReservationService {
    private static final String RESERVATION_REASON = "Reserva del descuento por pedido de pago anual";

    private static final String DISCARD_REASON = "Liberación de la reserva por descarte del pedido de pago anual";

    private static final String DEADLINE_EXPIRY_REASON = "Liberación de la reserva por vencimiento automático del pedido de pago anual";

    private final DiscountDefinitionRepository definitionRepository;
    private final StudentDiscountRepository studentDiscountRepository;
    private final AppliedStudentDiscountRepository appliedRepository;
    private final Clock clock;

    public AnnualUpfrontDiscountReservationService(DiscountDefinitionRepository definitionRepository, StudentDiscountRepository studentDiscountRepository,
                                                   AppliedStudentDiscountRepository appliedRepository, Clock clock) {
        this.definitionRepository = definitionRepository;
        this.studentDiscountRepository = studentDiscountRepository;
        this.appliedRepository = appliedRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockDiscountDefinitionsInAscendingOrder(Collection<Long> definitionIds) {
        new TreeSet<>(definitionIds).forEach(id -> definitionRepository.findByIdForUpdate(id).orElseThrow());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseReservationsOfDiscardedInvoice(Long invoiceId, StatusChangeActor actor) {
        List<LiveInvoiceDiscount> live = appliedRepository.findLiveDiscountsOfInvoice(invoiceId);
        if (live.isEmpty()) {
            return;
        }
        List<Long> studentDiscountIds = live.stream().map(LiveInvoiceDiscount::studentDiscountId).distinct().sorted().toList();
        lockDiscountDefinitionsInAscendingOrder(studentDiscountRepository.findAllById(studentDiscountIds).stream().map(StudentDiscount::getDiscountDefinitionId).toList());
        List<StudentDiscount> discounts = studentDiscountIds.stream().map(id -> studentDiscountRepository.findByIdForUpdate(id).orElseThrow()).toList();
        appliedRepository.markConsumptionReleased(live.stream().map(LiveInvoiceDiscount::invoiceDiscountId).toList(), LocalDateTime.now(clock));
        Instant now = Instant.now(clock);
        String revocationReason = actor.processName() != null ? DEADLINE_EXPIRY_REASON : DISCARD_REASON;
        discounts.stream().filter(discount -> discount.getOrigin() == StudentDiscountOrigin.ANNUAL_UPFRONT_REQUEST && discount.getStatus() == StudentDiscountStatus.ACTIVE).forEach(discount -> {
            discount.revokeStudentDiscount(revocationReason, discount.getFromPeriod(), actor, now);
            studentDiscountRepository.save(discount);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long reserveAnnualUpfrontDiscount(Long definitionId, Long programEnrollmentId, Long studentId, Currency currency, YearMonth firstPeriod, YearMonth lastPeriod, Long personId) {
        DiscountDefinition definition = definitionRepository.findById(definitionId).orElseThrow();
        StudentDiscount reserved = studentDiscountRepository.saveAndFlush(StudentDiscount.reserveAnnualUpfrontDiscount(new AnnualUpfrontReservation(programEnrollmentId, studentId,
                currency, definition, new ScholarshipPeriodRange(firstPeriod, lastPeriod), personId, RESERVATION_REASON), Instant.now(clock)));
        return reserved.getId();
    }
}

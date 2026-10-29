package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.AnnualUpfrontReservation;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.ScholarshipPeriodRange;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.shared.domain.Currency;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collection;
import java.util.TreeSet;

@Service
public class AnnualUpfrontDiscountReservationService {
    private static final String RESERVATION_REASON = "Reserva del descuento por pedido de pago anual";

    private final DiscountDefinitionRepository definitionRepository;
    private final StudentDiscountRepository studentDiscountRepository;
    private final Clock clock;

    public AnnualUpfrontDiscountReservationService(DiscountDefinitionRepository definitionRepository, StudentDiscountRepository studentDiscountRepository, Clock clock) {
        this.definitionRepository = definitionRepository;
        this.studentDiscountRepository = studentDiscountRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockDiscountDefinitionsInAscendingOrder(Collection<Long> definitionIds) {
        new TreeSet<>(definitionIds).forEach(id -> definitionRepository.findByIdForUpdate(id).orElseThrow());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long reserveAnnualUpfrontDiscount(Long definitionId, Long programEnrollmentId, Long studentId, Currency currency, YearMonth firstPeriod, YearMonth lastPeriod, Long personId) {
        DiscountDefinition definition = definitionRepository.findById(definitionId).orElseThrow();
        StudentDiscount reserved = studentDiscountRepository.saveAndFlush(StudentDiscount.reserveAnnualUpfrontDiscount(new AnnualUpfrontReservation(programEnrollmentId, studentId,
                currency, definition, new ScholarshipPeriodRange(firstPeriod, lastPeriod), personId, RESERVATION_REASON), Instant.now(clock)));
        return reserved.getId();
    }
}

package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateBillingPeriodRequest;
import com.red_escuela.backend.entity.BillingPeriod;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.repository.BillingPeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ventana del mes en la que se puede emitir la boleta de la cuota corriente —
 * hoy es informativa (no bloquea a Administración, ver spec funcional), así que
 * este service es CRUD puro sin ninguna validación que consulte el resto del
 * dominio de facturación todavía.
 */
@Service
public class BillingPeriodService {

    private final BillingPeriodRepository billingPeriodRepository;

    public BillingPeriodService(BillingPeriodRepository billingPeriodRepository) {
        this.billingPeriodRepository = billingPeriodRepository;
    }

    @Transactional
    public BillingPeriod create(CreateBillingPeriodRequest request, Person createdBy) {
        BillingPeriod period = new BillingPeriod();
        period.setDiaInicio(request.diaInicio());
        period.setDiaFin(request.diaFin());
        period.setVigenteDesde(request.vigenteDesde());
        period.setCreatedBy(createdBy);
        period.setCreatedAt(LocalDateTime.now());
        return billingPeriodRepository.save(period);
    }

    public List<BillingPeriod> list() {
        return billingPeriodRepository.findAllByOrderByVigenteDesdeDesc();
    }
}

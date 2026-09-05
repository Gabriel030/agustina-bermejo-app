package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateLateFeePolicyRequest;
import com.red_escuela.backend.entity.LateFeePolicy;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.repository.LateFeePolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Política de mora institucional única (no configurable por carrera/curso —
 * decisión de negocio explícita), versionada por fila nueva. Sin política
 * configurada todavía, {@link #vigenteEl} devuelve vacío y no se aplica ningún
 * recargo — mismo criterio que el resto del proyecto de no inventar un default
 * mágico para una regla de negocio que todavía nadie definió.
 */
@Service
public class LateFeePolicyService {

    private final LateFeePolicyRepository lateFeePolicyRepository;

    public LateFeePolicyService(LateFeePolicyRepository lateFeePolicyRepository) {
        this.lateFeePolicyRepository = lateFeePolicyRepository;
    }

    @Transactional
    public LateFeePolicy create(CreateLateFeePolicyRequest request, Person createdBy) {
        LateFeePolicy policy = new LateFeePolicy();
        policy.setGracePeriodDays(request.diasDeGracia());
        policy.setSurchargePercentagePerPeriod(request.porcentajeRecargoPorPeriodo());
        policy.setPeriodDays(request.diasPorPeriodo());
        policy.setDaysUntilCutoffRisk(request.diasParaRiesgoDeCorte());
        policy.setValidFrom(request.vigenteDesde());
        policy.setCreatedBy(createdBy);
        policy.setCreatedAt(LocalDateTime.now());
        return lateFeePolicyRepository.save(policy);
    }

    public List<LateFeePolicy> list() {
        return lateFeePolicyRepository.findAllByOrderByValidFromDesc();
    }

    public Optional<LateFeePolicy> validOn(LocalDate date) {
        return lateFeePolicyRepository.findFirstByValidFromLessThanEqualOrderByValidFromDesc(date);
    }
}

package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class RejectCycleService implements RejectCycleUseCase {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final CurrentPersonService currentPersonService;

    public RejectCycleService(AcademicOfferingCycleRepository cycleRepository, CurrentPersonService currentPersonService) {
        this.cycleRepository = cycleRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public void reject(Long cycleId, String reason) {
        var cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
        cycle.reject(currentPersonService.resolveCurrentPerson(), reason, LocalDateTime.now());
        cycleRepository.save(cycle);
    }
}

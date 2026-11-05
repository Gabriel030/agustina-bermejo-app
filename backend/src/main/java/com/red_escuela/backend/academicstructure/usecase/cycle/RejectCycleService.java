package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.service.AcademicOfferingCycleReviewer;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RejectCycleService implements RejectCycleUseCase {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final AcademicOfferingCycleReviewer cycleReviewer;

    public RejectCycleService(AcademicOfferingCycleRepository cycleRepository, AcademicOfferingCycleReviewer cycleReviewer) {
        this.cycleRepository = cycleRepository;
        this.cycleReviewer = cycleReviewer;
    }

    @Override
    @Transactional
    public void reject(Long cycleId, String reason) {
        var cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
        cycleReviewer.reject(cycle, reason);
        cycleRepository.save(cycle);
    }
}

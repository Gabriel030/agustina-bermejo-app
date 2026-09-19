package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@Transactional(readOnly = true)
public class ListAcademicOfferingCyclesService implements ListAcademicOfferingCyclesUseCase {

    private final AcademicOfferingCycleRepository cycleRepository;

    public ListAcademicOfferingCyclesService(AcademicOfferingCycleRepository cycleRepository) {
        this.cycleRepository = cycleRepository;
    }

    @Override
    public List<AcademicOfferingCycleResponse> list(Long programId, Long courseId, AcademicOfferingCycleStatus status) {
        if (programId != null) {
            return cycleRepository.findByProgramIdOrderByCreatedAtDesc(programId)
                    .stream().map(AcademicOfferingCycleResponse::from).toList();
        }
        if (courseId != null) {
            return cycleRepository.findByCourseIdOrderByCreatedAtDesc(courseId)
                    .stream().map(AcademicOfferingCycleResponse::from).toList();
        }
        if (status != null) {
            return cycleRepository.findByStatus(status)
                    .stream().map(AcademicOfferingCycleResponse::from).toList();
        }
        return cycleRepository.findAll()
                .stream().map(AcademicOfferingCycleResponse::from).toList();
    }
}

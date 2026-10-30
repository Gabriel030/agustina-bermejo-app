package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.InstructorRole;
import com.red_escuela.backend.academicstructure.exception.CycleIncompleteException;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class SubmitCycleForApprovalService implements SubmitCycleForApprovalUseCase {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;

    public SubmitCycleForApprovalService(
            AcademicOfferingCycleRepository cycleRepository,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSectionRepository
    ) {
        this.cycleRepository = cycleRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
    }

    @Override
    @Transactional
    public void submit(Long cycleId) {
        var cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
        cycle.ensureEditable();

        var subjectSections = subjectSectionRepository.findByAcademicOfferingCycleId(cycleId);
        var courseSections = courseSectionRepository.findByAcademicOfferingCycleId(cycleId);

        if (subjectSections.isEmpty() && courseSections.isEmpty()) {
            throw new CycleIncompleteException("el ciclo no tiene comisiones");
        }

        boolean allHaveLead = subjectSections.stream()
                .allMatch(s -> s.getInstructors().stream().anyMatch(i -> i.getRole() == InstructorRole.LEAD));
        if (!allHaveLead) {
            throw new CycleIncompleteException("hay comisiones sin docente titular asignado");
        }

        boolean allCourseSectionsHaveLead = courseSections.stream()
                .allMatch(s -> s.getInstructors().stream().anyMatch(i -> i.getRole() == InstructorRole.LEAD));
        if (!allCourseSectionsHaveLead) {
            throw new CycleIncompleteException("hay comisiones sin docente titular asignado");
        }

        cycle.submitForApproval();
        cycleRepository.save(cycle);
    }
}

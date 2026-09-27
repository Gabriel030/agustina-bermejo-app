package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;
import com.red_escuela.backend.academicstructure.dto.CreateAcademicOfferingCycleRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.exception.CycleAlreadyInProgressException;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@UseCase
public class CreateAcademicOfferingCycleService implements CreateAcademicOfferingCycleUseCase {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final TermRepository termRepository;
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;
    private final CurrentPersonService currentPersonService;

    public CreateAcademicOfferingCycleService(
            AcademicOfferingCycleRepository cycleRepository,
            TermRepository termRepository,
            ProgramRepository programRepository,
            CourseRepository courseRepository,
            CurrentPersonService currentPersonService
    ) {
        this.cycleRepository = cycleRepository;
        this.termRepository = termRepository;
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public AcademicOfferingCycleResponse create(CreateAcademicOfferingCycleRequest request) {
        var term = termRepository.findById(request.termId())
                .orElseThrow(() -> new TermNotFoundException(request.termId()));

        var cycle = new AcademicOfferingCycle();
        cycle.setTerm(term);
        cycle.setCreatedBy(currentPersonService.resolveCurrentPerson());
        cycle.setCreatedAt(LocalDateTime.now());

        if (request.programId() != null) {
            var program = programRepository.findById(request.programId())
                    .orElseThrow(() -> new ProgramNotFoundException(request.programId()));
            var active = cycleRepository.findByProgramIdOrderByCreatedAtDesc(request.programId())
                    .stream()
                    .filter(c -> c.getTerm().getId().equals(request.termId()))
                    .filter(c -> c.getStatus() == AcademicOfferingCycleStatus.DRAFT || c.getStatus() == AcademicOfferingCycleStatus.PENDING_APPROVAL)
                    .findFirst();
            if (active.isPresent()) throw new CycleAlreadyInProgressException();
            cycle.setProgram(program);
        } else {
            var course = courseRepository.findById(request.courseId())
                    .orElseThrow(() -> new CourseNotFoundException(request.courseId()));
            var active = cycleRepository.findByCourseIdOrderByCreatedAtDesc(request.courseId())
                    .stream()
                    .filter(c -> c.getTerm().getId().equals(request.termId()))
                    .filter(c -> c.getStatus() == AcademicOfferingCycleStatus.DRAFT || c.getStatus() == AcademicOfferingCycleStatus.PENDING_APPROVAL)
                    .findFirst();
            if (active.isPresent()) throw new CycleAlreadyInProgressException();
            cycle.setCourse(course);
        }

        return AcademicOfferingCycleResponse.from(cycleRepository.save(cycle));
    }
}

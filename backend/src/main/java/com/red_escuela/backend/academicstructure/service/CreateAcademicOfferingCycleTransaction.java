package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.domain.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CycleAlreadyInProgressException;
import com.red_escuela.backend.academicstructure.domain.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.api.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateAcademicOfferingCycleTransaction {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final TermRepository termRepository;
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;
    private final AcademicOfferingCycleDrafter cycleDrafter;
    private final HolidayYearGuard holidayYearGuard;
    private final TermOfferingGuard termOfferingGuard;

    public CreateAcademicOfferingCycleTransaction(
            AcademicOfferingCycleRepository cycleRepository,
            TermRepository termRepository,
            ProgramRepository programRepository,
            CourseRepository courseRepository,
            AcademicOfferingCycleDrafter cycleDrafter,
            HolidayYearGuard holidayYearGuard,
            TermOfferingGuard termOfferingGuard
    ) {
        this.cycleRepository = cycleRepository;
        this.termRepository = termRepository;
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
        this.cycleDrafter = cycleDrafter;
        this.holidayYearGuard = holidayYearGuard;
        this.termOfferingGuard = termOfferingGuard;
    }

    @Transactional
    public AcademicOfferingCycleResponse create(Long programId, Long courseId, Long termId) {
        var term = termRepository.findById(termId)
                .orElseThrow(() -> new TermNotFoundException(termId));

        termOfferingGuard.requireTermOpenForOffering(term);
        holidayYearGuard.ensureHolidaysLoadedForTerm(term);

        var cycle = cycleDrafter.draftFor(term);

        if (programId != null) {
            var program = programRepository.findById(programId)
                    .orElseThrow(() -> new ProgramNotFoundException(programId));
            var active = cycleRepository.findByProgramIdOrderByCreatedAtDesc(programId)
                    .stream()
                    .filter(c -> c.getTerm().getId().equals(termId))
                    .filter(c -> c.getStatus() != AcademicOfferingCycleStatus.REJECTED)
                    .findFirst();
            if (active.isPresent()) throw new CycleAlreadyInProgressException();
            cycle.setProgram(program);
        } else {
            var course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new CourseNotFoundException(courseId));
            var active = cycleRepository.findByCourseIdOrderByCreatedAtDesc(courseId)
                    .stream()
                    .filter(c -> c.getTerm().getId().equals(termId))
                    .filter(c -> c.getStatus() != AcademicOfferingCycleStatus.REJECTED)
                    .findFirst();
            if (active.isPresent()) throw new CycleAlreadyInProgressException();
            cycle.setCourse(course);
        }

        try {
            return AcademicOfferingCycleResponse.from(cycleRepository.saveAndFlush(cycle), 0L);
        } catch (DataIntegrityViolationException violation) {
            throw new CycleAlreadyInProgressException();
        }
    }
}

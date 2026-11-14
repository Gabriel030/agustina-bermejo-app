package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.ProgramSectionCandidateView;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionScheduleFinder;
import com.red_escuela.backend.academicstructure.api.SectionSlotView;
import com.red_escuela.backend.enrollment.dto.ChangeSectionCandidateResponse;
import com.red_escuela.backend.enrollment.dto.ChangeSectionCandidateResponse.BlockReason;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.SectionScheduleOverlapException;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class ChangeSectionCandidatesFinder {

    private static final List<EnrollmentStatus> SEAT_HOLDING_STATUSES =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR);

    private final SubjectSectionEnrollmentRepository enrollmentRepository;
    private final SectionFinder sectionFinder;
    private final SectionScheduleFinder sectionScheduleFinder;
    private final StudentScheduleOverlapGuard scheduleOverlapGuard;

    public ChangeSectionCandidatesFinder(SubjectSectionEnrollmentRepository enrollmentRepository, SectionFinder sectionFinder,
            SectionScheduleFinder sectionScheduleFinder, StudentScheduleOverlapGuard scheduleOverlapGuard) {
        this.enrollmentRepository = enrollmentRepository;
        this.sectionFinder = sectionFinder;
        this.sectionScheduleFinder = sectionScheduleFinder;
        this.scheduleOverlapGuard = scheduleOverlapGuard;
    }

    public List<ChangeSectionCandidateResponse> findCandidates(Long sectionEnrollmentId) {
        SubjectSectionEnrollment origin = enrollmentRepository.findById(sectionEnrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(sectionEnrollmentId));
        return sectionFinder.findProgramSectionCandidates(origin.subjectId()).stream()
                .map(candidate -> toResponse(origin, candidate))
                .toList();
    }

    private ChangeSectionCandidateResponse toResponse(SubjectSectionEnrollment origin, ProgramSectionCandidateView candidate) {
        long occupied = enrollmentRepository.countBySubjectSectionIdAndStatusIn(candidate.id(), SEAT_HOLDING_STATUSES);
        Long available = candidate.capacity() == null ? null : Math.max(0L, candidate.capacity() - occupied);
        List<SectionSlotView> slots = sectionScheduleFinder.findProgramSectionSlots(candidate.id());
        Optional<SectionScheduleOverlapException> overlap = Optional.empty();
        BlockReason reason = null;
        if (candidate.id().equals(origin.subjectSectionId())) {
            reason = BlockReason.CURRENT_SECTION;
        } else if (!candidate.openForEnrollment()) {
            reason = BlockReason.SECTION_NOT_OPEN;
        } else if (!candidate.termId().equals(origin.termId())) {
            reason = BlockReason.OTHER_TERM;
        } else if (available != null && available == 0L) {
            reason = BlockReason.SECTION_FULL;
        } else {
            overlap = scheduleOverlapGuard.findOverlapForProgramSection(origin.getStudent().getId(), candidate.id(), candidate.termId(), origin.getId());
            if (overlap.isPresent()) {
                reason = BlockReason.SCHEDULE_OVERLAP;
            }
        }
        return new ChangeSectionCandidateResponse(candidate.id(), candidate.name(), candidate.capacity(), occupied, available, slots,
                reason == null, reason, overlap.map(Throwable::getMessage).orElse(detailOf(reason)),
                overlap.map(SectionScheduleOverlapException::conflictingSubjectName).orElse(null));
    }

    private static String detailOf(BlockReason reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case CURRENT_SECTION -> "Es la comisión actual del alumno";
            case SECTION_NOT_OPEN -> "La comisión no está abierta a inscripción";
            case OTHER_TERM -> "La comisión es de otro período";
            case SECTION_FULL -> "La comisión no tiene cupo";
            case SCHEDULE_OVERLAP -> null;
        };
    }
}

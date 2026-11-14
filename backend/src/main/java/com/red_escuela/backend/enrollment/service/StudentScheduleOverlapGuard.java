package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.SectionScheduleFinder;
import com.red_escuela.backend.academicstructure.api.SectionSlotView;
import com.red_escuela.backend.academicstructure.api.TermPeriodFinder;
import com.red_escuela.backend.academicstructure.api.TermPeriodView;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionScheduleOverlapException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class StudentScheduleOverlapGuard {

    private static final List<EnrollmentStatus> ATTENDING_STATUSES = List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS);
    private static final Locale SPANISH = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final SubjectSectionEnrollmentRepository subjectEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository;
    private final SectionScheduleFinder sectionScheduleFinder;
    private final TermPeriodFinder termPeriodFinder;

    public StudentScheduleOverlapGuard(SubjectSectionEnrollmentRepository subjectEnrollmentRepository,
            CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository, SectionScheduleFinder sectionScheduleFinder,
            TermPeriodFinder termPeriodFinder) {
        this.subjectEnrollmentRepository = subjectEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.sectionScheduleFinder = sectionScheduleFinder;
        this.termPeriodFinder = termPeriodFinder;
    }

    public void requireNoOverlapForProgramSection(Long studentId, Long targetSectionId, Long targetTermId, Long ignoredEnrollmentId) {
        findOverlapForProgramSection(studentId, targetSectionId, targetTermId, ignoredEnrollmentId).ifPresent(overlap -> {
            throw overlap;
        });
    }

    public Optional<SectionScheduleOverlapException> findOverlapForProgramSection(Long studentId, Long targetSectionId, Long targetTermId, Long ignoredEnrollmentId) {
        List<SectionSlotView> targetSlots = sectionScheduleFinder.findProgramSectionSlots(targetSectionId);
        if (targetSlots.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, TermPeriodView> periods = new HashMap<>();
        TermPeriodView targetPeriod = periods.computeIfAbsent(targetTermId, termPeriodFinder::findPeriod);
        for (SubjectSectionEnrollment other : subjectEnrollmentRepository.findByStudentId(studentId)) {
            if (other.getId().equals(ignoredEnrollmentId) || !ATTENDING_STATUSES.contains(other.getStatus())
                    || other.getSubjectSection().getId().equals(targetSectionId)) {
                continue;
            }
            TermPeriodView otherPeriod = periods.computeIfAbsent(other.termId(), termPeriodFinder::findPeriod);
            if (targetPeriod.overlaps(otherPeriod)) {
                Optional<SectionScheduleOverlapException> overlap = findOverlap(targetSlots, sectionScheduleFinder.findProgramSectionSlots(other.getSubjectSection().getId()),
                        other.getSubjectSection().getSubject().getName());
                if (overlap.isPresent()) {
                    return overlap;
                }
            }
        }
        for (CourseSubjectSectionEnrollment other : courseEnrollmentRepository.findByStudentIdAndStatusInOrderByIdAsc(studentId, ATTENDING_STATUSES)) {
            Long otherTermId = other.getCourseSubjectSection().getTerm().getId();
            TermPeriodView otherPeriod = periods.computeIfAbsent(otherTermId, termPeriodFinder::findPeriod);
            if (targetPeriod.overlaps(otherPeriod)) {
                Optional<SectionScheduleOverlapException> overlap = findOverlap(targetSlots, sectionScheduleFinder.findCourseSectionSlots(other.getCourseSubjectSection().getId()),
                        other.getCourseSubjectSection().getModuleName());
                if (overlap.isPresent()) {
                    return overlap;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<SectionScheduleOverlapException> findOverlap(List<SectionSlotView> targetSlots, List<SectionSlotView> otherSlots, String otherSubjectName) {
        for (SectionSlotView target : targetSlots) {
            for (SectionSlotView other : otherSlots) {
                boolean sameDay = target.dayOfWeek() == other.dayOfWeek();
                boolean overlapping = target.startTime().isBefore(other.endTime()) && other.startTime().isBefore(target.endTime());
                if (sameDay && overlapping) {
                    return Optional.of(new SectionScheduleOverlapException(otherSubjectName, other.dayOfWeek().getDisplayName(TextStyle.FULL, SPANISH),
                            other.startTime().format(TIME), other.endTime().format(TIME)));
                }
            }
        }
        return Optional.empty();
    }
}

package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.events.ProgramEnrollmentWithdrawnEvent;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProgramEnrollmentSectionWithdrawer {

    private static final List<EnrollmentStatus> OPEN_STATUSES =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR, EnrollmentStatus.WAITLISTED);

    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SectionFinder sectionFinder;
    private final SectionWaitlistPromoter waitlistPromoter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ProgramEnrollmentSectionWithdrawer(SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
                                              ProgramEnrollmentRepository programEnrollmentRepository,
                                              SectionFinder sectionFinder,
                                              SectionWaitlistPromoter waitlistPromoter,
                                              ApplicationEventPublisher eventPublisher,
                                              Clock clock) {
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.sectionFinder = sectionFinder;
        this.waitlistPromoter = waitlistPromoter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void withdrawOpenEnrollmentsOf(Long programEnrollmentId) {
        ProgramEnrollment programEnrollment = programEnrollmentRepository.findByIdForUpdate(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        List<SubjectSectionEnrollment> open = subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndStatusIn(programEnrollmentId, OPEN_STATUSES);
        open.stream()
                .map(SubjectSectionEnrollment::subjectSectionId)
                .distinct()
                .sorted()
                .forEach(sectionFinder::lockProgramSection);
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> sectionsWithFreedSeat = new ArrayList<>();
        for (SubjectSectionEnrollment enrollment : open) {
            if (enrollment.getStatus() != EnrollmentStatus.WAITLISTED) {
                sectionsWithFreedSeat.add(enrollment.subjectSectionId());
            }
            enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
            enrollment.setDroppedAt(now);
            subjectSectionEnrollmentRepository.save(enrollment);
        }
        sectionsWithFreedSeat.stream().distinct().sorted().forEach(waitlistPromoter::promoteNextFromWaitlist);
        eventPublisher.publishEvent(ProgramEnrollmentWithdrawnEvent.withdrawn(
                programEnrollment.getStudent().getId(), programEnrollmentId));
    }
}

package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.events.ProgramEnrollmentWithdrawnEvent;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
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
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVAL_PENDING, EnrollmentStatus.WAITLISTED);

    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SectionFinder sectionFinder;
    private final SectionWaitlistPromoter waitlistPromoter;
    private final ApplicationEventPublisher eventPublisher;
    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final Clock clock;

    public ProgramEnrollmentSectionWithdrawer(SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
                                              ProgramEnrollmentRepository programEnrollmentRepository,
                                              SectionFinder sectionFinder,
                                              SectionWaitlistPromoter waitlistPromoter,
                                              ApplicationEventPublisher eventPublisher,
                                              DirectorApprovalRequestRepository directorApprovalRequestRepository,
                                              Clock clock) {
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.sectionFinder = sectionFinder;
        this.waitlistPromoter = waitlistPromoter;
        this.eventPublisher = eventPublisher;
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
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
        directorApprovalRequestRepository.markAllApprovedConsumed(programEnrollmentId, ExceptionType.LATE_EXAM, now);
        sectionsWithFreedSeat.stream().distinct().sorted().forEach(waitlistPromoter::promoteNextFromWaitlist);
        eventPublisher.publishEvent(ProgramEnrollmentWithdrawnEvent.withdrawn(
                programEnrollment.getStudent().getId(), programEnrollmentId));
    }
}

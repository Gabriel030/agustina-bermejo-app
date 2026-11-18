package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.events.WaitlistEntryExpiredEvent;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SectionWaitlistExpiration {

    private final WaitlistQueues waitlistQueues;
    private final SubjectSectionEnrollmentRepository programRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSectionRepository;
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher eventPublisher;

    public SectionWaitlistExpiration(WaitlistQueues waitlistQueues, SubjectSectionEnrollmentRepository programRepository,
                                     CourseSubjectSectionEnrollmentRepository courseSectionRepository, AuditTrail auditTrail,
                                     ApplicationEventPublisher eventPublisher) {
        this.waitlistQueues = waitlistQueues;
        this.programRepository = programRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.auditTrail = auditTrail;
        this.eventPublisher = eventPublisher;
    }

    public List<Long> findProgramSectionIdsToExpire(LocalDateTime now) {
        return programRepository.findWaitlistedQueueIdsPastEnrollmentEnd(now, EnrollmentStatus.WAITLISTED);
    }

    public List<Long> findCourseSectionIdsToExpire(LocalDateTime now) {
        return courseSectionRepository.findWaitlistedQueueIdsPastEnrollmentEnd(now, EnrollmentStatus.WAITLISTED);
    }

    @Transactional
    public void expireProgramSection(Long sectionId, LocalDateTime now) {
        WaitlistQueue queue = waitlistQueues.of(WaitlistKind.PROGRAM_SECTION);
        queue.lock(sectionId);
        List<SubjectSectionEnrollment> waitlisted = programRepository.findBySubjectSectionIdAndStatusIn(sectionId, List.of(EnrollmentStatus.WAITLISTED));
        if (waitlisted.isEmpty() || !waitlisted.get(0).getSubjectSection().getTerm().hasEnrollmentEndedAt(now)) {
            return;
        }
        String queueName = queueNameOf(queue, sectionId);
        for (SubjectSectionEnrollment enrollment : waitlisted) {
            enrollment.setStatus(EnrollmentStatus.WAITLIST_EXPIRED);
            programRepository.save(enrollment);
            recordExpiry(queue, enrollment.getId(), enrollment.getStudent().getPerson().getId(), queueName);
        }
    }

    @Transactional
    public void expireCourseSection(Long sectionId, LocalDateTime now) {
        WaitlistQueue queue = waitlistQueues.of(WaitlistKind.COURSE_SECTION);
        queue.lock(sectionId);
        List<CourseSubjectSectionEnrollment> waitlisted = courseSectionRepository.findByCourseSubjectSectionIdAndStatusIn(sectionId, List.of(EnrollmentStatus.WAITLISTED));
        if (waitlisted.isEmpty() || !waitlisted.get(0).getCourseSubjectSection().getTerm().hasEnrollmentEndedAt(now)) {
            return;
        }
        String queueName = queueNameOf(queue, sectionId);
        for (CourseSubjectSectionEnrollment enrollment : waitlisted) {
            enrollment.setStatus(EnrollmentStatus.WAITLIST_EXPIRED);
            courseSectionRepository.save(enrollment);
            recordExpiry(queue, enrollment.getId(), enrollment.getStudent().getPerson().getId(), queueName);
        }
    }

    private String queueNameOf(WaitlistQueue queue, Long queueId) {
        return queue.findQueues(queueId, null).stream().findFirst().map(WaitlistQueueSnapshot::name).orElse("");
    }

    private void recordExpiry(WaitlistQueue queue, Long enrollmentId, Long studentPersonId, String queueName) {
        auditTrail.record(new AuditRecord(AuditAction.WAITLIST_EXPIRED, queue.auditEntityType(), enrollmentId,
                "WAITLISTED", "WAITLIST_EXPIRED", null));
        eventPublisher.publishEvent(WaitlistEntryExpiredEvent.of(enrollmentId, studentPersonId, queueName));
    }
}

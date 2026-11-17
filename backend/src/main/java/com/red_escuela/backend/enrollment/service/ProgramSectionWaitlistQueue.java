package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.events.SubjectWaitlistPromotedEvent;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.WaitlistEnrollmentStudentRow;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Transactional
public class ProgramSectionWaitlistQueue implements WaitlistQueue {

    private static final List<EnrollmentStatus> OCCUPYING =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR);

    private final SubjectSectionEnrollmentRepository repository;
    private final SectionFinder sectionFinder;
    private final ApplicationEventPublisher eventPublisher;

    public ProgramSectionWaitlistQueue(SubjectSectionEnrollmentRepository repository, SectionFinder sectionFinder,
                                       ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.sectionFinder = sectionFinder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public WaitlistKind kind() {
        return WaitlistKind.PROGRAM_SECTION;
    }

    @Override
    public AuditEntityType auditEntityType() {
        return AuditEntityType.SECTION_ENROLLMENT;
    }

    @Override
    public List<WaitlistQueueSnapshot> findQueues(Long queueId, Long termId) {
        return repository.findWaitlistQueueRows(queueId, termId, OCCUPYING, EnrollmentStatus.WAITLISTED).stream()
                .map(row -> new WaitlistQueueSnapshot(row.getId(), row.getName(),
                        WaitlistQueueTermName.of(row.getTermDescription(), row.getTermYear()),
                        row.getCapacity(), row.getOccupied(), row.getWaiting(), null))
                .toList();
    }

    @Override
    public List<WaitlistCandidate> findWaitingCandidates(Long queueId) {
        return repository.findWaitlistEntryRows(queueId, EnrollmentStatus.WAITLISTED).stream()
                .map(row -> new WaitlistCandidate(kind(), row.getEnrollmentId(), row.getStudentId(), row.getFullName(),
                        row.getDocumentNumber(), row.getJoinedAt(), row.getProgramEnrollmentId(), row.getSubjectId(),
                        row.getSectionId(), row.getTermId(), row.getPrioritizedAt()))
                .toList();
    }

    @Override
    public Map<Long, Long> findStudentIdByEnrollmentId(Long queueId) {
        return repository.findWaitlistEnrollmentStudentRows(queueId).stream()
                .collect(Collectors.toMap(WaitlistEnrollmentStudentRow::getEnrollmentId, WaitlistEnrollmentStudentRow::getStudentId));
    }

    @Override
    public void lock(Long queueId) {
        sectionFinder.lockProgramSection(queueId);
    }

    @Override
    public boolean isOpenForPromotion(Long queueId) {
        return sectionFinder.isProgramSectionOpenForEnrollment(queueId);
    }

    @Override
    public void promote(Long enrollmentId) {
        SubjectSectionEnrollment next = repository.findById(enrollmentId).orElseThrow();
        next.setStatus(EnrollmentStatus.ENROLLED);
        repository.save(next);
        eventPublisher.publishEvent(SubjectWaitlistPromotedEvent.of(next.getId(), next.getStudent().getPerson().getId(),
                next.getSubjectSection().getSubject().getName(), next.getSubjectSection().getName()));
    }
}

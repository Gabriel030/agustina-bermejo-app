package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.WaitlistEnrollmentStudentRow;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Transactional
public class CourseSectionWaitlistQueue implements WaitlistQueue {

    private static final List<EnrollmentStatus> OCCUPYING =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR);

    private final CourseSubjectSectionEnrollmentRepository repository;
    private final SectionFinder sectionFinder;

    public CourseSectionWaitlistQueue(CourseSubjectSectionEnrollmentRepository repository, SectionFinder sectionFinder) {
        this.repository = repository;
        this.sectionFinder = sectionFinder;
    }

    @Override
    public WaitlistKind kind() {
        return WaitlistKind.COURSE_SECTION;
    }

    @Override
    public AuditEntityType auditEntityType() {
        return AuditEntityType.COURSE_SECTION_ENROLLMENT;
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
                        row.getDocumentNumber(), row.getJoinedAt(), null, null, null, null, row.getPrioritizedAt()))
                .toList();
    }

    @Override
    public Map<Long, Long> findStudentIdByEnrollmentId(Long queueId) {
        return repository.findWaitlistEnrollmentStudentRows(queueId).stream()
                .collect(Collectors.toMap(WaitlistEnrollmentStudentRow::getEnrollmentId, WaitlistEnrollmentStudentRow::getStudentId));
    }

    @Override
    public void lock(Long queueId) {
        sectionFinder.lockCourseSection(queueId);
    }

    @Override
    public boolean isOpenForPromotion(Long queueId) {
        return true;
    }

    @Override
    public void promote(Long enrollmentId) {
        CourseSubjectSectionEnrollment next = repository.findById(enrollmentId).orElseThrow();
        next.setStatus(EnrollmentStatus.ENROLLED);
        repository.save(next);
    }

    @Override
    public Optional<WaitlistEntryOwner> findOwner(Long enrollmentId) {
        return repository.findById(enrollmentId)
                .map(e -> new WaitlistEntryOwner(e.getCourseSubjectSection().getId(), e.getStudent().getPerson().getId()));
    }

    @Override
    public void markPrioritized(Long enrollmentId, LocalDateTime at) {
        CourseSubjectSectionEnrollment entry = repository.findById(enrollmentId).orElseThrow();
        entry.setWaitlistPrioritizedAt(at);
        repository.save(entry);
    }

    @Override
    public void remove(Long enrollmentId) {
        CourseSubjectSectionEnrollment entry = repository.findById(enrollmentId).orElseThrow();
        entry.setStatus(EnrollmentStatus.WITHDRAWN);
        repository.save(entry);
    }
}

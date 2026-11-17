package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.WaitlistEnrollmentStudentRow;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class CourseSectionWaitlistQueue implements WaitlistQueue {

    private static final List<EnrollmentStatus> OCCUPYING =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR);

    private final CourseSubjectSectionEnrollmentRepository repository;

    public CourseSectionWaitlistQueue(CourseSubjectSectionEnrollmentRepository repository) {
        this.repository = repository;
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
                        row.getDocumentNumber(), row.getJoinedAt(), null, null, null, null))
                .toList();
    }

    @Override
    public Map<Long, Long> findStudentIdByEnrollmentId(Long queueId) {
        return repository.findWaitlistEnrollmentStudentRows(queueId).stream()
                .collect(Collectors.toMap(WaitlistEnrollmentStudentRow::getEnrollmentId, WaitlistEnrollmentStudentRow::getStudentId));
    }
}

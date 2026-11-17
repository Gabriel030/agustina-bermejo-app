package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.CourseWaitlistQueueRow;
import com.red_escuela.backend.enrollment.repository.WaitlistEnrollmentStudentRow;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Transactional
public class CourseWaitlistQueue implements WaitlistQueue {

    private static final List<EnrollmentStatus> OCCUPYING = List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS);

    private final CourseEnrollmentRepository repository;
    private final CourseCatalogFinder courseCatalogFinder;

    public CourseWaitlistQueue(CourseEnrollmentRepository repository, CourseCatalogFinder courseCatalogFinder) {
        this.repository = repository;
        this.courseCatalogFinder = courseCatalogFinder;
    }

    @Override
    public WaitlistKind kind() {
        return WaitlistKind.COURSE;
    }

    @Override
    public AuditEntityType auditEntityType() {
        return AuditEntityType.COURSE_ENROLLMENT;
    }

    @Override
    public List<WaitlistQueueSnapshot> findQueues(Long queueId, Long termId) {
        return repository.findWaitlistQueueRows(queueId, termId, OCCUPYING, EnrollmentStatus.WAITLISTED).stream()
                .map(row -> new WaitlistQueueSnapshot(row.getId(), row.getName(),
                        WaitlistQueueTermName.of(row.getTermDescription(), row.getTermYear()),
                        row.getCapacity(), row.getOccupied(), row.getWaiting(), deadlineOf(row)))
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

    private LocalDate deadlineOf(CourseWaitlistQueueRow row) {
        if (row.getWaitlistDeadlineOffsetDays() == null || row.getTermStart() == null) {
            return null;
        }
        return row.getTermStart().toLocalDate().plusDays(row.getWaitlistDeadlineOffsetDays());
    }

    @Override
    public void lock(Long queueId) {
        courseCatalogFinder.lockCourse(queueId);
    }

    @Override
    public boolean isOpenForPromotion(Long queueId) {
        return true;
    }

    @Override
    public void promote(Long enrollmentId) {
        CourseEnrollment next = repository.findById(enrollmentId).orElseThrow();
        next.setStatus(EnrollmentStatus.ENROLLED);
        repository.save(next);
    }
}

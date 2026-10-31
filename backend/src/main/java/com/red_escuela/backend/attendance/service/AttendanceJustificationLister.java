package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.AttendanceJustificationPage;
import com.red_escuela.backend.attendance.dto.AttendanceJustificationResponse;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.domain.AttendanceJustificationStatus;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceJustificationLister {

    static final int MAX_PAGE_SIZE = 100;

    private final AttendanceRepository attendanceRepository;

    public AttendanceJustificationLister(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional(readOnly = true)
    public AttendanceJustificationPage listByStatus(AttendanceJustificationStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("classDate").ascending().and(Sort.by("id").ascending()));
        Page<Attendance> found = switch (status) {
            case PENDING -> attendanceRepository.findByStatus(AttendanceStatus.JUSTIFICATION_PENDING, pageable);
            case APPROVED -> attendanceRepository.findByStatusAndJustificationReasonIsNotNull(AttendanceStatus.JUSTIFIED, pageable);
            case REJECTED -> attendanceRepository.findByStatusAndJustificationRejectionReasonIsNotNull(AttendanceStatus.ABSENT, pageable);
        };
        return new AttendanceJustificationPage(found.getContent().stream().map(a -> toResponse(a, status)).toList(), found.getTotalElements());
    }

    private AttendanceJustificationResponse toResponse(Attendance attendance, AttendanceJustificationStatus status) {
        var program = attendance.getProgramSubjectSectionEnrollment();
        var student = program != null ? program.getStudent().getPerson() : attendance.getCourseSubjectSectionEnrollment().getStudent().getPerson();
        String subjectName = program != null ? program.getSubjectSection().getSubject().getName()
                : attendance.getCourseSubjectSectionEnrollment().getCourseSubjectSection().getCourseSubject().getName();
        String sectionName = program != null ? program.getSubjectSection().getName()
                : attendance.getCourseSubjectSectionEnrollment().getCourseSubjectSection().getName();
        var decidedAt = status == AttendanceJustificationStatus.REJECTED ? attendance.getJustificationRejectedAt()
                : status == AttendanceJustificationStatus.APPROVED ? attendance.getJustifiedAt() : null;
        return new AttendanceJustificationResponse(attendance.getId(), student.getName() + " " + student.getLastname(), subjectName,
                sectionName, attendance.getClassDate(), attendance.getJustificationReason(), attendance.getJustificationAttachmentUrl(),
                status.name(), attendance.getJustificationRejectionReason(), decidedAt);
    }
}

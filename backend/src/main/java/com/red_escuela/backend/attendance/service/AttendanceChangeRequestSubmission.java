package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceChangeRequestSubmission {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final AttendanceChangeRequestDrafter changeRequestDrafter;

    public AttendanceChangeRequestSubmission(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
                                             AttendanceRepository attendanceRepository,
                                             AttendanceSectionAccess sectionAccess,
                                             AttendanceChangeRequestDrafter changeRequestDrafter) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.attendanceRepository = attendanceRepository;
        this.sectionAccess = sectionAccess;
        this.changeRequestDrafter = changeRequestDrafter;
    }

    @Transactional
    public AttendanceChangeRequest submit(Long attendanceId, Long requestedById, CreateAttendanceChangeRequestRequest body) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new AttendanceNotFoundException(attendanceId));
        Long sectionId = sectionAccess.sectionIdOf(attendance);
        if (attendance.getProgramSubjectSectionEnrollment() != null) {
            sectionAccess.requireOwnProgramSection(sectionId, requestedById);
        } else {
            sectionAccess.requireOwnCourseSection(sectionId, requestedById);
        }
        body.requestedStatus().requireRecordableByInstructor();
        AttendanceChangeRequest request = changeRequestDrafter.draftPendingRequest(attendance, requestedById, body);
        return attendanceChangeRequestRepository.save(request);
    }
}

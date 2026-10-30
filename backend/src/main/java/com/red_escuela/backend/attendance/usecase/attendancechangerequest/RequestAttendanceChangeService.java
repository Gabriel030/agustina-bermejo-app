package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RequestAttendanceChangeService implements RequestAttendanceChangeUseCase {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceSectionAccess sectionAccess;

    public RequestAttendanceChangeService(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
                                           AttendanceRepository attendanceRepository,
                                           AttendanceSectionAccess sectionAccess) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.attendanceRepository = attendanceRepository;
        this.sectionAccess = sectionAccess;
    }

    @Override
    @Transactional
    public AttendanceChangeRequest request(RequestAttendanceChangeCommand command) {
        Attendance attendance = attendanceRepository.findById(command.attendanceId())
                .orElseThrow(() -> new AttendanceNotFoundException(command.attendanceId()));
        Long sectionId = sectionAccess.sectionIdOf(attendance);
        Long requestedById = command.requestedBy().getId();
        if (attendance.getProgramSubjectSectionEnrollment() != null) {
            sectionAccess.requireOwnProgramSection(sectionId, requestedById);
        } else {
            sectionAccess.requireOwnCourseSection(sectionId, requestedById);
        }

        command.body().requestedStatus().requireRecordableByInstructor();
        AttendanceChangeRequest request = new AttendanceChangeRequest();
        request.setAttendance(attendance);
        request.setRequestedBy(command.requestedBy());
        request.setPreviousStatus(attendance.getStatus());
        request.setRequestedStatus(command.body().requestedStatus());
        request.setReason(command.body().reason());
        request.setStatus(AttendanceChangeRequestStatus.PENDING);
        return attendanceChangeRequestRepository.save(request);
    }
}

package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceChangeRequestDrafter;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RequestAttendanceChangeService implements RequestAttendanceChangeUseCase {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final AttendanceChangeRequestDrafter changeRequestDrafter;

    public RequestAttendanceChangeService(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
                                           AttendanceRepository attendanceRepository,
                                           AttendanceSectionAccess sectionAccess,
                                           AttendanceChangeRequestDrafter changeRequestDrafter) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.attendanceRepository = attendanceRepository;
        this.sectionAccess = sectionAccess;
        this.changeRequestDrafter = changeRequestDrafter;
    }

    @Override
    @Transactional
    public AttendanceChangeRequest request(RequestAttendanceChangeCommand command) {
        Attendance attendance = attendanceRepository.findById(command.attendanceId())
                .orElseThrow(() -> new AttendanceNotFoundException(command.attendanceId()));
        Long sectionId = sectionAccess.sectionIdOf(attendance);
        Long requestedById = command.requestedById();
        if (attendance.getProgramSubjectSectionEnrollment() != null) {
            sectionAccess.requireOwnProgramSection(sectionId, requestedById);
        } else {
            sectionAccess.requireOwnCourseSection(sectionId, requestedById);
        }

        command.body().requestedStatus().requireRecordableByInstructor();
        AttendanceChangeRequest request = changeRequestDrafter.draftPendingRequest(attendance, requestedById, command.body());
        return attendanceChangeRequestRepository.save(request);
    }
}

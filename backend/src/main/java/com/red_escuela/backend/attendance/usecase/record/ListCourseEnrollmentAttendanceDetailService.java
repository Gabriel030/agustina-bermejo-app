package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.AttendanceResponse;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.specialist.AttendanceResponseAssembler;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListCourseEnrollmentAttendanceDetailService implements ListCourseEnrollmentAttendanceDetailUseCase {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceResponseAssembler responseAssembler;

    public ListCourseEnrollmentAttendanceDetailService(
            AttendanceRepository attendanceRepository, AttendanceResponseAssembler responseAssembler) {
        this.attendanceRepository = attendanceRepository;
        this.responseAssembler = responseAssembler;
    }

    @Override
    public List<AttendanceResponse> list(Long enrollmentId) {
        return responseAssembler.assembleWithDeadlines(attendanceRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId));
    }
}

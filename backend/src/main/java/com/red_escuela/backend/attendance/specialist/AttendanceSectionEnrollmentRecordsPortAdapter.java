package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentRecordsPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class AttendanceSectionEnrollmentRecordsPortAdapter implements SectionEnrollmentRecordsPort {

    private final AttendanceRepository attendanceRepository;

    public AttendanceSectionEnrollmentRecordsPortAdapter(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    @Override
    public boolean hasProgramEnrollmentRecords(Long programSectionEnrollmentId) {
        return attendanceRepository.existsByProgramSubjectSectionEnrollmentId(programSectionEnrollmentId);
    }
}

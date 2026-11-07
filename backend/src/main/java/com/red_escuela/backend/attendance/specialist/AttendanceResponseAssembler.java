package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.api.JustificationDeadlinePolicy;
import com.red_escuela.backend.attendance.dto.AttendanceResponse;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class AttendanceResponseAssembler {

    private final JustificationDeadlinePolicy deadlinePolicy;

    public AttendanceResponseAssembler(JustificationDeadlinePolicy deadlinePolicy) {
        this.deadlinePolicy = deadlinePolicy;
    }

    public List<AttendanceResponse> assembleWithDeadlines(List<Attendance> attendances) {
        if (attendances.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, LocalDate> deadlines = deadlinePolicy.deadlinesOf(
                attendances.stream().map(Attendance::getClassDate).toList());
        return attendances.stream().map(attendance -> assemble(attendance, deadlines.get(attendance.getClassDate()))).toList();
    }

    private AttendanceResponse assemble(Attendance attendance, LocalDate deadline) {
        boolean justifiable = attendance.getStatus() == AttendanceStatus.ABSENT && deadlinePolicy.isDeadlineOpen(deadline);
        return AttendanceResponse.from(attendance, deadline, justifiable);
    }
}

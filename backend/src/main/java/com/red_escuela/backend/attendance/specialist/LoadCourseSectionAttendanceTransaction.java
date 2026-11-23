package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LoadCourseSectionAttendanceTransaction {

    private final SectionAttendanceLoader attendanceLoader;
    private final ApplicationEventPublisher eventPublisher;

    public LoadCourseSectionAttendanceTransaction(SectionAttendanceLoader attendanceLoader,
                                              ApplicationEventPublisher eventPublisher) {
        this.attendanceLoader = attendanceLoader;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public List<Attendance> load(Long sectionId, Long instructorId, CargarAsistenciaRequest request) {
        List<Attendance> processed = attendanceLoader.loadCourseSectionAttendance(sectionId, instructorId, request);
        processed.forEach(saved -> eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(saved)));
        return processed;
    }
}

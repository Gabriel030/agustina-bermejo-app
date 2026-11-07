package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.specialist.SectionAttendanceLoader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class LoadProgramSectionAttendanceService implements LoadProgramSectionAttendanceUseCase {

    private final SectionAttendanceLoader attendanceLoader;
    private final ApplicationEventPublisher eventPublisher;

    public LoadProgramSectionAttendanceService(SectionAttendanceLoader attendanceLoader,
                                            ApplicationEventPublisher eventPublisher) {
        this.attendanceLoader = attendanceLoader;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public List<Attendance> load(LoadProgramSectionAttendanceCommand command) {
        List<Attendance> processed = attendanceLoader.loadProgramSectionAttendance(
                command.sectionId(), command.instructorId(), command.request());
        processed.forEach(saved -> eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(saved)));
        return processed;
    }
}

package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.InvalidJustificationInputException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceActorResolver;
import com.red_escuela.backend.attendance.service.AttendanceFinder;
import com.red_escuela.backend.attendance.service.AttendanceJustificationRecorder;
import com.red_escuela.backend.attendance.service.JustificationDeadlinePolicyTest;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JustifyAttendanceServiceTest {

    private static final LocalDate CLASS_DATE = LocalDate.of(2026, 3, 6);

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final AttendanceFinder attendanceFinder = mock(AttendanceFinder.class);
    private final AttendanceActorResolver actorResolver = mock(AttendanceActorResolver.class);

    private JustifyAttendanceService serviceOn(LocalDate today) {
        return new JustifyAttendanceService(
                attendanceRepository, attendanceFinder, new AttendanceJustificationRecorder(actorResolver),
                JustificationDeadlinePolicyTest.policy(today, 5), mock(ApplicationEventPublisher.class));
    }

    private Attendance absence() {
        Attendance attendance = new Attendance();
        attendance.setId(11L);
        attendance.setClassDate(CLASS_DATE);
        attendance.setStatus(AttendanceStatus.ABSENT);
        when(attendanceFinder.findOrThrow(11L)).thenReturn(attendance);
        when(attendanceRepository.save(attendance)).thenReturn(attendance);
        return attendance;
    }

    @Test
    void preceptorJustifyInTimeLeavesJustified() {
        Attendance attendance = absence();

        Attendance saved = serviceOn(LocalDate.of(2026, 3, 13))
                .justify(new JustifyAttendanceCommand(11L, "Certificado", null, null));

        assertThat(saved.getStatus()).isEqualTo(AttendanceStatus.JUSTIFIED);
        assertThat(attendance.getJustificationReason()).isEqualTo("Certificado");
    }

    @Test
    void preceptorJustifyAfterDeadlineRequiresExceptionReason() {
        Attendance attendance = absence();
        JustifyAttendanceService late = serviceOn(LocalDate.of(2026, 3, 14));

        assertThatThrownBy(() -> late.justify(new JustifyAttendanceCommand(11L, "Certificado", null, null)))
                .isInstanceOf(InvalidJustificationInputException.class);
        assertThatThrownBy(() -> late.justify(new JustifyAttendanceCommand(11L, "Certificado", null, "  ")))
                .isInstanceOf(InvalidJustificationInputException.class);
        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void preceptorJustifyAfterDeadlineAppendsTheExceptionReason() {
        Attendance attendance = absence();

        Attendance saved = serviceOn(LocalDate.of(2026, 3, 14))
                .justify(new JustifyAttendanceCommand(11L, "Certificado", null, "Internación"));

        assertThat(saved.getStatus()).isEqualTo(AttendanceStatus.JUSTIFIED);
        assertThat(attendance.getJustificationReason()).isEqualTo("Certificado [Excepción fuera de plazo: Internación]");
    }

    @Test
    void exceptionReasonIsIgnoredWhenTheDeadlineIsOpen() {
        Attendance attendance = absence();

        serviceOn(LocalDate.of(2026, 3, 13)).justify(new JustifyAttendanceCommand(11L, "Certificado", null, "Internación"));

        assertThat(attendance.getJustificationReason()).isEqualTo("Certificado");
    }

    @Test
    void combinedReasonIsTruncatedToTheColumnLength() {
        Attendance attendance = absence();

        serviceOn(LocalDate.of(2026, 3, 14))
                .justify(new JustifyAttendanceCommand(11L, "x".repeat(500), null, "Internación"));

        assertThat(attendance.getJustificationReason()).hasSize(500);
    }
}

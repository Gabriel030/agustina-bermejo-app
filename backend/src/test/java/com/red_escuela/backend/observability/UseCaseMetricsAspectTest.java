package com.red_escuela.backend.observability;

import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.usecase.attendance.ListProgramEnrollmentAttendanceUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.RejectAttendanceJustificationCommand;
import com.red_escuela.backend.attendance.usecase.attendance.RejectAttendanceJustificationUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class UseCaseMetricsAspectTest extends AbstractIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ListProgramEnrollmentAttendanceUseCase listProgramEnrollmentAttendance;

    @Autowired
    private RejectAttendanceJustificationUseCase rejectAttendanceJustification;

    @Test
    void successfulUseCaseIsRecordedWithUseCaseMethodAndSuccessOutcome() {
        listProgramEnrollmentAttendance.list(-1L);

        Timer timer = meterRegistry.find("usecase.duration")
                .tag("usecase", "ListProgramEnrollmentAttendanceUseCase")
                .tag("method", "list")
                .tag("outcome", "success")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void failingUseCaseIsRecordedWithTheErrorCodeAsOutcomeAndRethrows() {
        assertThatThrownBy(() -> rejectAttendanceJustification.reject(new RejectAttendanceJustificationCommand(-1L, "Motivo suficiente")))
                .isInstanceOf(AttendanceNotFoundException.class);

        Timer timer = meterRegistry.find("usecase.duration")
                .tag("usecase", "RejectAttendanceJustificationUseCase")
                .tag("method", "reject")
                .tag("outcome", "ATTENDANCE_NOT_FOUND")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void tagsNeverCarryParameterValues() {
        listProgramEnrollmentAttendance.list(-4L);
        int seriesBefore = seriesOfListUseCase();

        listProgramEnrollmentAttendance.list(-2L);
        listProgramEnrollmentAttendance.list(-3L);

        assertThat(seriesOfListUseCase()).isEqualTo(seriesBefore);
    }

    private int seriesOfListUseCase() {
        return meterRegistry.find("usecase.duration")
                .tag("usecase", "ListProgramEnrollmentAttendanceUseCase")
                .timers()
                .size();
    }
}

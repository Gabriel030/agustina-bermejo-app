package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.api.JustificationDeadlinePolicyTest;
import com.red_escuela.backend.attendance.dto.AttendanceResponse;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceResponseAssemblerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 11);

    private static Attendance attendance(long id, LocalDate day, AttendanceStatus status) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(5L);
        Attendance attendance = new Attendance();
        attendance.setId(id);
        attendance.setProgramSubjectSectionEnrollment(enrollment);
        attendance.setClassDate(day);
        attendance.setStatus(status);
        return attendance;
    }

    private static AttendanceResponseAssembler assembler(LocalDate... holidays) {
        return new AttendanceResponseAssembler(JustificationDeadlinePolicyTest.policy(TODAY, 5, holidays));
    }

    @Test
    void absentWithinDeadlineIsJustifiableAndExposesDeadline() {
        List<AttendanceResponse> responses = assembler().assembleWithDeadlines(
                List.of(attendance(1L, LocalDate.of(2026, 3, 6), AttendanceStatus.ABSENT)));

        assertThat(responses.get(0).justificationDeadline()).isEqualTo(LocalDate.of(2026, 3, 13));
        assertThat(responses.get(0).justifiable()).isTrue();
    }

    @Test
    void absentPastDeadlineIsNotJustifiableButKeepsDeadline() {
        List<AttendanceResponse> responses = assembler().assembleWithDeadlines(
                List.of(attendance(1L, LocalDate.of(2026, 2, 27), AttendanceStatus.ABSENT)));

        assertThat(responses.get(0).justificationDeadline()).isEqualTo(LocalDate.of(2026, 3, 6));
        assertThat(responses.get(0).justifiable()).isFalse();
    }

    @Test
    void presentWithinDeadlineIsNotJustifiable() {
        List<AttendanceResponse> responses = assembler().assembleWithDeadlines(
                List.of(attendance(1L, LocalDate.of(2026, 3, 9), AttendanceStatus.PRESENT)));

        assertThat(responses.get(0).justifiable()).isFalse();
        assertThat(responses.get(0).justificationDeadline()).isEqualTo(LocalDate.of(2026, 3, 16));
    }

    @Test
    void holidayInsideTheWindowPushesTheDeadlineAndKeepsTheAbsenceJustifiable() {
        List<AttendanceResponse> responses = assembler(LocalDate.of(2026, 3, 9)).assembleWithDeadlines(
                List.of(attendance(1L, LocalDate.of(2026, 3, 4), AttendanceStatus.ABSENT)));

        assertThat(responses.get(0).justificationDeadline()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(responses.get(0).justifiable()).isTrue();
    }

    @Test
    void eachAttendanceGetsItsOwnDeadlineAndFlagInTheSameList() {
        List<AttendanceResponse> responses = assembler().assembleWithDeadlines(List.of(
                attendance(1L, LocalDate.of(2026, 3, 6), AttendanceStatus.ABSENT),
                attendance(2L, LocalDate.of(2026, 2, 27), AttendanceStatus.ABSENT)));

        assertThat(responses).extracting(AttendanceResponse::justifiable).containsExactly(true, false);
        assertThat(responses).extracting(AttendanceResponse::justificationDeadline)
                .containsExactly(LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 6));
    }

    @Test
    void emptyListYieldsEmptyResponses() {
        assertThat(assembler().assembleWithDeadlines(List.of())).isEmpty();
    }
}

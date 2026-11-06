package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.attendance.api.JustificationDeadlinePolicyTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceJustificationRecorderTest {

    private final AttendanceActorResolver actorResolver = mock(AttendanceActorResolver.class);
    private final AttendanceJustificationRecorder recorder = new AttendanceJustificationRecorder(actorResolver, JustificationDeadlinePolicyTest.policy(LocalDate.of(2026, 3, 6), 5));

    @Test
    void recordsTheCurrentActorAsJustifier() {
        Person person = new Person();
        when(actorResolver.currentActor()).thenReturn(person);
        Attendance attendance = new Attendance();
        attendance.setStatus(AttendanceStatus.ABSENT);

        recorder.markJustifiedByCurrentActor(attendance);

        assertThat(attendance.getJustifiedBy()).isSameAs(person);
        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.JUSTIFIED);
        assertThat(attendance.getJustifiedAt()).isNotNull();
    }
}

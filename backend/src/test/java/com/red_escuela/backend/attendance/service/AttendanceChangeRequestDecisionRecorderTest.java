package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceChangeRequestDecisionRecorderTest {

    private final AttendanceActorResolver actorResolver = mock(AttendanceActorResolver.class);
    private final AttendanceChangeRequestDecisionRecorder recorder = new AttendanceChangeRequestDecisionRecorder(actorResolver);

    @Test
    void recordsTheDecisionWithTheCurrentActorAndTimestamp() {
        Person person = new Person();
        when(actorResolver.currentActor()).thenReturn(person);
        AttendanceChangeRequest request = new AttendanceChangeRequest();

        recorder.recordDecisionByCurrentActor(request, AttendanceChangeRequestStatus.REJECTED);

        assertThat(request.getStatus()).isEqualTo(AttendanceChangeRequestStatus.REJECTED);
        assertThat(request.getApprovedBy()).isSameAs(person);
        assertThat(request.getApprovedAt()).isNotNull();
    }
}

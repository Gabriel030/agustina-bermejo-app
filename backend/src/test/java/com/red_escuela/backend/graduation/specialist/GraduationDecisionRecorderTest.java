package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GraduationDecisionRecorderTest {

    private final CurrentActorFinder actorFinder = Mockito.mock(CurrentActorFinder.class);
    private final GraduationRequestRepository repository = Mockito.mock(GraduationRequestRepository.class);
    private final GraduationRequestApprover approver = Mockito.mock(GraduationRequestApprover.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-03-01T10:00:00Z"), ZoneOffset.UTC);
    private final Person actor = new Person();
    private GraduationDecisionRecorder recorder;

    @BeforeEach
    void prepareRecorder() {
        Mockito.when(actorFinder.currentPerson()).thenReturn(actor);
        Mockito.when(repository.save(Mockito.any())).thenAnswer(call -> call.getArgument(0));
        recorder = new GraduationDecisionRecorder(actorFinder, repository, approver, clock);
    }

    @Test
    void recordsTheCurrentActorAsDecider() {
        GraduationRequest rejected = recorder.recordRejection(new GraduationRequest(), "Falta documentación");

        assertThat(rejected.getStatus()).isEqualTo(GraduationRequestStatus.REJECTED);
        assertThat(rejected.getDecidedBy()).isSameAs(actor);
        assertThat(rejected.getDecidedAt()).isEqualTo(LocalDateTime.of(2027, 3, 1, 10, 0));
        assertThat(rejected.getRejectionReason()).isEqualTo("Falta documentación");
    }

    @Test
    void recordsTheCurrentActorAsVerifier() {
        GraduationRequest verified = recorder.recordVerification(new GraduationRequest());

        assertThat(verified.getStatus()).isEqualTo(GraduationRequestStatus.ADMIN_VERIFIED);
        assertThat(verified.getVerifiedBy()).isSameAs(actor);
        assertThat(verified.getVerifiedAt()).isEqualTo(LocalDateTime.of(2027, 3, 1, 10, 0));
    }

    @Test
    void approvesWithTheCurrentActorAtTheClockTime() {
        GraduationRequest request = new GraduationRequest();

        recorder.recordApproval(request);

        Mockito.verify(approver).recordApproval(request, actor, LocalDateTime.of(2027, 3, 1, 10, 0));
    }
}

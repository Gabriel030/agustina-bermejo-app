package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdict;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdictReason;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.enrollment.events.WaitlistEntryPausedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitlistPromoterTest {

    private static final Long QUEUE_ID = 1L;

    @Mock
    private WaitlistCandidateEligibility eligibility;
    @Mock
    private AuditTrail auditTrail;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private FakeQueue queue;
    private WaitlistPromoter promoter;

    @BeforeEach
    void setUp() {
        queue = new FakeQueue();
        promoter = new WaitlistPromoter(new WaitlistQueues(List.of(queue)), eligibility, auditTrail, eventPublisher);
    }

    @Test
    void promotesTheFirstCandidateOfTheQueueWhenASeatIsFree() {
        queue.capacity = 10;
        queue.occupied = 9;
        queue.candidates = List.of(candidate(11L), candidate(12L));
        when(eligibility.evaluate(any())).thenReturn(WaitlistVerdict.passing());

        promoter.promoteNext(WaitlistKind.PROGRAM_SECTION, QUEUE_ID);

        assertThat(queue.promoted).containsExactly(11L);
    }

    @Test
    void skippedCandidateKeepsItsPlaceAndTheNextEligibleOneIsPromoted() {
        queue.capacity = 10;
        queue.occupied = 9;
        queue.candidates = List.of(candidate(11L), candidate(12L));
        when(eligibility.evaluate(candidate(11L))).thenReturn(WaitlistVerdict.skippedBecause(WaitlistVerdictReason.SCHEDULE_OVERLAP));
        when(eligibility.evaluate(candidate(12L))).thenReturn(WaitlistVerdict.passing());

        promoter.promoteNext(WaitlistKind.PROGRAM_SECTION, QUEUE_ID);

        assertThat(queue.promoted).containsExactly(12L);
        assertThat(queue.candidates).extracting(WaitlistCandidate::enrollmentId).containsExactly(11L, 12L);
    }

    @Test
    void promotesNobodyWhenEveryCandidateIsSkipped() {
        queue.capacity = 10;
        queue.occupied = 9;
        queue.candidates = List.of(candidate(11L));
        when(eligibility.evaluate(any())).thenReturn(WaitlistVerdict.skippedBecause(WaitlistVerdictReason.ALREADY_PASSED));

        promoter.promoteNext(WaitlistKind.PROGRAM_SECTION, QUEUE_ID);

        assertThat(queue.promoted).isEmpty();
    }

    @Test
    void promotesNobodyWhenNoSeatIsFree() {
        queue.capacity = 10;
        queue.occupied = 10;
        queue.candidates = List.of(candidate(11L));

        promoter.promoteNext(WaitlistKind.PROGRAM_SECTION, QUEUE_ID);

        assertThat(queue.promoted).isEmpty();
    }

    @Test
    void promotesWhenTheQueueHasUnlimitedCapacity() {
        queue.capacity = null;
        queue.occupied = 500;
        queue.candidates = List.of(candidate(11L));
        when(eligibility.evaluate(any())).thenReturn(WaitlistVerdict.passing());

        promoter.promoteNext(WaitlistKind.PROGRAM_SECTION, QUEUE_ID);

        assertThat(queue.promoted).containsExactly(11L);
    }

    @Test
    void promotesNobodyWhenTheQueueIsClosedForPromotion() {
        queue.capacity = 10;
        queue.occupied = 0;
        queue.open = false;
        queue.candidates = List.of(candidate(11L));

        promoter.promoteNext(WaitlistKind.PROGRAM_SECTION, QUEUE_ID);

        assertThat(queue.promoted).isEmpty();
    }

    @Test
    void locksTheQueueBeforeReadingItsSeats() {
        queue.capacity = 10;
        queue.occupied = 10;

        promoter.promoteNext(WaitlistKind.PROGRAM_SECTION, QUEUE_ID);

        assertThat(queue.locked).isTrue();
    }

    @Test
    void candidatesSkippedByScheduleOverlapAheadOfThePromotedOneAreAnnouncedAsPaused() {
        queue.capacity = 10;
        queue.occupied = 9;
        queue.candidates = List.of(candidate(11L), candidate(12L), candidate(13L));
        when(eligibility.evaluate(candidate(11L))).thenReturn(WaitlistVerdict.skippedBecause(WaitlistVerdictReason.SCHEDULE_OVERLAP));
        when(eligibility.evaluate(candidate(12L))).thenReturn(WaitlistVerdict.passing());
        when(eligibility.findConflictingSectionName(candidate(11L))).thenReturn(Optional.of("Historia II"));

        promoter.promoteNext(WaitlistKind.PROGRAM_SECTION, QUEUE_ID);

        ArgumentCaptor<WaitlistEntryPausedEvent> captor = ArgumentCaptor.forClass(WaitlistEntryPausedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        WaitlistEntryPausedEvent event = captor.getValue();
        assertThat(event.enrollmentId()).isEqualTo(11L);
        assertThat(event.studentPersonId()).isEqualTo(111L);
        assertThat(event.queueName()).isEqualTo("Cola");
        assertThat(event.conflictingSectionName()).isEqualTo("Historia II");
    }

    @Test
    void nobodyIsAnnouncedAsPausedWhenNobodyBehindThemIsPromoted() {
        queue.capacity = 10;
        queue.occupied = 9;
        queue.candidates = List.of(candidate(11L), candidate(12L));
        when(eligibility.evaluate(any())).thenReturn(WaitlistVerdict.skippedBecause(WaitlistVerdictReason.SCHEDULE_OVERLAP));

        promoter.promoteNext(WaitlistKind.PROGRAM_SECTION, QUEUE_ID);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void candidatesSkippedForAnotherReasonAreNotAnnouncedAsPaused() {
        queue.capacity = 10;
        queue.occupied = 9;
        queue.candidates = List.of(candidate(11L), candidate(12L));
        when(eligibility.evaluate(candidate(11L))).thenReturn(WaitlistVerdict.skippedBecause(WaitlistVerdictReason.PREREQUISITES_NOT_MET));
        when(eligibility.evaluate(candidate(12L))).thenReturn(WaitlistVerdict.passing());

        promoter.promoteNext(WaitlistKind.PROGRAM_SECTION, QUEUE_ID);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void candidatesBehindThePromotedOneAreNotEvaluatedNorAnnounced() {
        queue.capacity = 10;
        queue.occupied = 9;
        queue.candidates = List.of(candidate(11L), candidate(12L));
        when(eligibility.evaluate(candidate(11L))).thenReturn(WaitlistVerdict.passing());

        promoter.promoteNext(WaitlistKind.PROGRAM_SECTION, QUEUE_ID);

        verify(eligibility, never()).evaluate(candidate(12L));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private static WaitlistCandidate candidate(Long enrollmentId) {
        return new WaitlistCandidate(WaitlistKind.PROGRAM_SECTION, enrollmentId, enrollmentId, "Ana", "1",
                LocalDateTime.of(2047, 2, 3, 9, 0), 1L, 1L, 1L, 1L, null);
    }

    private static final class FakeQueue implements WaitlistQueue {
        Integer capacity;
        long occupied;
        boolean open = true;
        boolean locked;
        List<WaitlistCandidate> candidates = List.of();
        final List<Long> promoted = new ArrayList<>();

        @Override
        public WaitlistKind kind() {
            return WaitlistKind.PROGRAM_SECTION;
        }

        @Override
        public AuditEntityType auditEntityType() {
            return AuditEntityType.SECTION_ENROLLMENT;
        }

        @Override
        public List<WaitlistQueueSnapshot> findQueues(Long queueId, Long termId) {
            return List.of(new WaitlistQueueSnapshot(queueId, "Cola", "2047", capacity, occupied, candidates.size(), null));
        }

        @Override
        public List<WaitlistCandidate> findWaitingCandidates(Long queueId) {
            return candidates;
        }

        @Override
        public Map<Long, Long> findStudentIdByEnrollmentId(Long queueId) {
            return Map.of();
        }

        @Override
        public void lock(Long queueId) {
            locked = true;
        }

        @Override
        public boolean isOpenForPromotion(Long queueId) {
            return open;
        }

        @Override
        public void promote(Long enrollmentId) {
            promoted.add(enrollmentId);
        }

        @Override
        public java.util.Optional<WaitlistEntryOwner> findOwner(Long enrollmentId) {
            return Optional.of(new WaitlistEntryOwner(QUEUE_ID, enrollmentId + 100));
        }

        @Override
        public void markPrioritized(Long enrollmentId, LocalDateTime at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(Long enrollmentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasReceiptBlockingRemoval(Long enrollmentId) {
            return false;
        }
    }
}

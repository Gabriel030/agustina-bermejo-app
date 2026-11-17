package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdict;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdictReason;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitlistPromoterTest {

    private static final Long QUEUE_ID = 1L;

    @Mock
    private WaitlistCandidateEligibility eligibility;

    private FakeQueue queue;
    private WaitlistPromoter promoter;

    @BeforeEach
    void setUp() {
        queue = new FakeQueue();
        promoter = new WaitlistPromoter(new WaitlistQueues(List.of(queue)), eligibility);
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
    }
}

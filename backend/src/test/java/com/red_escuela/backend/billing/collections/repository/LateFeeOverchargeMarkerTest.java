package com.red_escuela.backend.billing.collections.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LateFeeOverchargeMarkerTest {

    @Test
    void theRecoveryQueryUsesTheSameHandlerAndMarkerEncodingAsTheRecorder() throws Exception {
        String sql = LateFeeChargeRepository.class.getMethod("findRecentlySettledLateFeeOriginIdsAfter", Long.class, java.time.LocalDate.class, int.class)
                .getAnnotation(Query.class).value();

        assertThat(sql).contains("p.handler_name = '" + LateFeeOverchargeMarker.HANDLER_NAME + "'");
        assertThat(sql).contains("p.event_id = s.marker");
        assertThat(sql).contains(LateFeeOverchargeMarker.ALLOCATION_MARKER_SQL + " as marker");
        assertThat(sql).contains(LateFeeOverchargeMarker.CREDIT_NOTE_MARKER_SQL);
    }

    @Test
    void theMarkerOfACreditNoteNeverCollidesWithTheMarkerOfAnAllocationWithTheSameId() {
        assertThat(LateFeeOverchargeMarker.markerOfCreditNote(255L)).isEqualTo(UUID.fromString("00000000-0000-0001-0000-0000000000ff"));
        assertThat(LateFeeOverchargeMarker.markerOfCreditNote(255L)).isNotEqualTo(LateFeeOverchargeMarker.markerOf(255L));
    }

    @Test
    void theMarkerOfASettlementSourceFollowsItsKind() {
        assertThat(LateFeeOverchargeMarker.markerOf(com.red_escuela.backend.billing.collections.domain.LateFeeSettlementSource.ofAllocation(7L)))
                .isEqualTo(LateFeeOverchargeMarker.markerOf(7L));
        assertThat(LateFeeOverchargeMarker.markerOf(com.red_escuela.backend.billing.collections.domain.LateFeeSettlementSource.ofCreditNote(7L)))
                .isEqualTo(LateFeeOverchargeMarker.markerOfCreditNote(7L));
    }

    @Test
    void theMarkerOfAnAllocationIsItsIdAsAZeroPaddedUuid() {
        assertThat(LateFeeOverchargeMarker.markerOf(255L)).isEqualTo(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));
    }
}

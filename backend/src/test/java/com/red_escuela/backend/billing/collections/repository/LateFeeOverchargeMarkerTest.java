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
        assertThat(sql).contains("p.event_id = " + LateFeeOverchargeMarker.ALLOCATION_MARKER_SQL);
    }

    @Test
    void theMarkerOfAnAllocationIsItsIdAsAZeroPaddedUuid() {
        assertThat(LateFeeOverchargeMarker.markerOf(255L)).isEqualTo(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));
    }
}

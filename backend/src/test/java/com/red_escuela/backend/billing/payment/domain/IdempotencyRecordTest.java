package com.red_escuela.backend.billing.payment.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-136")
class IdempotencyRecordTest {

    @Test
    void aFreshlyClaimedRecordHasNoResponseAndStoringOneMakesItReplayable() {
        IdempotencyRecord record = new IdempotencyRecord();

        assertThat(record.hasStoredResponse()).isFalse();
        assertThat(record.getResponseStatus()).isNull();

        record.storeResponse(201, "{\"reference\":\"payment-1\"}");

        assertThat(record.hasStoredResponse()).isTrue();
        assertThat(record.getResponseStatus()).isEqualTo(201);
        assertThat(record.getResponseBody()).isEqualTo("{\"reference\":\"payment-1\"}");
    }
}

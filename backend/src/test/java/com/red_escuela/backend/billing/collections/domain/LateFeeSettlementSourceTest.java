package com.red_escuela.backend.billing.collections.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-377")
class LateFeeSettlementSourceTest {

    @Test
    void aSourceFromAnAllocationHasNoCreditNote() {
        LateFeeSettlementSource source = LateFeeSettlementSource.ofAllocation(5L);

        assertThat(source.allocationId()).contains(5L);
        assertThat(source.creditNoteId()).isEmpty();
    }

    @Test
    void aSourceFromACreditNoteHasNoAllocation() {
        LateFeeSettlementSource source = LateFeeSettlementSource.ofCreditNote(8L);

        assertThat(source.creditNoteId()).contains(8L);
        assertThat(source.allocationId()).isEmpty();
    }

    @Test
    void aSourceWithBothOrNoneIsRejected() {
        assertThatThrownBy(() -> new LateFeeSettlementSource(Optional.of(1L), Optional.of(2L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LateFeeSettlementSource(Optional.empty(), Optional.empty())).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.red_escuela.backend.shared.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusTypeTest {

    @Test
    void getStatusReturnsTheConstantName() {
        assertThat(StatusType.ACTIVE.getStatus()).isEqualTo("ACTIVE");
        assertThat(StatusType.INACTIVE.getStatus()).isEqualTo("INACTIVE");
        assertThat(StatusType.DELETED.getStatus()).isEqualTo("DELETED");
    }
}

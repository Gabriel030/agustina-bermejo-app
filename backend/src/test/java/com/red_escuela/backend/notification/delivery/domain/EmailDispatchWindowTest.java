package com.red_escuela.backend.notification.delivery.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("c06")
class EmailDispatchWindowTest {

    @ParameterizedTest
    @CsvSource({"08:59,false", "09:00,true", "10:00,true", "10:59,true", "10:59:59,true", "11:00,false", "11:01,false", "00:00,false", "23:59,false"})
    void theStandardWindowOpensAtNineAndClosesAtEleven(String time, boolean open) {
        assertThat(EmailDispatchWindow.STANDARD.isOpenAt(LocalTime.parse(time))).isEqualTo(open);
    }

    @Test
    void aWindowThatEndsWhereItStartsIsRejected() {
        assertThatThrownBy(() -> new EmailDispatchWindow(LocalTime.of(9, 0), LocalTime.of(9, 0))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRejected() {
        assertThatThrownBy(() -> new EmailDispatchWindow(LocalTime.of(11, 0), LocalTime.of(9, 0))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWindowWithoutLimitsIsRejected() {
        assertThatThrownBy(() -> new EmailDispatchWindow(null, LocalTime.of(9, 0))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmailDispatchWindow(LocalTime.of(9, 0), null)).isInstanceOf(NullPointerException.class);
    }
}

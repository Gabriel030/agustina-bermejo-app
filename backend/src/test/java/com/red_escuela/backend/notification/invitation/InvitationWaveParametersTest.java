package com.red_escuela.backend.notification.invitation;

import com.red_escuela.backend.notification.invitation.domain.InvitationWaveParameters;
import com.red_escuela.backend.shared.exception.RequestFieldOutOfRangeException;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationWaveParametersTest {

    @Test
    void acceptsTheLowerBoundsAndTrimsTheName() {
        InvitationWaveParameters parameters = new InvitationWaveParameters("  Oleada 1  ", 1, 1, 1);

        assertThat(parameters.name()).isEqualTo("Oleada 1");
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new InvitationWaveParameters("  ", 10, 5, 100)).isInstanceOf(RequestFieldRequiredException.class);
        assertThatThrownBy(() -> new InvitationWaveParameters(null, 10, 5, 100)).isInstanceOf(RequestFieldRequiredException.class);
    }

    @Test
    void rejectsNameLongerThanTheColumn() {
        assertThatThrownBy(() -> new InvitationWaveParameters("x".repeat(161), 10, 5, 100)).isInstanceOf(RequestFieldOutOfRangeException.class);
        assertThatCode(() -> new InvitationWaveParameters("x".repeat(160), 10, 5, 100)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> new InvitationWaveParameters("Oleada", 0, 5, 100)).isInstanceOf(RequestFieldOutOfRangeException.class);
        assertThatThrownBy(() -> new InvitationWaveParameters("Oleada", -1, 5, 100)).isInstanceOf(RequestFieldOutOfRangeException.class);
    }

    @Test
    void rejectsIntervalOutsideOneAndOneDay() {
        assertThatThrownBy(() -> new InvitationWaveParameters("Oleada", 10, 0, 100)).isInstanceOf(RequestFieldOutOfRangeException.class);
        assertThatThrownBy(() -> new InvitationWaveParameters("Oleada", 10, 1441, 100)).isInstanceOf(RequestFieldOutOfRangeException.class);
        assertThatCode(() -> new InvitationWaveParameters("Oleada", 10, 1440, 100)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonPositiveDailyCap() {
        assertThatThrownBy(() -> new InvitationWaveParameters("Oleada", 1, 5, 0)).isInstanceOf(RequestFieldOutOfRangeException.class);
    }

    @Test
    void rejectsBatchLargerThanTheDailyCap() {
        assertThatThrownBy(() -> new InvitationWaveParameters("Oleada", 101, 5, 100)).isInstanceOf(RequestFieldOutOfRangeException.class);
        assertThatCode(() -> new InvitationWaveParameters("Oleada", 100, 5, 100)).doesNotThrowAnyException();
    }

    @Test
    void dailyCapAtTheGlobalLimitPassesAndOneAboveIsRejected() {
        InvitationWaveParameters atLimit = new InvitationWaveParameters("Oleada", 10, 5, 300);
        InvitationWaveParameters aboveLimit = new InvitationWaveParameters("Oleada", 10, 5, 301);

        assertThat(atLimit.requireWithinGlobalDailyLimit(300)).isSameAs(atLimit);
        assertThatThrownBy(() -> aboveLimit.requireWithinGlobalDailyLimit(300)).isInstanceOf(RequestFieldOutOfRangeException.class);
    }
}

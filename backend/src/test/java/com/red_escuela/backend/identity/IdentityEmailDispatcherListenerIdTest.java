package com.red_escuela.backend.identity;

import com.red_escuela.backend.identity.specialist.IdentityEmailDispatcher;
import com.red_escuela.backend.shared.event.EventPublicationResubmissionJob;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityEmailDispatcherListenerIdTest {

    @Test
    void theCircuitGuardedListenerPrefixMatchesTheDispatcherClass() {
        assertThat(EventPublicationResubmissionJob.MAIL_LISTENER_PREFIX).isEqualTo(IdentityEmailDispatcher.class.getName() + ".");
    }
}

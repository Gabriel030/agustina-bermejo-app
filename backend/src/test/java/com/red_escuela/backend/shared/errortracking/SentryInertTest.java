package com.red_escuela.backend.shared.errortracking;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import io.sentry.Sentry;
import io.sentry.ITransportFactory;
import io.sentry.transport.NoOpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class SentryInertTest extends AbstractIntegrationTest {

    @Autowired
    private ServerErrorReporter serverErrorReporter;

    @AfterEach
    void closeSentry() {
        Sentry.close();
    }

    private static ITransportFactory transportSpy() {
        ITransportFactory factory = mock(ITransportFactory.class);
        when(factory.create(any(), any())).thenReturn(NoOpTransport.getInstance());
        return factory;
    }

    @Test
    void sentryStaysDisabledInTheRunningContextWhenNoDsnIsConfigured() {
        assertThat(Sentry.isEnabled()).isFalse();
    }

    @Test
    void noTransportIsCreatedAndNothingIsSentWithoutDsn() {
        ITransportFactory factory = transportSpy();
        SentryInitializer initializer = new SentryInitializer("", "test", "", options -> options.setTransportFactory(factory));

        initializer.afterPropertiesSet();
        serverErrorReporter.reportServerError(new RuntimeException("boom"));

        assertThat(Sentry.isEnabled()).isFalse();
        verifyNoInteractions(factory);
    }

    @Test
    void theTransportSpyDetectsConnectionsWhenADsnIsConfigured() {
        ITransportFactory factory = transportSpy();
        SentryInitializer initializer = new SentryInitializer("https://publickey@localhost.invalid/1", "test", "", options -> options.setTransportFactory(factory));

        initializer.afterPropertiesSet();
        initializer.destroy();

        verify(factory, times(1)).create(any(), any());
    }
}

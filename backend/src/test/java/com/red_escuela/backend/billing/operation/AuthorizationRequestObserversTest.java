package com.red_escuela.backend.billing.operation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthorizationRequestObserversTest {

    private final AuthorizationRequestObserver first = mock(AuthorizationRequestObserver.class);
    private final AuthorizationRequestObserver second = mock(AuthorizationRequestObserver.class);
    private final BillingOperationType type = BillingOperationType.values()[0];

    @SuppressWarnings("unchecked")
    private AuthorizationRequestObservers observersOver(AuthorizationRequestObserver... registered) {
        ObjectProvider<AuthorizationRequestObserver> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(Stream.of(registered));
        return new AuthorizationRequestObservers(provider);
    }

    @Test
    void everyEventIsForwardedToEveryRegisteredObserver() {
        AuthorizationRequestObservers observers = observersOver(first, second);
        Object context = new Object();

        observers.afterOpened(1L, type, context);
        observers.afterRejected(2L, type, 7L, "no");
        observers.afterWithdrawn(3L, type, 8L);
        observers.afterExpired(4L, type);
        observers.afterResumeRejected(5L, type, 9L, "later");

        for (AuthorizationRequestObserver observer : new AuthorizationRequestObserver[]{first, second}) {
            verify(observer).afterOpened(1L, type, context);
            verify(observer).afterRejected(2L, type, 7L, "no");
            verify(observer).afterWithdrawn(3L, type, 8L);
            verify(observer).afterExpired(4L, type);
            verify(observer).afterResumeRejected(5L, type, 9L, "later");
        }
    }

    @Test
    void theCompositeNeverForwardsToItselfOrAnotherComposite() {
        AuthorizationRequestObservers nested = observersOver(second);
        AuthorizationRequestObservers observers = observersOver(first, nested);

        observers.afterExpired(4L, type);

        verify(first).afterExpired(4L, type);
        verifyNoInteractions(second);
        verify(first, org.mockito.Mockito.never()).afterOpened(any(), any(), any());
    }
}

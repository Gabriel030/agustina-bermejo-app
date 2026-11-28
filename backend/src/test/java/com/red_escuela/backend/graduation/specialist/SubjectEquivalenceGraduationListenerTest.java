package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.enrollment.events.SubjectEquivalenceRecordedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubjectEquivalenceGraduationListenerTest {

    private final GraduationRequestAutoRequester requester = mock(GraduationRequestAutoRequester.class);
    private final GraduationRequestOutdatedFlagger flagger = mock(GraduationRequestOutdatedFlagger.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final SubjectEquivalenceGraduationListener listener =
            new SubjectEquivalenceGraduationListener(requester, flagger, transactionManager);

    private static SubjectEquivalenceRecordedEvent event() {
        return SubjectEquivalenceRecordedEvent.recordedFor(1L, 5L, 3L, 9L, "Algebra");
    }

    @Test
    void concurrentGraduationRequestCreationDoesNotPropagateToTheEquivalence() {
        when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(mock(TransactionStatus.class));
        doThrow(new DataIntegrityViolationException("uk_graduation_request_active")).when(requester).createIfEligible(anyLong());

        assertThatCode(() -> listener.onEquivalenceRecorded(event())).doesNotThrowAnyException();

        verify(requester).createIfEligible(5L);
    }
}

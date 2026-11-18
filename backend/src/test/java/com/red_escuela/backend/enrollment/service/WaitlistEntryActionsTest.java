package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.exception.WaitlistRemovalBlockedByPendingReceiptException;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitlistEntryActionsTest {

    @Test
    void removalBlockedWhilePendingReceipt() {
        WaitlistEntryLoader loader = mock(WaitlistEntryLoader.class);
        WaitlistQueue queue = mock(WaitlistQueue.class);
        when(loader.loadLocked(WaitlistKind.COURSE, 7L)).thenReturn(new WaitlistEntryContext(queue, null, null, null));
        when(queue.hasReceiptBlockingRemoval(7L)).thenReturn(true);
        WaitlistEntryActions service = new WaitlistEntryActions(loader, null, mock(AuditTrail.class), mock(ApplicationEventPublisher.class));

        assertThrows(WaitlistRemovalBlockedByPendingReceiptException.class, () -> service.remove(WaitlistKind.COURSE, 7L, "Motivo suficiente"));
        verify(queue, never()).remove(any());
    }
}

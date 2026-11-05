package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.service.MpWebhookRawEventRecorder;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;

@UseCase
public class RecordMpWebhookEventService implements RecordMpWebhookEventUseCase {
    private final CurrentBillingModePort currentBillingModePort;
    private final MpWebhookRawEventRecorder rawEventRecorder;

    public RecordMpWebhookEventService(CurrentBillingModePort currentBillingModePort, MpWebhookRawEventRecorder rawEventRecorder) {
        this.currentBillingModePort = currentBillingModePort;
        this.rawEventRecorder = rawEventRecorder;
    }

    @Override
    public boolean recordWhenBillingDoesNotRegisterPayments(String xSignature, String xRequestId, String mpPaymentId) {
        BillingMode billingMode = currentBillingModePort.currentMode();
        if (billingMode.isAtLeast(BillingMode.FULL)) {
            return false;
        }
        rawEventRecorder.recordVerifiedEvent(xSignature, xRequestId, mpPaymentId, billingMode);
        return true;
    }
}

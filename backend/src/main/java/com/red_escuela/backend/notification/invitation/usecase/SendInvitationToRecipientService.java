package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.domain.InvitationSendResult;
import com.red_escuela.backend.notification.invitation.exception.InvitationSendDeferredException;
import com.red_escuela.backend.notification.invitation.specialist.InvitationRecipientSendTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SendInvitationToRecipientService implements SendInvitationToRecipientUseCase {

    private final InvitationRecipientSendTransaction sendTransaction;

    public SendInvitationToRecipientService(InvitationRecipientSendTransaction sendTransaction) {
        this.sendTransaction = sendTransaction;
    }

    @Override
    public InvitationSendResult sendInvitationToRecipient(Long recipientId) {
        try {
            return sendTransaction.sendInvitationToRecipient(recipientId);
        } catch (InvitationSendDeferredException deferred) {
            return InvitationSendResult.DEFERRED;
        }
    }
}

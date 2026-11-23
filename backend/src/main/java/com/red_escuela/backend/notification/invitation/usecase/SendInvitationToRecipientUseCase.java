package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.domain.InvitationSendResult;

public interface SendInvitationToRecipientUseCase {

    InvitationSendResult sendInvitationToRecipient(Long recipientId);
}

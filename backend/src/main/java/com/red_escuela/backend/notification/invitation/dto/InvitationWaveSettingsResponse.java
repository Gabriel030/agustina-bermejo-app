package com.red_escuela.backend.notification.invitation.dto;

public record InvitationWaveSettingsResponse(int globalDailyCap, int maxBatchSize, int maxRecipients,
                                             InvitationWaveSendWindow sendWindow) {
}

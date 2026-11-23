package com.red_escuela.backend.notification.invitation.dto;

import com.red_escuela.backend.notification.invitation.domain.InvitationExclusionReason;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;

import java.time.Instant;

public record InvitationWaveRecipientEntry(Long personId, InvitationRecipientStatus status,
                                           InvitationExclusionReason exclusionReason, int attempts, String lastErrorCode,
                                           Instant sentAt) {
}

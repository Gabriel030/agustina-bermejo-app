package com.red_escuela.backend.notification.invitation.dto;

public record CreateInvitationWaveCommand(String name, Integer batchSize, Integer intervalMinutes, Integer dailyCap, Long importBatchId) {
}

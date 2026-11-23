package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveReportDownload;

public interface DownloadInvitationWaveReportUseCase {
    InvitationWaveReportDownload downloadInvitationWaveReport(Long waveId);
}

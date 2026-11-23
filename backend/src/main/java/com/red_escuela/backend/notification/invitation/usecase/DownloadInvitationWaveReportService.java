package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveReportDownload;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveReportWriter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DownloadInvitationWaveReportService implements DownloadInvitationWaveReportUseCase {

    private final InvitationWaveReportWriter reportWriter;

    public DownloadInvitationWaveReportService(InvitationWaveReportWriter reportWriter) {
        this.reportWriter = reportWriter;
    }

    @Override
    public InvitationWaveReportDownload downloadInvitationWaveReport(Long waveId) {
        return reportWriter.writeInvitationWaveReport(waveId);
    }
}

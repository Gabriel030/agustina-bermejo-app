package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.broadcast.specialist.BroadcastDispatchSettings;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveSendWindow;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveSettingsResponse;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveSettings;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.beans.factory.annotation.Value;

@UseCase
public class GetInvitationWaveSettingsService implements GetInvitationWaveSettingsUseCase {

    private final InvitationWaveSettings settings;
    private final BroadcastDispatchSettings dispatchSettings;
    private final String zone;

    public GetInvitationWaveSettingsService(InvitationWaveSettings settings, BroadcastDispatchSettings dispatchSettings,
                                            @Value("${app.timezone}") String zone) {
        this.settings = settings;
        this.dispatchSettings = dispatchSettings;
        this.zone = zone;
    }

    @Override
    public InvitationWaveSettingsResponse getInvitationWaveSettings() {
        return new InvitationWaveSettingsResponse(settings.globalDailyLimit(), dispatchSettings.maxPerRun(),
                settings.maxRecipients(), new InvitationWaveSendWindow(dispatchSettings.window().start().toString(),
                dispatchSettings.window().end().toString(), zone));
    }
}

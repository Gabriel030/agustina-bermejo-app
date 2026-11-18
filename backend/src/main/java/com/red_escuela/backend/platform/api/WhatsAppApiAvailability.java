package com.red_escuela.backend.platform.api;

import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppApiAvailability {

    private final InstituteSettingsSnapshotHolder snapshotHolder;

    public WhatsAppApiAvailability(InstituteSettingsSnapshotHolder snapshotHolder) {
        this.snapshotHolder = snapshotHolder;
    }

    public boolean isEnabled() {
        return snapshotHolder.current().isEnabled(InstituteSettingCode.WHATSAPP_API);
    }
}

package com.red_escuela.backend.platform.specialist;

import com.red_escuela.backend.platform.domain.InstituteSettingChange;
import com.red_escuela.backend.platform.domain.WhatsAppApiSettingChange;
import com.red_escuela.backend.platform.exception.WhatsappProviderNotConfiguredException;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsAppApiSettingChanger {

    private static final String DEFAULT_REASON = "Cambio del interruptor de WhatsApp por API";

    private final InstituteSettingChanger settingChanger;
    private final WhatsAppProviderConfiguration providerConfiguration;
    private final AuditTrail auditTrail;

    public WhatsAppApiSettingChanger(InstituteSettingChanger settingChanger, WhatsAppProviderConfiguration providerConfiguration, AuditTrail auditTrail) {
        this.settingChanger = settingChanger;
        this.providerConfiguration = providerConfiguration;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public void change(WhatsAppApiSettingChange change) {
        if (change.enabled() && !providerConfiguration.isConfigured()) {
            throw new WhatsappProviderNotConfiguredException(providerConfiguration.name());
        }
        String reason = change.reason() == null || change.reason().isBlank() ? DEFAULT_REASON : change.reason().strip();
        settingChanger.change(new InstituteSettingChange(InstituteSettingCode.WHATSAPP_API, change.enabled(), null, reason));
        auditTrail.record(new AuditRecord(
                change.enabled() ? AuditAction.WHATSAPP_API_ENABLED : AuditAction.WHATSAPP_API_DISABLED,
                AuditEntityType.INSTITUTE_SETTING,
                (long) InstituteSettingCode.WHATSAPP_API.ordinal(),
                String.valueOf(!change.enabled()),
                String.valueOf(change.enabled()),
                reason));
    }
}

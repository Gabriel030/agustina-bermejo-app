package com.red_escuela.backend.platform.specialist;

import com.red_escuela.backend.platform.domain.WhatsAppApiSetting;
import com.red_escuela.backend.platform.entity.InstituteSetting;
import com.red_escuela.backend.platform.exception.InstituteSettingNotProvisionedException;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
import com.red_escuela.backend.platform.repository.InstituteSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WhatsAppApiSettingReader {

    private static final List<String> PENDING_REQUIREMENTS = List.of(
            "Proveedor de WhatsApp configurado",
            "Número de WhatsApp Business verificado con Meta",
            "Plantillas aprobadas por Meta");

    private final InstituteSettingRepository settingRepository;
    private final WhatsAppProviderConfiguration providerConfiguration;
    private final int dailyLimit;

    public WhatsAppApiSettingReader(InstituteSettingRepository settingRepository,
                                    WhatsAppProviderConfiguration providerConfiguration,
                                    @Value("${app.broadcast.providers.whatsapp.daily-limit:250}") int dailyLimit) {
        this.settingRepository = settingRepository;
        this.providerConfiguration = providerConfiguration;
        this.dailyLimit = dailyLimit;
    }

    @Transactional(readOnly = true)
    public WhatsAppApiSetting read() {
        InstituteSetting setting = settingRepository.findById(InstituteSettingCode.WHATSAPP_API)
                .orElseThrow(() -> new InstituteSettingNotProvisionedException(InstituteSettingCode.WHATSAPP_API));
        return new WhatsAppApiSetting(setting.isEnabled(), providerConfiguration.isConfigured(), dailyLimit,
                providerConfiguration.isConfigured() ? List.of() : PENDING_REQUIREMENTS, setting.getVersion());
    }
}

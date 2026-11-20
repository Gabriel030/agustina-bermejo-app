package com.red_escuela.backend.notification.delivery.specialist;

import com.red_escuela.backend.notification.delivery.domain.ProviderReceipt;

import java.util.List;

public interface WhatsAppProvider {

    ProviderReceipt sendTemplate(String to, String templateName, String languageCode, List<String> variables);
}

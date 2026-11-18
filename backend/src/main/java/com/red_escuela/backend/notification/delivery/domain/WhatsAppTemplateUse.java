package com.red_escuela.backend.notification.delivery.domain;

import java.util.Objects;

public record WhatsAppTemplateUse(String templateName, String languageCode, String variablesJson, PricingCategory pricingCategory) {

    public WhatsAppTemplateUse {
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(languageCode, "languageCode");
        Objects.requireNonNull(variablesJson, "variablesJson");
        Objects.requireNonNull(pricingCategory, "pricingCategory");
    }
}

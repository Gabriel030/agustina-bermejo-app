package com.red_escuela.backend.billing.planning;

import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import org.springframework.stereotype.Component;

@Component
public class TestTemplateTermsBootstrap {
    public TestTemplateTermsBootstrap(BillingConfigurationReader configurationReader) {
        TestTemplateTerms.useReader(configurationReader);
    }
}

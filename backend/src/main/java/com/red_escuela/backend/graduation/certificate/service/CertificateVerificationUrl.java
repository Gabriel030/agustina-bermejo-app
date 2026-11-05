package com.red_escuela.backend.graduation.certificate.service;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CertificateVerificationUrl {

    private final String frontendBaseUrl;

    public CertificateVerificationUrl(@Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

    public String of(CertificateCode code) {
        return frontendBaseUrl + "/verificar/" + code.value();
    }
}

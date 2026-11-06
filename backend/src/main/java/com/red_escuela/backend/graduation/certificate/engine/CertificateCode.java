package com.red_escuela.backend.graduation.certificate.engine;

import com.red_escuela.backend.graduation.certificate.exception.InvalidCertificateCodeException;

import java.security.SecureRandom;
import java.util.Locale;

public record CertificateCode(String value) {

    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int LENGTH = 26;
    public static final int ENTROPY_BITS = LENGTH * 5;

    public CertificateCode {
        if (value == null || value.length() != LENGTH || !value.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)) {
            throw new InvalidCertificateCodeException();
        }
    }

    public static CertificateCode generate(SecureRandom random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new CertificateCode(code.toString());
    }

    public static CertificateCode parse(String raw) {
        if (raw == null) {
            throw new InvalidCertificateCodeException();
        }
        String normalized = raw.replace("-", "").replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        return new CertificateCode(normalized);
    }

    public String formatted() {
        return value.substring(0, 5) + "-" + value.substring(5, 10) + "-" + value.substring(10, 15) + "-"
                + value.substring(15, 20) + "-" + value.substring(20, 26);
    }
}

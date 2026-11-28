package com.red_escuela.backend.graduation.certificate.engine;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class DefaultInstituteLogo {

    private static final String RESOURCE_PATH = "graduation/default-institute-logo.png";
    private static final byte[] BYTES = load();

    private DefaultInstituteLogo() {
    }

    public static byte[] bytes() {
        return BYTES.clone();
    }

    private static byte[] load() {
        try (InputStream stream = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            return stream.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}

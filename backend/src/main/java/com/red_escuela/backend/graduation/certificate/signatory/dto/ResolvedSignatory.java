package com.red_escuela.backend.graduation.certificate.signatory.dto;

import org.springframework.lang.Nullable;

public record ResolvedSignatory(Long id, String name, String role, @Nullable SignatureImage signature) {

    public record SignatureImage(String contentType, long sizeBytes, int widthPixels, int heightPixels, String sha256, byte[] content) {
    }
}

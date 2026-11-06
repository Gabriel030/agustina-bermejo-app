package com.red_escuela.backend.graduation.certificate.verification.service;

public final class DocumentMasker {

    private static final int VISIBLE_SUFFIX = 3;

    private DocumentMasker() {
    }

    public static String mask(String document) {
        if (document == null) {
            return "";
        }
        int visible = Math.min(VISIBLE_SUFFIX, document.length() / 2);
        int hidden = document.length() - visible;
        return "*".repeat(hidden) + document.substring(hidden);
    }
}

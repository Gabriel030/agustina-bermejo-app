package com.red_escuela.backend.billing.payment.domain;

import java.util.Set;

public final class ReceiptFileSignature {

    public static final String PDF = "application/pdf";
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(PDF, JPEG, PNG);

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private ReceiptFileSignature() {
    }

    public static String detectContentType(byte[] content) {
        if (startsWith(content, PDF_MAGIC)) {
            return PDF;
        }
        if (startsWith(content, JPEG_MAGIC)) {
            return JPEG;
        }
        if (startsWith(content, PNG_MAGIC)) {
            return PNG;
        }
        return null;
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content == null || content.length < magic.length) {
            return false;
        }
        for (int index = 0; index < magic.length; index++) {
            if (content[index] != magic[index]) {
                return false;
            }
        }
        return true;
    }
}

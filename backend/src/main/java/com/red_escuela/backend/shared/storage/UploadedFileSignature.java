package com.red_escuela.backend.shared.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

final class UploadedFileSignature {
    private static final Map<String, byte[]> MAGIC_BY_EXTENSION = Map.of(
            "pdf", new byte[]{'%', 'P', 'D', 'F', '-'},
            "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}
    );

    private UploadedFileSignature() {
    }

    static boolean matches(String extension, InputStream content) throws IOException {
        byte[] magic = MAGIC_BY_EXTENSION.get(extension);
        if (magic == null) {
            return false;
        }
        byte[] header = content.readNBytes(magic.length);
        return Arrays.equals(header, magic);
    }
}

package com.red_escuela.backend.shared.storage;

import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class UploadTestFiles {

    private static final byte[] EXECUTABLE = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};

    private UploadTestFiles() {
    }

    public static MockMultipartFile renamedExecutable() {
        return new MockMultipartFile("file", "proof.pdf", "application/pdf", EXECUTABLE);
    }

    public static MockMultipartFile declaredPdfWithPngBytes() {
        return new MockMultipartFile("file", "proof.pdf", "application/pdf", PNG);
    }

    public static MockMultipartFile validPdf() {
        return new MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-1.4 body".getBytes(StandardCharsets.US_ASCII));
    }

    public static long countFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).count();
        }
    }
}

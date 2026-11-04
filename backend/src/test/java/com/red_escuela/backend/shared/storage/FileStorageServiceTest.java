package com.red_escuela.backend.shared.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    private static final byte[] PDF = "%PDF-1.4 body".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    private static final byte[] HTML = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path uploads;

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorageService(uploads.toString());
    }

    @Test
    void htmlDeclaredAsPdfIsRejectedAndNothingIsWritten() throws IOException {
        MockMultipartFile disguised = new MockMultipartFile("file", "a.pdf", "application/pdf", HTML);

        assertThatThrownBy(() -> storage.store(1L, "docs", disguised)).isInstanceOf(UnsupportedFileTypeException.class);
        assertThat(filesUnder(uploads)).isEmpty();
    }

    @Test
    void aPdfDeclaredAsPngIsRejected() {
        MockMultipartFile mismatched = new MockMultipartFile("file", "a.png", "image/png", PDF);

        assertThatThrownBy(() -> storage.store(1L, "docs", mismatched)).isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void anEmptyFileIsRejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> storage.store(1L, "docs", empty)).isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void aDeclaredTypeOutsideTheAllowListIsRejected() {
        MockMultipartFile html = new MockMultipartFile("file", "a.html", "text/html", HTML);

        assertThatThrownBy(() -> storage.store(1L, "docs", html)).isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void genuinePdfJpegAndPngAreStoredWithTheirExtension() {
        String pdf = storage.store(1L, "docs", new MockMultipartFile("file", "a.pdf", "application/pdf", PDF));
        String jpeg = storage.store(1L, "docs", new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG));
        String png = storage.store(1L, "docs", new MockMultipartFile("file", "a.png", "image/png", PNG));

        assertThat(pdf).endsWith(".pdf");
        assertThat(jpeg).endsWith(".jpg");
        assertThat(png).endsWith(".png");
        assertThat(storage.exists(pdf)).isTrue();
    }

    @Test
    void avatarWithSpoofedContentTypeIsRejected() {
        MockMultipartFile disguised = new MockMultipartFile("file", "a.png", "image/png", HTML);

        assertThatThrownBy(() -> storage.storeAvatar(1L, disguised)).isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void genuineAvatarIsStored() {
        assertThat(storage.storeAvatar(1L, new MockMultipartFile("file", "a.png", "image/png", PNG))).endsWith(".png");
    }

    @Test
    void qrWithSpoofedContentTypeIsRejected() {
        MockMultipartFile disguised = new MockMultipartFile("file", "q.jpg", "image/jpeg", HTML);

        assertThatThrownBy(() -> storage.storeQr(disguised)).isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void genuineQrIsStored() {
        assertThat(storage.storeQr(new MockMultipartFile("file", "q.jpg", "image/jpeg", JPEG))).endsWith(".jpg");
    }

    private static Stream<Path> walk(Path root) throws IOException {
        return Files.walk(root).filter(Files::isRegularFile);
    }

    private static java.util.List<Path> filesUnder(Path root) throws IOException {
        try (Stream<Path> files = walk(root)) {
            return files.toList();
        }
    }
}

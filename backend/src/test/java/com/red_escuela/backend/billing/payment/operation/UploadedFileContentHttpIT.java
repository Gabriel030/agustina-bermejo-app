package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import com.red_escuela.backend.shared.exception.GlobalExceptionHandler;
import com.red_escuela.backend.shared.storage.UnsupportedFileTypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadedFileContentHttpIT extends AbstractBankTransferReceiptHttpIT {

    private static final String UNSUPPORTED_FILE_TYPE = "UNSUPPORTED_FILE_TYPE";
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] HTML_BYTES = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EXECUTABLE_BYTES = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00};
    private static final byte[] TRUNCATED_PDF_BYTES = {'%', 'P', 'D', 'F'};
    private static final byte[] TRUNCATED_PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] EMPTY_BYTES = {};
    private static final byte[] FLIPPED_LAST_PDF_BYTE = {'%', 'P', 'D', 'F', '.'};

    @Autowired
    BankTransferReceiptRepository receiptRepository;
    @Value("${spring.servlet.multipart.max-file-size}")
    DataSize maxFileSize;
    @Value("${spring.servlet.multipart.max-request-size}")
    DataSize maxRequestSize;
    @Autowired
    GlobalExceptionHandler exceptionHandler;

    static Stream<Arguments> rejectedReceiptUploads() {
        return Stream.of(
                Arguments.of("html renamed as pdf", "comprobante.pdf", "application/pdf", HTML_BYTES),
                Arguments.of("executable renamed as pdf", "comprobante.pdf", "application/pdf", EXECUTABLE_BYTES),
                Arguments.of("empty pdf", "comprobante.pdf", "application/pdf", EMPTY_BYTES),
                Arguments.of("truncated pdf signature", "comprobante.pdf", "application/pdf", TRUNCATED_PDF_BYTES),
                Arguments.of("pdf signature off by one byte", "comprobante.pdf", "application/pdf", FLIPPED_LAST_PDF_BYTE),
                Arguments.of("truncated png signature", "comprobante.png", "image/png", TRUNCATED_PNG_BYTES),
                Arguments.of("real pdf declared as png", "comprobante.png", "image/png", withSignature(PDF_SIGNATURE)),
                Arguments.of("real png declared as pdf", "comprobante.pdf", "application/pdf", withSignature(PNG_SIGNATURE)),
                Arguments.of("real jpeg declared as png", "comprobante.png", "image/png", withSignature(JPEG_SIGNATURE)),
                Arguments.of("html with html content type", "comprobante.html", "text/html", HTML_BYTES),
                Arguments.of("executable with exe extension", "installer.exe", "application/x-msdownload", EXECUTABLE_BYTES),
                Arguments.of("real pdf with octet-stream content type", "comprobante.pdf", "application/octet-stream", withSignature(PDF_SIGNATURE)),
                Arguments.of("real pdf with missing content type", "comprobante.pdf", null, withSignature(PDF_SIGNATURE)),
                Arguments.of("html with double extension", "comprobante.pdf.html", "text/html", HTML_BYTES),
                Arguments.of("executable with double extension declared as pdf", "comprobante.pdf.exe", "application/pdf", EXECUTABLE_BYTES),
                Arguments.of("html with path traversal name", "../../comprobante.pdf", "application/pdf", HTML_BYTES),
                Arguments.of("html with null byte name", "a\u0000.pdf", "application/pdf", HTML_BYTES)
        );
    }

    static Stream<Arguments> rejectedAvatarUploads() {
        return Stream.of(
                Arguments.of("html renamed as png", "avatar.png", "image/png", HTML_BYTES),
                Arguments.of("executable renamed as jpg", "avatar.jpg", "image/jpeg", EXECUTABLE_BYTES),
                Arguments.of("empty png", "avatar.png", "image/png", EMPTY_BYTES),
                Arguments.of("truncated png signature", "avatar.png", "image/png", TRUNCATED_PNG_BYTES),
                Arguments.of("real pdf declared as png", "avatar.png", "image/png", withSignature(PDF_SIGNATURE)),
                Arguments.of("real png declared as jpeg", "avatar.jpg", "image/jpeg", withSignature(PNG_SIGNATURE)),
                Arguments.of("real png declared as pdf", "avatar.pdf", "application/pdf", withSignature(PNG_SIGNATURE)),
                Arguments.of("svg with script", "avatar.svg", "image/svg+xml", "<svg onload=alert(1)/>".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("html with double extension", "avatar.png.html", "text/html", HTML_BYTES),
                Arguments.of("html with path traversal name", "../../avatar.png", "image/png", HTML_BYTES)
        );
    }

    private static byte[] withSignature(byte[] signature) {
        byte[] body = " body".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[signature.length + body.length];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        System.arraycopy(body, 0, bytes, signature.length, body.length);
        return bytes;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedReceiptUploads")
    void legacyReceiptUploadRejectsFileWhoseContentOrTypeIsNotAnAllowedMatch(String scenario, String fileName, String contentType, byte[] content) throws Exception {
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", TODAY);
        String token = studentTokenOf(debtor);
        long receiptsBefore = receiptRepository.count();

        mockMvc.perform(multipart("/me/invoices/{id}/bank-transfer-receipts", invoice)
                        .file(new MockMultipartFile("comprobante", fileName, contentType, content))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(UNSUPPORTED_FILE_TYPE));

        assertThat(receiptRepository.count()).isEqualTo(receiptsBefore);
        assertThat(filesStoredFor(debtor.studentId())).isZero();
    }

    @Test
    void legacyReceiptUploadWithoutTheFilePartIsRejectedWithoutTheUnsupportedFileTypeCode() throws Exception {
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", TODAY);
        MvcResult result = mockMvc.perform(multipart("/me/invoices/{id}/bank-transfer-receipts", invoice)
                        .header("Authorization", "Bearer " + studentTokenOf(debtor)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(UNSUPPORTED_FILE_TYPE);
        assertThat(filesStoredFor(debtor.studentId())).isZero();
    }

    @Test
    void legacyReceiptUploadAcceptsARealPdfAndStoresExactlyOneFileAndOneReceipt() throws Exception {
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", TODAY);
        long receiptsBefore = receiptRepository.count();

        mockMvc.perform(multipart("/me/invoices/{id}/bank-transfer-receipts", invoice)
                        .file(new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", pdfBytes("real")))
                        .header("Authorization", "Bearer " + studentTokenOf(debtor)))
                .andExpect(status().isCreated());

        assertThat(receiptRepository.count()).isEqualTo(receiptsBefore + 1);
        assertThat(filesStoredFor(debtor.studentId())).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedReceiptNames")
    void legacyReceiptUploadAcceptsARealFileAndIgnoresTheClientFileNameWhenStoring(String scenario, String fileName) throws Exception {
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", TODAY);

        MvcResult result = mockMvc.perform(multipart("/me/invoices/{id}/bank-transfer-receipts", invoice)
                        .file(new MockMultipartFile("comprobante", fileName, "application/pdf", pdfBytes("name")))
                        .header("Authorization", "Bearer " + studentTokenOf(debtor)))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("..").doesNotContain("evil");
        assertThat(filesStoredFor(debtor.studentId())).isEqualTo(1);
        Path studentDirectory = Path.of(uploadsDirectory).resolve("students").resolve(debtor.studentId().toString());
        try (Stream<Path> stored = Files.list(studentDirectory)) {
            assertThat(stored.map(path -> path.getFileName().toString()))
                    .allMatch(name -> name.startsWith("comprobantes_") && name.endsWith(".pdf") && !name.contains("evil"));
        }
        assertThat(Files.exists(Path.of(uploadsDirectory).resolve("evil.pdf"))).isFalse();
        assertThat(Files.exists(Path.of(uploadsDirectory).getParent().resolve("evil.pdf"))).isFalse();
    }

    static Stream<Arguments> acceptedReceiptNames() {
        return Stream.of(
                Arguments.of("path traversal name", "../../evil.pdf"),
                Arguments.of("absolute path name", "/etc/evil.pdf"),
                Arguments.of("windows path name", "..\\..\\evil.pdf"),
                Arguments.of("double extension with exe", "evil.pdf.exe"),
                Arguments.of("double extension with html", "evil.html.pdf")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedAvatarUploads")
    void avatarUploadRejectsFileWhoseContentOrTypeIsNotAnAllowedMatchAndKeepsTheCurrentAvatar(String scenario, String fileName, String contentType, byte[] content) throws Exception {
        Debtor debtor = debtor();
        String token = studentTokenOf(debtor);
        Long personId = personOf(debtor).getId();
        mockMvc.perform(multipart("/me/avatar")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", pngBytes("current")))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        String avatarBefore = avatarPathOf(personId);
        long filesBefore = avatarFilesOf(personId);
        assertThat(avatarBefore).isNotNull();

        mockMvc.perform(multipart("/me/avatar")
                        .file(new MockMultipartFile("file", fileName, contentType, content))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(UNSUPPORTED_FILE_TYPE));

        assertThat(avatarPathOf(personId)).isEqualTo(avatarBefore);
        assertThat(avatarFilesOf(personId)).isEqualTo(filesBefore);
    }

    @Test
    void avatarUploadRejectsHtmlRenamedAsPngWhenThereIsNoPreviousAvatarAndStoresNothing() throws Exception {
        Debtor debtor = debtor();
        Long personId = personOf(debtor).getId();

        mockMvc.perform(multipart("/me/avatar")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", HTML_BYTES))
                        .header("Authorization", "Bearer " + studentTokenOf(debtor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(UNSUPPORTED_FILE_TYPE));

        assertThat(avatarPathOf(personId)).isNull();
        assertThat(avatarFilesOf(personId)).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedAvatars")
    void avatarUploadAcceptsARealImageAndReplacesTheAvatarPath(String scenario, String fileName, String contentType, byte[] content, String extension) throws Exception {
        Debtor debtor = debtor();
        Long personId = personOf(debtor).getId();

        mockMvc.perform(multipart("/me/avatar")
                        .file(new MockMultipartFile("file", fileName, contentType, content))
                        .header("Authorization", "Bearer " + studentTokenOf(debtor)))
                .andExpect(status().isNoContent());

        assertThat(avatarPathOf(personId)).endsWith("." + extension).doesNotContain("evil");
        assertThat(avatarFilesOf(personId)).isEqualTo(1);
    }

    static Stream<Arguments> acceptedAvatars() {
        return Stream.of(
                Arguments.of("real png", "avatar.png", "image/png", pngBytes("real"), "png"),
                Arguments.of("real jpeg", "avatar.jpg", "image/jpeg", withSignature(JPEG_SIGNATURE), "jpg"),
                Arguments.of("real png with path traversal name", "../../evil.png", "image/png", pngBytes("real"), "png")
        );
    }

    @Test
    void multipartLimitIsExactlyFiveMegabytesPerFile() {
        assertThat(maxFileSize).isEqualTo(DataSize.ofMegabytes(5));
        assertThat(maxRequestSize).isEqualTo(DataSize.ofMegabytes(10));
    }

    @Test
    void oversizedUploadIsMappedToABadRequestWithTheMaxUploadSizeCode() {
        var response = exceptionHandler.handleBadRequest(new MaxUploadSizeExceededException(DataSize.ofMegabytes(5).toBytes()));
        assertThat(response.code()).isEqualTo(ErrorCodeResponse.codeOf(MaxUploadSizeExceededException.class));
        assertThat(response.code()).isEqualTo("MAX_UPLOAD_SIZE_EXCEEDED");
    }

    @Test
    void unsupportedFileTypeExceptionKeepsTheCodeTheFrontendMaps() {
        assertThat(ErrorCodeResponse.codeOf(UnsupportedFileTypeException.class)).isEqualTo(UNSUPPORTED_FILE_TYPE);
    }

    @Test
    void storedReceiptIsServedWithNosniffAndFixedPdfContentTypeEvenIfUploadedWithAnHtmlFileName() throws Exception {
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", TODAY);
        MvcResult uploaded = mockMvc.perform(multipart("/me/invoices/{id}/bank-transfer-receipts", invoice)
                        .file(new MockMultipartFile("comprobante", "evil.html", "application/pdf", pdfBytes("download")))
                        .header("Authorization", "Bearer " + studentTokenOf(debtor)))
                .andExpect(status().isCreated())
                .andReturn();
        long receiptId = bodyOf(uploaded).get("id").asLong();

        mockMvc.perform(get("/bank-transfer-receipts/{id}/file", receiptId)
                        .header("Authorization", "Bearer " + staffToken("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    private String avatarPathOf(Long personId) {
        return jdbc.queryForObject("select avatar_path from person where id = ?", String.class, personId);
    }

    private long avatarFilesOf(Long personId) throws IOException {
        Path directory = Path.of(uploadsDirectory).resolve("avatars");
        if (!Files.exists(directory)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().startsWith(personId + "_")).count();
        }
    }
}

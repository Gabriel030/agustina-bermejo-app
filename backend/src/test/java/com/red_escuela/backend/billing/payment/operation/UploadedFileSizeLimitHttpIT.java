package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UploadedFileSizeLimitHttpIT extends AbstractBankTransferReceiptHttpIT {

    private static final String BOUNDARY = "----UploadedFileSizeLimitBoundary";
    private static final byte[] PDF_SIGNATURE = "%PDF-1.4 ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @LocalServerPort
    int port;
    @Value("${spring.servlet.multipart.max-file-size}")
    DataSize maxFileSize;
    @Autowired
    BankTransferReceiptRepository receiptRepository;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void receiptAtSizeLimitIsAccepted() throws Exception {
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", TODAY);
        long receiptsBefore = receiptRepository.count();

        HttpResponse<String> response = upload("/me/invoices/" + invoice + "/bank-transfer-receipts", "comprobante",
                "comprobante.pdf", "application/pdf", paddedTo(PDF_SIGNATURE, limitInBytes()), studentTokenOf(debtor));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(receiptRepository.count()).isEqualTo(receiptsBefore + 1);
        assertThat(filesStoredFor(debtor.studentId())).isEqualTo(1);
    }

    @Test
    void receiptOneByteOverLimitIsRejectedWithoutStoring() throws Exception {
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", TODAY);
        long receiptsBefore = receiptRepository.count();

        HttpResponse<String> response = upload("/me/invoices/" + invoice + "/bank-transfer-receipts", "comprobante",
                "comprobante.pdf", "application/pdf", paddedTo(PDF_SIGNATURE, limitInBytes() + 1), studentTokenOf(debtor));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains(maxUploadSizeCode());
        assertThat(receiptRepository.count()).isEqualTo(receiptsBefore);
        assertThat(filesStoredFor(debtor.studentId())).isZero();
    }

    @Test
    void avatarAtSizeLimitIsAccepted() throws Exception {
        Debtor debtor = debtor();
        Long personId = personOf(debtor).getId();

        HttpResponse<String> response = upload("/me/avatar", "file", "avatar.png", "image/png",
                paddedTo(PNG_SIGNATURE, limitInBytes()), studentTokenOf(debtor));

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(avatarPathOf(personId)).endsWith(".png");
        assertThat(avatarFilesOf(personId)).isEqualTo(1);
    }

    @Test
    void avatarOneByteOverLimitIsRejectedWithoutStoring() throws Exception {
        Debtor debtor = debtor();
        Long personId = personOf(debtor).getId();

        HttpResponse<String> response = upload("/me/avatar", "file", "avatar.png", "image/png",
                paddedTo(PNG_SIGNATURE, limitInBytes() + 1), studentTokenOf(debtor));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains(maxUploadSizeCode());
        assertThat(avatarPathOf(personId)).isNull();
        assertThat(avatarFilesOf(personId)).isZero();
    }

    private int limitInBytes() {
        assertThat(maxFileSize).isEqualTo(DataSize.ofMegabytes(5));
        return Math.toIntExact(maxFileSize.toBytes());
    }

    private static String maxUploadSizeCode() {
        return ErrorCodeResponse.codeOf(MaxUploadSizeExceededException.class);
    }

    private static byte[] paddedTo(byte[] signature, int size) {
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 'a');
        System.arraycopy(signature, 0, content, 0, signature.length);
        return content;
    }

    private HttpResponse<String> upload(String path, String partName, String fileName, String contentType, byte[] content, String token)
            throws IOException, InterruptedException {
        ByteArrayOutputStream body = new ByteArrayOutputStream(content.length + 512);
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + fileName
                + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
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

package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.service.PaymentReceiptPersister;
import com.red_escuela.backend.shared.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadPaymentReceiptDiskOrderTest {

    @TempDir
    Path uploads;

    private final PaymentReceiptPersister persister = mock(PaymentReceiptPersister.class);
    private UploadPaymentReceiptService service;
    private final MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1".getBytes());

    @BeforeEach
    void setUp() {
        service = new UploadPaymentReceiptService(new FileStorageService(uploads.toString()), persister);
    }

    @Test
    void foreignInvoiceUploadWritesNothingToDisk() throws IOException {
        doThrow(new InvoiceNotFoundException(10L)).when(persister).assertUploadable(10L, 7L);
        long before = countEntries();

        assertThatThrownBy(() -> service.upload(10L, 7L, file)).isInstanceOf(InvoiceNotFoundException.class);

        assertThat(countEntries()).isEqualTo(before);
    }

    @Test
    void missingInvoiceUploadWritesNothingToDisk() throws IOException {
        doThrow(new InvoiceNotFoundException(99L)).when(persister).assertUploadable(99L, 7L);
        long before = countEntries();

        assertThatThrownBy(() -> service.upload(99L, 7L, file)).isInstanceOf(InvoiceNotFoundException.class);

        assertThat(countEntries()).isEqualTo(before);
    }

    @Test
    void ownInvoiceUploadStillStoresTheFile() throws IOException {
        when(persister.persist(any(), any(), anyString())).thenReturn(new BankTransferReceipt());

        service.upload(5L, 7L, file);

        try (Stream<Path> files = Files.walk(uploads.resolve("students").resolve("7"))) {
            assertThat(files.filter(Files::isRegularFile)).hasSize(1);
        }
    }

    private long countEntries() throws IOException {
        try (Stream<Path> files = Files.walk(uploads)) {
            return files.count();
        }
    }
}

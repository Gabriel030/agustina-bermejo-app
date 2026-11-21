package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.service.PaymentReceiptPersister;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.UnsupportedFileTypeException;
import com.red_escuela.backend.shared.storage.UploadTestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadPaymentReceiptSignatureTest {

    @TempDir
    Path uploads;

    private final PaymentReceiptPersister persister = mock(PaymentReceiptPersister.class);
    private UploadPaymentReceiptService service;

    @BeforeEach
    void setUp() {
        service = new UploadPaymentReceiptService(new FileStorageService(uploads.toString()), persister);
    }

    @Test
    void renamedExecutableIsRejected() throws IOException {
        assertThatThrownBy(() -> service.upload(5L, 7L, UploadTestFiles.renamedExecutable()))
                .isInstanceOf(UnsupportedFileTypeException.class);

        assertThat(UploadTestFiles.countFiles(uploads)).isZero();
        verify(persister, never()).persist(any(), any(), anyString());
    }

    @Test
    void declaredPdfWithOtherBytesIsRejected() throws IOException {
        assertThatThrownBy(() -> service.upload(5L, 7L, UploadTestFiles.declaredPdfWithPngBytes()))
                .isInstanceOf(UnsupportedFileTypeException.class);

        assertThat(UploadTestFiles.countFiles(uploads)).isZero();
    }

    @Test
    void validFileStillStored() throws IOException {
        when(persister.persist(any(), any(), anyString())).thenReturn(new BankTransferReceipt());

        service.upload(5L, 7L, UploadTestFiles.validPdf());

        assertThat(UploadTestFiles.countFiles(uploads)).isEqualTo(1);
    }
}

package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.service.PaymentReceiptPersister;
import com.red_escuela.backend.shared.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadPaymentReceiptServiceTest {

    private final FileStorageService storage = mock(FileStorageService.class);
    private final PaymentReceiptPersister persister = mock(PaymentReceiptPersister.class);
    private final UploadPaymentReceiptService service = new UploadPaymentReceiptService(storage, persister);
    private final MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1".getBytes());

    @Test
    void theStoredFileIsDeletedWhenThePersisterRejectsTheUpload() {
        when(storage.store(7L, "comprobantes", file)).thenReturn("students/7/c.pdf");
        when(persister.persist(99L, 7L, "students/7/c.pdf")).thenThrow(new InvoiceNotFoundException(99L));

        assertThatThrownBy(() -> service.upload(99L, 7L, file)).isInstanceOf(InvoiceNotFoundException.class);

        verify(storage).delete("students/7/c.pdf");
    }

    @Test
    void theStoredFileIsKeptWhenThePersisterAccepts() {
        BankTransferReceipt receipt = new BankTransferReceipt();
        when(storage.store(7L, "comprobantes", file)).thenReturn("students/7/c.pdf");
        when(persister.persist(5L, 7L, "students/7/c.pdf")).thenReturn(receipt);

        assertThat(service.upload(5L, 7L, file)).isSameAs(receipt);

        verify(storage, never()).delete(any());
    }
}

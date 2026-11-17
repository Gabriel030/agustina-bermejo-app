package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadedFileContentHttpIT extends AbstractBankTransferReceiptHttpIT {

    @Autowired
    UploadPaymentReceiptUseCase legacyUpload;

    private static MockMultipartFile spoofedAsPdf(String part, String content) {
        return new MockMultipartFile(part, "comprobante.pdf", "application/pdf", content.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile spoofedAsPng(String part) {
        return new MockMultipartFile(part, "avatar.png", "image/png", "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void legacyReceiptUploadRejectsHtmlAndExecutableRenamedAsPdf() throws Exception {
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", TODAY);
        String token = studentTokenOf(debtor);
        for (String content : new String[]{"<html><script>alert(1)</script></html>", "MZ\u0090\u0000\u0003 executable"}) {
            mockMvc.perform(multipart("/me/invoices/{id}/bank-transfer-receipts", invoice)
                            .file(spoofedAsPdf("comprobante", content)).header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void legacyReceiptUploadAcceptsARealPdf() throws Exception {
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", TODAY);
        mockMvc.perform(multipart("/me/invoices/{id}/bank-transfer-receipts", invoice)
                        .file(new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", pdfBytes("real")))
                        .header("Authorization", "Bearer " + studentTokenOf(debtor)))
                .andExpect(status().isCreated());
    }

    @Test
    void avatarUploadRejectsHtmlRenamedAsPngAndAcceptsARealPng() throws Exception {
        Debtor debtor = debtor();
        String token = studentTokenOf(debtor);
        mockMvc.perform(multipart("/me/avatar").file(spoofedAsPng("file")).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/me/avatar")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", pngBytes("real")))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void receiptDownloadCarriesNosniffAndTheServerFixedContentType() throws Exception {
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", TODAY);
        BankTransferReceipt uploaded = legacyUpload.upload(invoice, debtor.studentId(),
                new MockMultipartFile("comprobante", "evil.html", "application/pdf", pdfBytes("download")));
        mockMvc.perform(get("/bank-transfer-receipts/{id}/file", uploaded.getId())
                        .header("Authorization", "Bearer " + staffToken("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "application/pdf"));
    }
}

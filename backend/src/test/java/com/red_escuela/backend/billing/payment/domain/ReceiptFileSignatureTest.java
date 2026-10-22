package com.red_escuela.backend.billing.payment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptFileSignatureTest {

    @Test
    void detectsEachSupportedSignature() {
        assertThat(ReceiptFileSignature.detectContentType("%PDF-1.7".getBytes())).isEqualTo("application/pdf");
        assertThat(ReceiptFileSignature.detectContentType(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("image/jpeg");
        assertThat(ReceiptFileSignature.detectContentType(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1})).isEqualTo("image/png");
    }

    @Test
    void rejectsTruncatedOrUnknownContent() {
        assertThat(ReceiptFileSignature.detectContentType("%PDF".getBytes())).isNull();
        assertThat(ReceiptFileSignature.detectContentType(new byte[]{(byte) 0xFF, (byte) 0xD8})).isNull();
        assertThat(ReceiptFileSignature.detectContentType(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0B})).isNull();
        assertThat(ReceiptFileSignature.detectContentType("MZ".getBytes())).isNull();
        assertThat(ReceiptFileSignature.detectContentType(null)).isNull();
    }
}

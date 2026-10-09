package com.red_escuela.backend.shared.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-233")
class DocumentNumberTest {

    @Test
    void dniAcceptsSevenAndEightDigits() {
        assertThat(DocumentNumber.of(DocumentType.DNI, "1234567").value()).isEqualTo("1234567");
        assertThat(DocumentNumber.of(DocumentType.DNI, " 12345678 ").value()).isEqualTo("12345678");
    }

    @Test
    void dniRejectsLettersSeparatorsAndWrongLengths() {
        for (String invalid : new String[] {"123456", "123456789", "12.345.678", "1234 5678", "1234567A", ""}) {
            assertThatThrownBy(() -> DocumentNumber.of(DocumentType.DNI, invalid))
                    .isInstanceOf(InvalidDocumentNumberException.class);
        }
    }

    @Test
    void passportAndOtherAcceptAlphanumericFourToTwentyAndNormalizeToUppercase() {
        assertThat(DocumentNumber.of(DocumentType.PASSPORT, "ab1234").value()).isEqualTo("AB1234");
        assertThat(DocumentNumber.of(DocumentType.OTHER, "abcd").value()).isEqualTo("ABCD");
        assertThat(DocumentNumber.of(DocumentType.OTHER, "A".repeat(20)).value()).hasSize(20);
    }

    @Test
    void passportAndOtherRejectShortLongAndSymbols() {
        for (DocumentType type : new DocumentType[] {DocumentType.PASSPORT, DocumentType.OTHER}) {
            for (String invalid : new String[] {"abc", "A".repeat(21), "AB-1234", "AB 1234", "AB.1234"}) {
                assertThatThrownBy(() -> DocumentNumber.of(type, invalid))
                        .isInstanceOf(InvalidDocumentNumberException.class);
            }
        }
    }

    @Test
    void sameNumberWithDifferentTypesAreDifferentDocuments() {
        assertThat(DocumentNumber.of(DocumentType.DNI, "12345678"))
                .isNotEqualTo(DocumentNumber.of(DocumentType.OTHER, "12345678"));
    }
}

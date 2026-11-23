package com.red_escuela.backend.graduation.debtexception.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.red_escuela.backend.graduation.api.DebtExceptionDocument;
import com.red_escuela.backend.graduation.api.DebtExceptionDocumentIssuer;
import com.red_escuela.backend.graduation.api.DebtExceptionScope;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DebtExceptionDocumentIssuersTest {

    @Test
    void routesEachTypeToItsOwnIssuer() {
        FakeIssuer exam = new FakeIssuer(CertificateDocumentType.EXAM_CERTIFICATE);
        FakeIssuer regular = new FakeIssuer(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE);
        DebtExceptionDocumentIssuers issuers = new DebtExceptionDocumentIssuers(List.of(exam, regular));

        assertThat(issuers.forType(CertificateDocumentType.EXAM_CERTIFICATE)).containsSame(exam);
        assertThat(issuers.forType(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE)).containsSame(regular);
    }

    @Test
    void typeWithoutIssuerIsEmpty() {
        DebtExceptionDocumentIssuers issuers = new DebtExceptionDocumentIssuers(List.of());

        assertThat(issuers.forType(CertificateDocumentType.GRADUATION_CERTIFICATE)).isEmpty();
    }

    @Test
    void duplicateIssuersForATypeFailAtStartup() {
        List<DebtExceptionDocumentIssuer> duplicated = List.of(new FakeIssuer(CertificateDocumentType.EXAM_CERTIFICATE),
                new FakeIssuer(CertificateDocumentType.EXAM_CERTIFICATE));

        assertThatThrownBy(() -> new DebtExceptionDocumentIssuers(duplicated)).isInstanceOf(IllegalStateException.class);
    }

    private record FakeIssuer(CertificateDocumentType type) implements DebtExceptionDocumentIssuer {

        @Override
        public boolean supports(CertificateDocumentType candidate) {
            return candidate == type;
        }

        @Override
        public void requireScopeBelongsToStudent(DebtExceptionScope scope) {
        }

        @Override
        public DebtExceptionDocument render(DebtExceptionScope scope) {
            return new DebtExceptionDocument("fake", new byte[0], null);
        }
    }
}

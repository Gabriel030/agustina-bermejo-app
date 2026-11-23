package com.red_escuela.backend.graduation.certificate.verification;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.specialist.VerifiableDocumentRegistry;
import com.red_escuela.backend.graduation.certificate.verification.specialist.DocumentVerificationSource;
import com.red_escuela.backend.graduation.certificate.verification.specialist.PublicDocumentLookup;
import com.red_escuela.backend.graduation.certificate.verification.specialist.PublicVerificationRateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicDocumentLookupTest {

    private static DocumentVerificationSource sourceOf(CertificateDocumentType type) {
        DocumentVerificationSource source = mock(DocumentVerificationSource.class);
        when(source.type()).thenReturn(type);
        return source;
    }

    @Test
    void twoSourcesForTheSameTypeFailAtStartup() {
        List<DocumentVerificationSource> sources = List.of(sourceOf(CertificateDocumentType.EXAM_CERTIFICATE),
                sourceOf(CertificateDocumentType.EXAM_CERTIFICATE));

        assertThatThrownBy(() -> new PublicDocumentLookup(sources, mock(VerifiableDocumentRegistry.class),
                mock(PublicVerificationRateLimiter.class), new SimpleMeterRegistry(), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXAM_CERTIFICATE");
    }
}

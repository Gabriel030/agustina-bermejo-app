package com.red_escuela.backend.graduation.certificate.verification;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTemplateResolver;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.regular.service.RegularCertificatePdfProvider;
import com.red_escuela.backend.graduation.certificate.regular.service.RegularCertificateTemplate;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.service.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.service.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.certificate.service.GraduationCertificatePdfProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfProviderLogsTest {

    private static final String CODE = "7QK3M9XH2VBN4T8R5W6YZ0A1B2";

    private final CertificateSnapshot snapshot = new CertificateSnapshot("PEREZ, ANA", "DNI", "30123456", "la carrera", "Tec", null,
            1800, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto", List.of(), "BUILT_IN_V1");
    private final CertificateFileStore fileStore = mock(CertificateFileStore.class);
    private final CertificatePdfRenderer renderer = mock(CertificatePdfRenderer.class);
    private final CertificateDigest digest = mock(CertificateDigest.class);
    private final CertificateVerificationUrl url = new CertificateVerificationUrl("https://app.example");

    private String capture(Runnable action) {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            action.run();
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
        } finally {
            root.detachAppender(appender);
        }
    }

    @Test
    void graduationProviderRegenerationLogsTheIdAndNeverTheCode() {
        GraduationCertificateRepository repository = mock(GraduationCertificateRepository.class);
        GraduationCertificate certificate = GraduationCertificate.builder().id(42L).code(CODE).kind(
                com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind.PROGRAM)
                .snapshot(snapshot).pdfPath("x.pdf").pdfSha256("0".repeat(64)).issuedAt(LocalDateTime.now()).build();
        when(repository.findById(42L)).thenReturn(Optional.of(certificate));
        when(fileStore.load("x.pdf")).thenReturn(Optional.empty());
        when(renderer.render(any(), any(), any(), any())).thenReturn(new byte[] {1});
        when(digest.sha256(any())).thenReturn("h");
        GraduationCertificatePdfProvider provider = new GraduationCertificatePdfProvider(repository, fileStore, renderer,
                mock(CertificateTemplateResolver.class), url, digest);

        String logs = capture(() -> provider.pdfOf(42L));

        assertThat(logs).contains("42").doesNotContain(CODE);
    }

    @Test
    void regularProviderRegenerationLogsTheIdAndNeverTheCode() {
        RegularStudentCertificateRepository repository = mock(RegularStudentCertificateRepository.class);
        RegularStudentCertificate certificate = RegularStudentCertificate.builder().id(43L).code(CODE)
                .status(RegularCertificateStatus.VALID).snapshot(snapshot).pdfPath("x.pdf").pdfSha256("0".repeat(64))
                .expiresAt(LocalDateTime.of(2026, 12, 5, 10, 0)).build();
        when(repository.findById(43L)).thenReturn(Optional.of(certificate));
        when(fileStore.load("x.pdf")).thenReturn(Optional.empty());
        when(renderer.render(any(), any(), any(), any())).thenReturn(new byte[] {1});
        when(digest.sha256(any())).thenReturn("h");
        RegularCertificatePdfProvider provider = new RegularCertificatePdfProvider(repository, fileStore, renderer,
                mock(RegularCertificateTemplate.class), url, digest);

        String logs = capture(() -> provider.pdfOf(43L));

        assertThat(logs).contains("43").doesNotContain(CODE);
    }
}

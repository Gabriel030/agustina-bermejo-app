package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.ConfirmProspectRequest;
import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.identity.api.ConfirmedProspect;
import com.red_escuela.backend.identity.api.EmailVerificationSender;
import com.red_escuela.backend.identity.api.NewProspect;
import com.red_escuela.backend.identity.api.PasswordResetCodeIssuer;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.api.StudentOnboarding;
import com.red_escuela.backend.identity.api.StudentOnboardingSummary;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.TransactionBoundFileCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProspectServiceTest {

    private final StudentOnboarding studentOnboarding = mock(StudentOnboarding.class);
    private final PasswordResetCodeIssuer passwordResetCodeIssuer = mock(PasswordResetCodeIssuer.class);
    private final EmailVerificationSender emailVerificationSender = mock(EmailVerificationSender.class);
    private final ProspectService service = new ProspectService(mock(StudentFinder.class), studentOnboarding,
            mock(StudentDocumentRepository.class), mock(FileStorageService.class),
            mock(TransactionBoundFileCleanup.class), passwordResetCodeIssuer, emailVerificationSender,
            Clock.systemUTC());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createProspectSendsVerificationThroughTheIdentityApi() {
        StudentOnboardingSummary summary = mock(StudentOnboardingSummary.class);
        when(summary.studentId()).thenReturn(5L);
        when(studentOnboarding.registerProspect(any(NewProspect.class))).thenReturn(summary);
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anon", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        service.createProspect(new CreateProspectRequest("Ana", "Perez", "ana@test.com", null, 1L));

        verify(emailVerificationSender).sendVerificationToStudent(5L);
    }

    @Test
    void createProspectDoesNotSendVerificationWhenAuthenticated() {
        StudentOnboardingSummary summary = mock(StudentOnboardingSummary.class);
        when(studentOnboarding.registerProspect(any(NewProspect.class))).thenReturn(summary);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("staff", "x"));

        service.createProspect(new CreateProspectRequest("Ana", "Perez", "ana@test.com", null, 1L));

        verify(emailVerificationSender, never()).sendVerificationToStudent(any());
    }

    @Test
    void confirmRequestsPasswordResetCodeThroughTheIdentityApi() {
        StudentOnboardingSummary summary = mock(StudentOnboardingSummary.class);
        when(summary.email()).thenReturn("ana@test.com");
        when(studentOnboarding.confirmProspect(any(Long.class), any(ConfirmedProspect.class))).thenReturn(summary);

        service.confirm(5L, new ConfirmProspectRequest("123", null, "Calle", null, null, "Ciudad", null, null, null));

        verify(passwordResetCodeIssuer).issuePasswordResetCode("ana@test.com");
    }
}

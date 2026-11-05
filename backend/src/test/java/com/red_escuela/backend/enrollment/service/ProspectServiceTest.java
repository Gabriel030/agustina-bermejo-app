package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.ConfirmProspectRequest;
import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.enrollment.dto.ProspectListItemResponse;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProspectServiceTest {

    private final StudentOnboarding studentOnboarding = mock(StudentOnboarding.class);
    private final PasswordResetCodeIssuer passwordResetCodeIssuer = mock(PasswordResetCodeIssuer.class);
    private final EmailVerificationSender emailVerificationSender = mock(EmailVerificationSender.class);
    private final StudentFinder studentFinder = mock(StudentFinder.class);
    private final StudentDocumentRepository studentDocumentRepository = mock(StudentDocumentRepository.class);
    private final CurrentActorFinder currentActorFinder = mock(CurrentActorFinder.class);
    private final ProspectService service = new ProspectService(studentFinder, studentOnboarding,
            studentDocumentRepository, mock(FileStorageService.class),
            mock(TransactionBoundFileCleanup.class), passwordResetCodeIssuer, emailVerificationSender,
            currentActorFinder, Clock.systemUTC());

    @Test
    void createProspectPublicSendsVerificationAndLeavesActorNull() {
        StudentOnboardingSummary summary = mock(StudentOnboardingSummary.class);
        when(summary.studentId()).thenReturn(5L);
        when(studentOnboarding.registerProspect(any(NewProspect.class))).thenReturn(summary);

        service.createProspect(new CreateProspectRequest("Ana", "Perez", "ana@test.com", null, 1L));

        ArgumentCaptor<NewProspect> captor = ArgumentCaptor.forClass(NewProspect.class);
        verify(studentOnboarding).registerProspect(captor.capture());
        assertThat(captor.getValue().registeredByPersonId()).isNull();
        verify(emailVerificationSender).sendVerificationToStudent(5L);
        verifyNoInteractions(currentActorFinder);
    }

    @Test
    void createProspectByStaffStoresActorAndDoesNotSendVerification() {
        StudentOnboardingSummary summary = mock(StudentOnboardingSummary.class);
        when(studentOnboarding.registerProspect(any(NewProspect.class))).thenReturn(summary);
        when(currentActorFinder.currentPersonId()).thenReturn(77L);

        StudentOnboardingSummary created = service.createProspectByStaff(
                new CreateProspectRequest("Ana", "Perez", "ana@test.com", "11", 1L));

        ArgumentCaptor<NewProspect> captor = ArgumentCaptor.forClass(NewProspect.class);
        verify(studentOnboarding).registerProspect(captor.capture());
        assertThat(captor.getValue().registeredByPersonId()).isEqualTo(77L);
        assertThat(captor.getValue().institutionId()).isEqualTo(1L);
        assertThat(created).isSameAs(summary);
        verify(emailVerificationSender, never()).sendVerificationToStudent(any());
    }

    @Test
    void listProspectsReturnsMissingDocuments() {
        StudentOnboardingSummary withFront = prospect(1L);
        StudentOnboardingSummary withNothing = prospect(2L);
        when(studentFinder.listProspects()).thenReturn(List.of(withFront, withNothing));
        Student student = new Student();
        student.setId(1L);
        StudentDocument front = new StudentDocument();
        front.setStudent(student);
        front.setType(DocumentType.DNI_FRENTE);
        when(studentDocumentRepository.findByStudentIdIn(List.of(1L, 2L))).thenReturn(List.of(front));

        List<ProspectListItemResponse> items = service.listProspects();

        assertThat(items).extracting(ProspectListItemResponse::id).containsExactly(1L, 2L);
        assertThat(items.get(0).missingDocuments())
                .containsExactly(DocumentType.DNI_DORSO, DocumentType.ANALITICO_SECUNDARIO);
        assertThat(items.get(1).missingDocuments()).containsExactly(DocumentType.values());
    }

    private static StudentOnboardingSummary prospect(Long id) {
        return new StudentOnboardingSummary(id, id + 100, "Ana", "Paz", null, null, "a" + id + "@x.com",
                StudentOnboardingStatus.PROSPECTO, "ACTIVE", false, LocalDateTime.of(2026, 11, 5, 10, 0), null, null);
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

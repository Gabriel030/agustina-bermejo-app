package com.red_escuela.backend.enrollment.service;
import java.time.Clock;

import com.red_escuela.backend.enrollment.dto.ConfirmProspectRequest;
import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.enrollment.dto.SaveProspectDraftRequest;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.dto.ProspectListItemResponse;
import com.red_escuela.backend.identity.api.ConfirmedProspect;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.EmailVerificationSender;
import com.red_escuela.backend.identity.api.NewProspect;
import com.red_escuela.backend.identity.api.PasswordResetCodeIssuer;
import com.red_escuela.backend.identity.api.ProspectDraft;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.api.StudentOnboarding;
import com.red_escuela.backend.identity.api.StudentOnboardingSummary;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.TransactionBoundFileCleanup;

@Service
public class ProspectService {
    private final StudentFinder studentFinder;
    private final StudentOnboarding studentOnboarding;
    private final StudentDocumentRepository studentDocumentRepository;
    private final FileStorageService fileStorageService;
    private final TransactionBoundFileCleanup transactionBoundFileCleanup;
    private final PasswordResetCodeIssuer passwordResetCodeIssuer;
    private final EmailVerificationSender emailVerificationSender;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public ProspectService(StudentFinder studentFinder, StudentOnboarding studentOnboarding,
            StudentDocumentRepository studentDocumentRepository,
            FileStorageService fileStorageService, TransactionBoundFileCleanup transactionBoundFileCleanup,
            PasswordResetCodeIssuer passwordResetCodeIssuer,
            EmailVerificationSender emailVerificationSender,
            CurrentActorFinder currentActorFinder,
            Clock clock) {
        this.studentFinder = studentFinder;
        this.studentOnboarding = studentOnboarding;
        this.studentDocumentRepository = studentDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.transactionBoundFileCleanup = transactionBoundFileCleanup;
        this.passwordResetCodeIssuer = passwordResetCodeIssuer;
        this.emailVerificationSender = emailVerificationSender;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public StudentOnboardingSummary createProspect(CreateProspectRequest request) {
        StudentOnboardingSummary prospect = studentOnboarding.registerProspect(new NewProspect(
                request.nombre(), request.apellido(), request.email(), request.telefono(), request.institutionId(), null));
        emailVerificationSender.sendVerificationToStudent(prospect.studentId());
        return prospect;
    }

    @Transactional
    public StudentOnboardingSummary createProspectByStaff(CreateProspectRequest request) {
        return studentOnboarding.registerProspect(new NewProspect(
                request.nombre(), request.apellido(), request.email(), request.telefono(), request.institutionId(),
                currentActorFinder.currentPersonId()));
    }

    @Transactional(readOnly = true)
    public List<ProspectListItemResponse> listProspects() {
        List<StudentOnboardingSummary> prospects = studentFinder.listProspects();
        Map<Long, Set<DocumentType>> uploadedByStudent = studentDocumentRepository
                .findByStudentIdIn(prospects.stream().map(StudentOnboardingSummary::studentId).toList())
                .stream()
                .collect(Collectors.groupingBy(document -> document.getStudent().getId(),
                        Collectors.mapping(StudentDocument::getType, Collectors.toCollection(() -> EnumSet.noneOf(DocumentType.class)))));
        return prospects.stream()
                .map(prospect -> ProspectListItemResponse.of(prospect,
                        missingDocuments(uploadedByStudent.getOrDefault(prospect.studentId(), Set.of()))))
                .toList();
    }

    private static List<DocumentType> missingDocuments(Set<DocumentType> uploaded) {
        return Arrays.stream(DocumentType.values()).filter(type -> !uploaded.contains(type)).toList();
    }

    public void saveDraft(Long studentId, SaveProspectDraftRequest request) {
        studentOnboarding.saveProspectDraft(studentId, new ProspectDraft(
                request.dni(), request.documentType(), request.street(), request.number(), request.floor(),
                request.city(), request.state(), request.postalCode(), request.country()));
    }

    public void sendVerification(Long studentId) {
        findStudentOrThrow(studentId);
        emailVerificationSender.sendVerificationToStudent(studentId);
    }

    @Transactional
    public StudentDocument addDocument(Long studentId, DocumentType type, MultipartFile file) {
        Student student = findStudentOrThrow(studentId);

        studentDocumentRepository.findByStudentIdAndType(studentId, type).ifPresent(existing -> {
            transactionBoundFileCleanup.deleteAfterCommit(existing.getFilePath());
            studentDocumentRepository.delete(existing);
        });

        String relativePath = fileStorageService.store(studentId, type.name(), file);
        transactionBoundFileCleanup.deleteIfRolledBack(relativePath);

        StudentDocument document = new StudentDocument();
        document.setStudent(student);
        document.setType(type);
        document.setFilePath(relativePath);
        document.setUploadedAt(LocalDateTime.now(clock));

        return studentDocumentRepository.save(document);
    }

    public List<StudentDocument> listDocuments(Long studentId) {
        findStudentOrThrow(studentId);
        return studentDocumentRepository.findByStudentId(studentId);
    }

    @Transactional
    public StudentOnboardingSummary confirm(Long studentId, ConfirmProspectRequest request) {
        StudentOnboardingSummary confirmed = studentOnboarding.confirmProspect(studentId, new ConfirmedProspect(
                request.dni(), request.documentType(), request.street(), request.number(), request.floor(),
                request.city(), request.state(), request.postalCode(), request.country()));

        passwordResetCodeIssuer.issuePasswordResetCode(confirmed.email());

        return confirmed;
    }

    private Student findStudentOrThrow(Long studentId) {
        return studentFinder.requireById(studentId);
    }
}

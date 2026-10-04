package com.red_escuela.backend.enrollment.service;
import com.red_escuela.backend.identity.usecase.auth.RequestPasswordResetUseCase;
import com.red_escuela.backend.identity.usecase.emailverification.SendEmailVerificationUseCase;
import com.red_escuela.backend.identity.service.PersonFactory;

import com.red_escuela.backend.enrollment.dto.ConfirmProspectRequest;
import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.enrollment.dto.SaveProspectDraftRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.identity.exception.InstitutionNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProspectAlreadyConfirmedException;
import com.red_escuela.backend.identity.exception.RoleNotFoundException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.TransactionBoundFileCleanup;

@Service
public class ProspectService {
    private static final String ROLE_ALUMNO = "ALUMNO";

    private final StudentRepository studentRepository;
    private final StudentDocumentRepository studentDocumentRepository;
    private final InstitutionRepository institutionRepository;
    private final RoleRepository roleRepository;
    private final PersonFactory personFactory;
    private final FileStorageService fileStorageService;
    private final TransactionBoundFileCleanup transactionBoundFileCleanup;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final SendEmailVerificationUseCase sendEmailVerificationUseCase;

    public ProspectService(
            StudentRepository studentRepository,
            StudentDocumentRepository studentDocumentRepository,
            InstitutionRepository institutionRepository,
            RoleRepository roleRepository,
            PersonFactory personFactory,
            FileStorageService fileStorageService, TransactionBoundFileCleanup transactionBoundFileCleanup,
            RequestPasswordResetUseCase requestPasswordResetUseCase,
            SendEmailVerificationUseCase sendEmailVerificationUseCase) {
        this.studentRepository = studentRepository;
        this.studentDocumentRepository = studentDocumentRepository;
        this.institutionRepository = institutionRepository;
        this.roleRepository = roleRepository;
        this.personFactory = personFactory;
        this.fileStorageService = fileStorageService;
        this.transactionBoundFileCleanup = transactionBoundFileCleanup;
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.sendEmailVerificationUseCase = sendEmailVerificationUseCase;
    }

    @Transactional
    public Student createProspect(CreateProspectRequest request) {
        Institution institution = institutionRepository.findById(request.institutionId())
                .orElseThrow(() -> new InstitutionNotFoundException(request.institutionId()));

        Person person = personFactory.createBasePerson(request.nombre(), request.apellido(), request.email());
        person.setPhoneNumber(request.telefono());

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.PROSPECTO);

        Student saved = studentRepository.save(student);

        boolean isAnonymous = SecurityContextHolder.getContext().getAuthentication()
                instanceof AnonymousAuthenticationToken;
        if (isAnonymous) {
            sendEmailVerificationUseCase.sendVerification(saved.getId());
        }

        return saved;
    }

    @Transactional
    public void saveDraft(Long studentId, SaveProspectDraftRequest request) {
        Student student = findStudentOrThrow(studentId);
        Person person = student.getPerson();

        if (request.dni() != null) {
            person.setDni(request.dni());
        }

        Address address = person.getAddress();
        if (request.street() != null) address.setStreet(request.street());
        if (request.number() != null) address.setNumber(request.number());
        if (request.floor() != null) address.setFloor(request.floor());
        if (request.city() != null) address.setCity(request.city());
        if (request.state() != null) address.setState(request.state());
        if (request.postalCode() != null) address.setPostalCode(request.postalCode());
        if (request.country() != null) address.setCountry(request.country());

        try {
            studentRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException e) {
            throw new DniAlreadyExistsException(request.dni());
        }
    }

    public void sendVerification(Long studentId) {
        findStudentOrThrow(studentId);
        sendEmailVerificationUseCase.sendVerification(studentId);
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
        document.setUploadedAt(LocalDateTime.now());

        return studentDocumentRepository.save(document);
    }

    public List<StudentDocument> listDocuments(Long studentId) {
        findStudentOrThrow(studentId);
        return studentDocumentRepository.findByStudentId(studentId);
    }

    @Transactional
    public Student confirm(Long studentId, ConfirmProspectRequest request) {
        Student student = findStudentOrThrow(studentId);
        if (student.getOnboardingStatus() != StudentOnboardingStatus.PROSPECTO) {
            throw new ProspectAlreadyConfirmedException(studentId);
        }

        Person person = student.getPerson();
        person.setDni(request.dni());
        person.setUsername(request.dni());

        var address = person.getAddress();
        address.setStreet(request.street());
        address.setNumber(request.number());
        address.setFloor(request.floor());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country());

        Role alumno = roleRepository.findByName(ROLE_ALUMNO)
                .orElseThrow(() -> new RoleNotFoundException(ROLE_ALUMNO));
        person.getRoles().add(alumno);

        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);

        try {
            studentRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException e) {
            throw new DniAlreadyExistsException(request.dni());
        }

        requestPasswordResetUseCase.requestReset(person.emailValue());

        return student;
    }

    private Student findStudentOrThrow(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
    }
}

package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.ConfirmProspectRequest;
import com.red_escuela.backend.dto.CreateProspectRequest;
import com.red_escuela.backend.entity.DocumentType;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentDocument;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.exception.DniAlreadyExistsException;
import com.red_escuela.backend.exception.InstitutionNotFoundException;
import com.red_escuela.backend.exception.ProspectAlreadyConfirmedException;
import com.red_escuela.backend.exception.RoleNotFoundException;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.repository.RoleRepository;
import com.red_escuela.backend.repository.StudentDocumentRepository;
import com.red_escuela.backend.repository.StudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Orquesta el proceso de preinscripción pública → prospecto → verificación de
 * documentación → alta definitiva (spec funcional, sección 5, pasos 1-4).
 */
@Service
public class ProspectService {

    private static final String ROLE_ALUMNO = "ALUMNO";

    private final StudentRepository studentRepository;
    private final StudentDocumentRepository studentDocumentRepository;
    private final InstitutionRepository institutionRepository;
    private final RoleRepository roleRepository;
    private final PersonFactory personFactory;
    private final FileStorageService fileStorageService;
    private final AuthService authService;

    public ProspectService(
            StudentRepository studentRepository,
            StudentDocumentRepository studentDocumentRepository,
            InstitutionRepository institutionRepository,
            RoleRepository roleRepository,
            PersonFactory personFactory,
            FileStorageService fileStorageService,
            AuthService authService) {
        this.studentRepository = studentRepository;
        this.studentDocumentRepository = studentDocumentRepository;
        this.institutionRepository = institutionRepository;
        this.roleRepository = roleRepository;
        this.personFactory = personFactory;
        this.fileStorageService = fileStorageService;
        this.authService = authService;
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

        return studentRepository.save(student);
    }

    @Transactional
    public StudentDocument addDocument(Long studentId, DocumentType type, MultipartFile file) {
        Student student = findStudentOrThrow(studentId);

        // Un tipo de documento tiene un único registro vigente: el nuevo reemplaza al
        // anterior, tanto la fila como el archivo físico en disco.
        studentDocumentRepository.findByStudentIdAndType(studentId, type).ifPresent(existing -> {
            fileStorageService.delete(existing.getFilePath());
            studentDocumentRepository.delete(existing);
        });

        String relativePath = fileStorageService.store(studentId, type.name(), file);

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
    public Student confirmOnboarding(Long studentId, ConfirmProspectRequest request) {
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

        authService.requestPasswordReset(person.getEmail());

        return student;
    }

    private Student findStudentOrThrow(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
    }
}

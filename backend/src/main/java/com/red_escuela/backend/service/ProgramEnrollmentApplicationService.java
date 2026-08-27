package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.entity.DocumentType;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentDocument;
import com.red_escuela.backend.exception.AlreadyHasPendingApplicationException;
import com.red_escuela.backend.exception.ProgramEnrollmentApplicationNotFoundException;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.repository.StudentDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Solicitud de alta en una carrera para el registro independiente (Épica 7).
 * Al aprobar, delega la creación del ProgramEnrollment real a
 * {@link ProgramEnrollmentService#matricular} — misma lógica de ventana de
 * admisión y duplicados que ya usa el alta directa por staff, no se duplica acá.
 */
@Service
public class ProgramEnrollmentApplicationService {

    private final ProgramEnrollmentApplicationRepository applicationRepository;
    private final ProgramRepository programRepository;
    private final StudentDocumentRepository studentDocumentRepository;
    private final FileStorageService fileStorageService;
    private final ProgramEnrollmentService programEnrollmentService;
    private final PersonRoleGranter personRoleGranter;

    public ProgramEnrollmentApplicationService(
            ProgramEnrollmentApplicationRepository applicationRepository,
            ProgramRepository programRepository,
            StudentDocumentRepository studentDocumentRepository,
            FileStorageService fileStorageService,
            ProgramEnrollmentService programEnrollmentService,
            PersonRoleGranter personRoleGranter) {
        this.applicationRepository = applicationRepository;
        this.programRepository = programRepository;
        this.studentDocumentRepository = studentDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.programEnrollmentService = programEnrollmentService;
        this.personRoleGranter = personRoleGranter;
    }

    @Transactional
    public ProgramEnrollmentApplication solicitar(Student student, Long programId) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ProgramNotFoundException(programId));

        applicationRepository
                .findByStudentIdAndProgramIdAndStatus(student.getId(), programId, ProgramEnrollmentApplicationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new AlreadyHasPendingApplicationException(student.getId(), programId);
                });

        ProgramEnrollmentApplication application = new ProgramEnrollmentApplication();
        application.setStudent(student);
        application.setProgram(program);
        application.setStatus(ProgramEnrollmentApplicationStatus.PENDING);
        application.setRequestedAt(LocalDateTime.now());
        return applicationRepository.save(application);
    }

    public List<ProgramEnrollmentApplication> listarPropias(Long studentId) {
        return applicationRepository.findByStudentIdOrderByRequestedAtDesc(studentId);
    }

    public List<ProgramEnrollmentApplication> listarPendientes() {
        return applicationRepository.findByStatusOrderByRequestedAtAsc(ProgramEnrollmentApplicationStatus.PENDING);
    }

    public ProgramEnrollmentApplication verDetalle(Long id) {
        return findOrThrow(id);
    }

    // Duplica el patrón de reemplazo de ProspectService.agregarDocumento (un tipo de
    // documento tiene un único registro vigente) — no se extrajo a un servicio
    // compartido porque hoy son los dos únicos casos de uso y la lógica es de 10
    // líneas; se evalúa unificar si aparece un tercer caso.
    @Transactional
    public StudentDocument agregarDocumento(Long applicationId, DocumentType type, MultipartFile file) {
        ProgramEnrollmentApplication application = findOrThrow(applicationId);
        Long studentId = application.getStudent().getId();

        studentDocumentRepository.findByStudentIdAndType(studentId, type).ifPresent(existing -> {
            fileStorageService.delete(existing.getFilePath());
            studentDocumentRepository.delete(existing);
        });

        String relativePath = fileStorageService.store(studentId, type, file);

        StudentDocument document = new StudentDocument();
        document.setStudent(application.getStudent());
        document.setType(type);
        document.setFilePath(relativePath);
        document.setUploadedAt(LocalDateTime.now());
        return studentDocumentRepository.save(document);
    }

    @Transactional
    public ProgramEnrollmentApplication aprobar(Long id, Person decidedBy) {
        ProgramEnrollmentApplication application = findOrThrow(id);
        application.aprobar(decidedBy);

        Student student = application.getStudent();
        programEnrollmentService.matricular(student.getId(), new CreateProgramEnrollmentRequest(application.getProgram().getId()));
        personRoleGranter.otorgarAlumnoSiNoLoTiene(student.getPerson());

        return application;
    }

    @Transactional
    public ProgramEnrollmentApplication rechazar(Long id, Person decidedBy, String reason) {
        ProgramEnrollmentApplication application = findOrThrow(id);
        application.rechazar(decidedBy, reason);
        return application;
    }

    private ProgramEnrollmentApplication findOrThrow(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ProgramEnrollmentApplicationNotFoundException(id));
    }
}

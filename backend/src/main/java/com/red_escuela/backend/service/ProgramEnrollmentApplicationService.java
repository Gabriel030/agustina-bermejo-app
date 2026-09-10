package com.red_escuela.backend.service;
import com.red_escuela.backend.identity.service.PersonRoleGranter;

import com.red_escuela.backend.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.entity.DocumentType;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.entity.StudentDocument;
import com.red_escuela.backend.exception.AlreadyHasPendingApplicationException;
import com.red_escuela.backend.exception.ProgramEnrollmentApplicationNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.repository.StudentDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import com.red_escuela.backend.billing.service.InvoiceFactory;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import com.red_escuela.backend.billing.service.ProgramCohortService;

/**
 * Solicitud de alta en una carrera para el registro independiente (Épica 7).
 * Al aprobar, delega la creación del ProgramEnrollment real a
 * {@link ProgramEnrollmentService#matricular} — misma lógica de duplicados que ya
 * usa el alta directa por staff, no se duplica acá. La ventana de admisión sí se
 * valida en los dos puntos de entrada por separado ({@link #solicitarConPago} y
 * {@code matricular}), reusando {@link ProgramEnrollmentService#validarVentanaDeAdmision}
 * — hace falta en los dos: sin la validación acá, se le pediría comprobante (y se
 * le cobraría) a alguien fuera de la ventana de admisión, mucho antes de que
 * {@code aprobar()} llegue a rechazarlo.
 * Desde la Fase 3, el pago de la matrícula es un paso bloqueante de la propia
 * solicitud (ver {@link #solicitarConPago}) — sin comprobante no hay solicitud.
 */
@Service
public class ProgramEnrollmentApplicationService {

    private final ProgramEnrollmentApplicationRepository applicationRepository;
    private final ProgramRepository programRepository;
    private final StudentDocumentRepository studentDocumentRepository;
    private final FileStorageService fileStorageService;
    private final ProgramEnrollmentService programEnrollmentService;
    private final PersonRoleGranter personRoleGranter;
    private final InvoiceFactory invoiceFactory;
    private final UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;
    private final ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;
    private final ProgramCohortService programCohortService;

    public ProgramEnrollmentApplicationService(
            ProgramEnrollmentApplicationRepository applicationRepository,
            ProgramRepository programRepository,
            StudentDocumentRepository studentDocumentRepository,
            FileStorageService fileStorageService,
            ProgramEnrollmentService programEnrollmentService,
            PersonRoleGranter personRoleGranter,
            InvoiceFactory invoiceFactory,
            UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase,
            ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase,
            ProgramCohortService programCohortService) {
        this.applicationRepository = applicationRepository;
        this.programRepository = programRepository;
        this.studentDocumentRepository = studentDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.programEnrollmentService = programEnrollmentService;
        this.personRoleGranter = personRoleGranter;
        this.invoiceFactory = invoiceFactory;
        this.uploadPaymentReceiptUseCase = uploadPaymentReceiptUseCase;
        this.approvePaymentReceiptUseCase = approvePaymentReceiptUseCase;
        this.programCohortService = programCohortService;
    }

    // Matrícula vence hoy: se paga en el momento, junto con la solicitud (a
    // diferencia de una cuota regular, que vence a futuro) — ver spec técnica,
    // "el Invoice + PaymentReceipt se crean en la MISMA request que solicitar()".
    @Transactional
    public ProgramEnrollmentApplication solicitarConPago(Student student, Long programId, Long cohortId, MultipartFile comprobante) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ProgramNotFoundException(programId));
        ProgramCohort cohort = programCohortService.findOrThrow(cohortId);
        programEnrollmentService.validarVentanaDeAdmision(cohort);

        applicationRepository
                .findByStudentIdAndProgramIdAndStatus(student.getId(), programId, ProgramEnrollmentApplicationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new AlreadyHasPendingApplicationException(student.getId(), programId);
                });

        Invoice invoice = invoiceFactory.crearAdHocParaCarrera(program, FeeType.ENROLLMENT_FEE, YearMonth.now(), LocalDate.now());
        PaymentReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), comprobante);

        ProgramEnrollmentApplication application = new ProgramEnrollmentApplication();
        application.setStudent(student);
        application.setProgram(program);
        application.setProgramCohort(cohort);
        application.setStatus(ProgramEnrollmentApplicationStatus.PENDING);
        application.setRequestedAt(LocalDateTime.now());
        application.setPaymentReceipt(receipt);
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

        String relativePath = fileStorageService.store(studentId, type.name(), file);

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
        Long cohortId = application.getProgramCohort() == null ? null : application.getProgramCohort().getId();
        var enrollment = programEnrollmentService.matricular(student.getId(),
                new CreateProgramEnrollmentRequest(application.getProgram().getId(), cohortId));
        personRoleGranter.grantStudentRoleIfMissing(student.getPerson());

        // Recién ahora existe el ProgramEnrollment — la factura de matrícula (generada
        // al solicitar, sin vínculo posible en ese momento) se completa retroactivamente,
        // mismo criterio que ApprovePaymentReceiptService.matricularCurso para el caso de curso.
        if (application.getPaymentReceipt() != null) {
            application.getPaymentReceipt().getInvoice().setProgramEnrollment(enrollment);
        }

        // Documentación + comprobante se deciden juntos, una sola decisión (ver spec
        // funcional) — aprobar la solicitud aprueba también el comprobante en la MISMA
        // transacción, lo que crea el Payment real (ApprovePaymentReceiptService.approve).
        if (application.getPaymentReceipt() != null) {
            approvePaymentReceiptUseCase.approve(application.getPaymentReceipt().getId(), decidedBy);
        }

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

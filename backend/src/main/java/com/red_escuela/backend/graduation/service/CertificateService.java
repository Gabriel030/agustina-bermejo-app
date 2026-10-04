package com.red_escuela.backend.graduation.service;
import java.time.Clock;
import com.red_escuela.backend.identity.service.CurrentPersonService;

import com.red_escuela.backend.graduation.entity.CertificateIssuance;
import com.red_escuela.backend.graduation.entity.CertificateType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.graduation.exception.CertificateNotEligibleException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.graduation.repository.CertificateIssuanceRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.billing.service.EnrollmentFeeRequirementService;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;

@Service
public class CertificateService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final ProgramEnrollmentService programEnrollmentService;
    private final InvoiceQueryService invoiceQueryService;
    private final CertificateIssuanceRepository certificateIssuanceRepository;
    private final CurrentPersonService currentPersonService;
    private final EnrollmentFeeRequirementService enrollmentFeeRequirementService;
    private final Clock clock;

    public CertificateService(ProgramEnrollmentRepository programEnrollmentRepository, ProgramEnrollmentService programEnrollmentService,
            InvoiceQueryService invoiceQueryService, CertificateIssuanceRepository certificateIssuanceRepository,
            CurrentPersonService currentPersonService,
            EnrollmentFeeRequirementService enrollmentFeeRequirementService,
            Clock clock) {
        this.enrollmentFeeRequirementService = enrollmentFeeRequirementService;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.programEnrollmentService = programEnrollmentService;
        this.invoiceQueryService = invoiceQueryService;
        this.certificateIssuanceRepository = certificateIssuanceRepository;
        this.currentPersonService = currentPersonService;
        this.clock = clock;
    }

    @Transactional
    public byte[] issueRegularCertificate(Long programEnrollmentId) {
        ProgramEnrollment enrollment = findOrThrow(programEnrollmentId);
        enrollmentFeeRequirementService.requireForCurrentPeriod(enrollment, EnrollmentFeeRequirementService.Scope.CERTIFICATE);
        if (enrollment.getStatus() != ProgramEnrollmentStatus.ENROLLED) {
            throw new CertificateNotEligibleException("la matriculación no está ENROLLED");
        }
        Student student = enrollment.getStudent();
        boolean cursandoAlgo = programEnrollmentService.hasActiveEnrollment(programEnrollmentId);
        if (!cursandoAlgo) {
            throw new CertificateNotEligibleException("el alumno no tiene ninguna cursada activa en este momento");
        }
        if (invoiceQueryService.hasOverdueDebt(student.getId())) {
            throw new CertificateNotEligibleException("el alumno no está al día con sus pagos");
        }

        Person person = student.getPerson();
        String texto = "Se deja constancia de que " + person.getName() + " " + person.getLastname()
                + " (DNI " + person.getDni() + ") es alumno/a regular de la carrera "
                + enrollment.getProgram().getName() + ".";
        byte[] pdf = generatePdf("Constancia de alumno regular", texto);
        recordIssuance(enrollment, CertificateType.REGULAR_STUDENT);
        return pdf;
    }

    @Transactional
    public byte[] issueApprovedSubjectsCertificate(Long programEnrollmentId) {
        ProgramEnrollment enrollment = findOrThrow(programEnrollmentId);
        enrollmentFeeRequirementService.requireForCurrentPeriod(enrollment, EnrollmentFeeRequirementService.Scope.CERTIFICATE);
        Person person = enrollment.getStudent().getPerson();
        List<ProgramSubject> aprobadas = programEnrollmentService.listApprovedSubjects(programEnrollmentId);

        StringBuilder texto = new StringBuilder(
                "Se deja constancia de que " + person.getName() + " " + person.getLastname()
                        + " (DNI " + person.getDni() + "), alumno/a de la carrera "
                        + enrollment.getProgram().getName() + ", tiene aprobadas las siguientes materias:");
        if (aprobadas.isEmpty()) {
            texto.append(" ninguna materia aprobada todavía.");
        } else {
            for (ProgramSubject entrada : aprobadas) {
                texto.append("\n- ").append(entrada.getSubject().getName());
            }
        }
        byte[] pdf = generatePdf("Constancia de materias aprobadas", texto.toString());
        recordIssuance(enrollment, CertificateType.APPROVED_SUBJECTS);
        return pdf;
    }

    private void recordIssuance(ProgramEnrollment enrollment, CertificateType type) {
        CertificateIssuance issuance = new CertificateIssuance();
        issuance.setType(type);
        issuance.setProgramEnrollment(enrollment);
        issuance.setIssuedAt(LocalDateTime.now(clock));
        issuance.setRequestedBy(currentPersonService.resolveCurrentPerson());
        certificateIssuanceRepository.save(issuance);
    }

    private byte[] generatePdf(String titulo, String cuerpo) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDFont fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margen = 60;
                float y = page.getMediaBox().getHeight() - margen;

                content.beginText();
                content.setFont(fontBold, 16);
                content.newLineAtOffset(margen, y);
                content.showText(titulo);
                content.endText();
                y -= 40;

                content.beginText();
                content.setFont(fontRegular, 11);
                content.newLineAtOffset(margen, y);
                for (String linea : cuerpo.split("\n")) {
                    content.showText(linea);
                    content.newLineAtOffset(0, -16);
                }
                content.endText();
                y -= 16 * cuerpo.split("\n").length + 40;

                content.beginText();
                content.setFont(fontRegular, 10);
                content.newLineAtOffset(margen, Math.max(y, margen));
                content.showText("Fecha de emisión: " + LocalDateTime.now(clock).format(FECHA));
                content.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la constancia", e);
        }
    }

    private ProgramEnrollment findOrThrow(Long id) {
        return programEnrollmentRepository.findById(id)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(id));
    }
}

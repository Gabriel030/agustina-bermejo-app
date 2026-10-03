package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.entity.ExceptionType;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

@Component
@Profile("local & demo")
public class DemoApprovalSeeder {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final GraduationRequestRepository graduationRequestRepository;
    private final NotificationRepository notificationRepository;

    public DemoApprovalSeeder(
            DirectorApprovalRequestRepository directorApprovalRequestRepository,
            GraduationRequestRepository graduationRequestRepository,
            NotificationRepository notificationRepository) {
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.graduationRequestRepository = graduationRequestRepository;
        this.notificationRepository = notificationRepository;
    }

    public void seed(DemoDataset dataset) {
        createDirectorApprovalRequests(dataset);
        createGraduationRequests(dataset);
        createNotifications(dataset);
    }

    private record ExceptionTarget(ProgramEnrollment enrollment, String subjectName, String detail) {}

    private void createDirectorApprovalRequests(DemoDataset dataset) {
        List<ExceptionTarget> waivers = new ArrayList<>();
        List<ExceptionTarget> lateExams = new ArrayList<>();
        for (ProgramEnrollment enrollment : dataset.getProgramEnrollments()) {
            if (enrollment.getStatus() != ProgramEnrollmentStatus.ENROLLED) {
                continue;
            }
            List<SubjectSectionEnrollment> history = dataset.getSectionEnrollments().stream()
                    .filter(e -> e.getProgramEnrollment().getId().equals(enrollment.getId()))
                    .toList();
            Set<String> approved = new HashSet<>();
            Set<String> taken = new HashSet<>();
            for (SubjectSectionEnrollment e : history) {
                String name = e.getSubjectSection().getSubject().getName();
                taken.add(name);
                DemoOutcome outcome = dataset.getOutcomes().get(e.getId());
                if (outcome != null && outcome.approves()) {
                    approved.add(name);
                }
                if (outcome == DemoOutcome.REGULAR_PENDING) {
                    lateExams.add(new ExceptionTarget(enrollment, name, null));
                }
            }
            DemoCatalog.PROGRAMS.stream()
                    .filter(spec -> spec.name().equals(enrollment.getProgram().getName()))
                    .flatMap(spec -> spec.subjectsByYear().stream())
                    .flatMap(List::stream)
                    .filter(name -> !taken.contains(name))
                    .forEach(name -> {
                        String prerequisite = DemoCatalog.prerequisiteOf(name);
                        if (prerequisite != null && taken.contains(prerequisite) && !approved.contains(prerequisite)) {
                            waivers.add(new ExceptionTarget(enrollment, name, prerequisite));
                        }
                    });
        }
        if (!waivers.isEmpty()) {
            ExceptionTarget t = waivers.get(0);
            saveRequest(dataset, t.enrollment(), ExceptionType.PREREQUISITE_WAIVER, t.subjectName(),
                    "Adeuda " + t.detail() + " y pide cursar " + t.subjectName() + " este ciclo con la correlativa pendiente.",
                    DirectorApprovalRequestStatus.REQUESTED, 4);
        }
        if (!lateExams.isEmpty()) {
            ExceptionTarget t = lateExams.get(0);
            saveRequest(dataset, t.enrollment(), ExceptionType.LATE_EXAM, t.subjectName(),
                    "Viaja por trabajo la semana de la mesa de noviembre y pide rendir el final de " + t.subjectName() + " fuera de término.",
                    DirectorApprovalRequestStatus.REQUESTED, 2);
        }
        if (waivers.size() > 1) {
            ExceptionTarget t = waivers.get(waivers.size() - 1);
            saveRequest(dataset, t.enrollment(), ExceptionType.PREREQUISITE_WAIVER, t.subjectName(),
                    "Viene de otra institución con " + t.detail() + " aprobada y la equivalencia todavía no está homologada.",
                    DirectorApprovalRequestStatus.REQUESTED, 1);
        }
        if (lateExams.size() > 1) {
            ExceptionTarget t = lateExams.get(1);
            saveRequest(dataset, t.enrollment(), ExceptionType.LATE_EXAM, t.subjectName(),
                    "La mesa de " + t.subjectName() + " se superpone con una mesa de otra carrera en la que también está inscripto.",
                    DirectorApprovalRequestStatus.APPROVED, 25);
        }
        if (waivers.size() > 2) {
            ExceptionTarget t = waivers.get(1);
            saveRequest(dataset, t.enrollment(), ExceptionType.PREREQUISITE_WAIVER, t.subjectName(),
                    "Solicita cursar " + t.subjectName() + " sin tener aprobada " + t.detail() + ".",
                    DirectorApprovalRequestStatus.REJECTED, 40);
        }
    }

    private void saveRequest(
            DemoDataset dataset, ProgramEnrollment enrollment, ExceptionType type, String subjectName,
            String justification, DirectorApprovalRequestStatus status, int daysAgo) {
        DirectorApprovalRequest request = new DirectorApprovalRequest();
        request.setType(type);
        request.setProgramEnrollment(enrollment);
        request.setSubject(dataset.subject(subjectName));
        request.setJustification(justification);
        request.setStatus(status);
        request.setRequestedBy(dataset.preceptorAt(0));
        LocalDateTime requestedAt = TODAY.minusDays(daysAgo).atTime(9, 45);
        request.setRequestedAt(requestedAt);
        if (status != DirectorApprovalRequestStatus.REQUESTED) {
            request.setDecidedBy(dataset.getDirector());
            request.setDecidedAt(requestedAt.plusDays(3));
            request.setDecisionNotes(status == DirectorApprovalRequestStatus.APPROVED
                    ? "Se autoriza por única vez, con seguimiento de la preceptoría"
                    : "No corresponde: la correlativa es condición de cursado según el plan vigente");
        }
        directorApprovalRequestRepository.save(request);
    }

    private void createGraduationRequests(DemoDataset dataset) {
        List<ProgramEnrollment> planComplete = dataset.getProgramEnrollments().stream()
                .filter(enrollment -> enrollment.getStatus() == ProgramEnrollmentStatus.ENROLLED)
                .filter(enrollment -> enrollment.getEnrollmentDate().getYear() == 2023)
                .toList();
        GraduationRequestStatus[] statuses = {
                GraduationRequestStatus.REQUESTED, GraduationRequestStatus.ADMIN_VERIFIED, GraduationRequestStatus.REJECTED};
        int[] daysAgo = {6, 18, 75};
        for (int i = 0; i < planComplete.size() && i < statuses.length; i++) {
            saveGraduation(dataset, planComplete.get(i), statuses[i], daysAgo[i]);
        }
    }

    private void saveGraduation(
            DemoDataset dataset, ProgramEnrollment enrollment, GraduationRequestStatus status, int daysAgo) {
        GraduationRequest request = graduationRequestRepository.findAll().stream()
                .filter(existing -> existing.getProgramEnrollment().getId().equals(enrollment.getId()))
                .findFirst()
                .orElseGet(GraduationRequest::new);
        request.setProgramEnrollment(enrollment);
        request.setStatus(status);
        LocalDateTime requestedAt = TODAY.minusDays(daysAgo).atTime(11, 10);
        request.setRequestedAt(requestedAt);
        if (status != GraduationRequestStatus.REQUESTED) {
            request.setVerifiedBy(dataset.administrativeAt(0));
            request.setVerifiedAt(requestedAt.plusDays(2));
        }
        if (status == GraduationRequestStatus.APPROVED || status == GraduationRequestStatus.REJECTED) {
            request.setDecidedBy(dataset.getDirector());
            request.setDecidedAt(requestedAt.plusDays(5));
            if (status == GraduationRequestStatus.REJECTED) {
                request.setRejectionReason("Falta presentar el analítico del secundario legalizado. Puede volver a solicitar el egreso cuando lo entregue en secretaría.");
            }
        }
        graduationRequestRepository.save(request);
    }

    private void createNotifications(DemoDataset dataset) {
        Person demoTeacher = dataset.getDemoInstructor().getPerson();
        Person demoStudent = dataset.getDemoStudent().getPerson();

        saveNotification(demoTeacher, NotificationType.SECTION_EVAL_CONFIG_APPROVED,
                "La dirección aprobó la configuración de evaluación de Didáctica del Nivel Inicial", true, 20);
        saveNotification(demoTeacher, NotificationType.SECTION_EVAL_CONFIG_REJECTED,
                "La dirección rechazó la configuración de evaluación de Literatura Infantil: revisá las fechas de parciales", false, 6);
        saveNotification(demoTeacher, NotificationType.ATTENDANCE_CHANGE_REQUEST_APPROVED,
                "Se aprobó tu pedido de cambio de asistencia del 18/09", false, 3);
        saveNotification(dataset.getDirector(), NotificationType.SECTION_EVAL_CONFIG_PROPOSED,
                "Tenés configuraciones de evaluación esperando aprobación", false, 1);
        saveNotification(demoStudent, NotificationType.DIRECTOR_APPROVAL_REQUEST_APPROVED,
                "La dirección aprobó tu excepción para rendir Didáctica General en mesa extraordinaria", false, 2);
        saveNotification(demoStudent, NotificationType.SUSPENSION_LIFTED,
                "Se levantó la suspensión por mora: ya podés inscribirte a mesas de examen", true, 30);
    }

    private void saveNotification(
            Person recipient, NotificationType type, String message, boolean read, int daysAgo) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setMessage(message);
        notification.setRead(read);
        notification.setCreatedAt(TODAY.minusDays(daysAgo).atTime(8, 30));
        notificationRepository.save(notification);
    }
}

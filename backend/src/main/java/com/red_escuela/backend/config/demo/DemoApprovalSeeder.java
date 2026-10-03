package com.red_escuela.backend.config.demo;

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

    private void createDirectorApprovalRequests(DemoDataset dataset) {
        List<ProgramEnrollment> active = dataset.getProgramEnrollments().stream()
                .filter(enrollment -> enrollment.getStatus() == ProgramEnrollmentStatus.ENROLLED)
                .toList();
        if (active.size() < 8) {
            return;
        }
        saveRequest(dataset, active.get(0), ExceptionType.PREREQUISITE_WAIVER, "Didáctica del Nivel Inicial",
                "Adeuda el final de Pedagogía pero ya cursó y aprobó la práctica. Pide cursar con la correlativa pendiente.",
                DirectorApprovalRequestStatus.REQUESTED, 4);
        saveRequest(dataset, active.get(2), ExceptionType.LATE_EXAM, "Programación I",
                "Faltó a la mesa de julio por internación. Presenta certificado médico y solicita mesa extraordinaria.",
                DirectorApprovalRequestStatus.REQUESTED, 2);
        saveRequest(dataset, active.get(4), ExceptionType.PREREQUISITE_WAIVER, "Bases de Datos",
                "Viene de otra institución con materias equivalentes sin homologar todavía.",
                DirectorApprovalRequestStatus.REQUESTED, 1);
        saveRequest(dataset, active.get(5), ExceptionType.LATE_EXAM, "Didáctica General",
                "Superposición con mesa de otra carrera en la que también está inscripta.",
                DirectorApprovalRequestStatus.APPROVED, 25);
        saveRequest(dataset, active.get(6), ExceptionType.PREREQUISITE_WAIVER, "Práctica Docente II",
                "Solicita cursar la práctica sin tener aprobada Psicología Educacional.",
                DirectorApprovalRequestStatus.REJECTED, 40);
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
        List<ProgramEnrollment> finished = dataset.getProgramEnrollments().stream()
                .filter(enrollment -> enrollment.getStatus() == ProgramEnrollmentStatus.COMPLETED)
                .toList();
        List<ProgramEnrollment> advanced = dataset.getProgramEnrollments().stream()
                .filter(enrollment -> enrollment.getStatus() == ProgramEnrollmentStatus.ENROLLED)
                .filter(enrollment -> enrollment.getEnrollmentDate().getYear() == 2024)
                .toList();

        if (!finished.isEmpty()) {
            saveGraduation(dataset, finished.get(0), GraduationRequestStatus.ADMIN_VERIFIED, 12);
        }
        if (advanced.size() >= 4) {
            saveGraduation(dataset, advanced.get(0), GraduationRequestStatus.REQUESTED, 6);
            saveGraduation(dataset, advanced.get(1), GraduationRequestStatus.ADMIN_VERIFIED, 18);
            saveGraduation(dataset, advanced.get(2), GraduationRequestStatus.APPROVED, 60);
            saveGraduation(dataset, advanced.get(3), GraduationRequestStatus.REJECTED, 75);
        }
    }

    private void saveGraduation(
            DemoDataset dataset, ProgramEnrollment enrollment, GraduationRequestStatus status, int daysAgo) {
        GraduationRequest request = new GraduationRequest();
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
                request.setRejectionReason("Adeuda el final de Investigación Educativa y dos cuotas del ciclo 2026");
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

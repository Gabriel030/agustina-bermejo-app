package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.entity.AudienceQuantifier;
import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("local & demo")
public class DemoAnnouncementSeeder {

    private record AnnouncementSpec(
            String title,
            String body,
            AnnouncementCategory category,
            boolean urgent,
            AudienceSegmentKey audienceKey,
            String audienceLabel,
            AnnouncementStatus status,
            int daysOffset) {}

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    private static final List<AnnouncementSpec> SPECS = List.of(
            new AnnouncementSpec(
                    "Segundo parcial: 11 de septiembre",
                    "El segundo parcial se toma el viernes 11 de septiembre a las 18:00 en el aula de siempre. "
                            + "Entra todo lo visto desde la unidad 5. Traigan calculadora y DNI.",
                    AnnouncementCategory.EVALUATION, true, AudienceSegmentKey.ALL,
                    "Toda la comisión", AnnouncementStatus.SENT, 28),
            new AnnouncementSpec(
                    "Material de la unidad 7 en el campus",
                    "Subí al campus los apuntes y las diapositivas de la unidad 7, junto con la guía de ejercicios.",
                    AnnouncementCategory.MATERIAL, false, AudienceSegmentKey.ALL,
                    "Toda la comisión", AnnouncementStatus.SENT, 21),
            new AnnouncementSpec(
                    "Recuperatorio para quienes desaprobaron el parcial",
                    "Quienes no alcanzaron el 4 en el segundo parcial tienen recuperatorio el 9 de octubre a las 18:00. "
                            + "Confirmen asistencia por mail antes del lunes.",
                    AnnouncementCategory.EVALUATION, true, AudienceSegmentKey.EXAM_FAILED,
                    "Desaprobaron el parcial 2", AnnouncementStatus.SENT, 9),
            new AnnouncementSpec(
                    "Falta entregar el TP 3",
                    "Todavía no recibí el trabajo práctico 3 de varios de ustedes. La entrega vence el viernes y "
                            + "sin el TP aprobado no se regulariza la materia.",
                    AnnouncementCategory.REMINDER, false, AudienceSegmentKey.TP_MISSING,
                    "No entregaron el TP 3", AnnouncementStatus.SENT, 5),
            new AnnouncementSpec(
                    "Atención con las inasistencias",
                    "Están cerca del límite de faltas permitido. Si necesitan justificar alguna, pasen por preceptoría "
                            + "con el certificado antes de fin de mes.",
                    AnnouncementCategory.ADMIN, true, AudienceSegmentKey.ABSENCES,
                    "Más de 5 inasistencias", AnnouncementStatus.SENT, 3),
            new AnnouncementSpec(
                    "Cambio de aula a partir del lunes",
                    "A partir del lunes nos mudamos al Aula Magna por refacciones en el aula habitual.",
                    AnnouncementCategory.SCHEDULING, false, AudienceSegmentKey.ALL,
                    "Toda la comisión", AnnouncementStatus.SENT, 1),
            new AnnouncementSpec(
                    "Fecha de la primera mesa de final",
                    "La primera mesa de final queda fijada para el 2 de diciembre a las 18:00. "
                            + "La inscripción abre dos semanas antes.",
                    AnnouncementCategory.EVALUATION, false, AudienceSegmentKey.MUST_TAKE_FINAL,
                    "Deben rendir final", AnnouncementStatus.SCHEDULED, -14),
            new AnnouncementSpec(
                    "Cierre de notas y condición final",
                    "La semana próxima publico el cierre de notas con la condición de cada uno.",
                    AnnouncementCategory.ADMIN, false, AudienceSegmentKey.ALL,
                    "Toda la comisión", AnnouncementStatus.SCHEDULED, -5));

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientRepository recipientRepository;

    public DemoAnnouncementSeeder(
            AnnouncementRepository announcementRepository,
            AnnouncementRecipientRepository recipientRepository) {
        this.announcementRepository = announcementRepository;
        this.recipientRepository = recipientRepository;
    }

    public void seed(DemoDataset dataset) {
        List<SubjectSection> sections = demoTeacherSections(dataset);
        if (sections.isEmpty()) {
            return;
        }
        for (int i = 0; i < SPECS.size(); i++) {
            AnnouncementSpec spec = SPECS.get(i);
            SubjectSection section = sections.get(i % sections.size());
            Announcement announcement = buildAnnouncement(dataset, spec, section);
            Announcement saved = announcementRepository.save(announcement);
            if (spec.status() == AnnouncementStatus.SENT) {
                materializeRecipients(dataset, saved, section, spec.audienceKey());
            }
        }
    }

    private List<SubjectSection> demoTeacherSections(DemoDataset dataset) {
        List<SubjectSection> sections = new ArrayList<>();
        for (SubjectSectionEnrollment enrollment : dataset.getSectionEnrollments()) {
            if (enrollment.getStatus() != EnrollmentStatus.IN_PROGRESS) {
                continue;
            }
            SubjectSection section = enrollment.getSubjectSection();
            boolean ownedByDemoTeacher = dataset.getDemoInstructor()
                    .equals(dataset.getSectionInstructors().get(section.getId()));
            if (ownedByDemoTeacher && sections.stream().noneMatch(s -> s.getId().equals(section.getId()))) {
                sections.add(section);
            }
        }
        return sections;
    }

    private Announcement buildAnnouncement(DemoDataset dataset, AnnouncementSpec spec, SubjectSection section) {
        Announcement announcement = new Announcement();
        announcement.setSubjectSection(section);
        announcement.setCreatedBy(dataset.getDemoInstructor().getPerson());
        announcement.setTitle(spec.title());
        announcement.setBody(spec.body());
        announcement.setCategory(spec.category());
        announcement.setUrgent(spec.urgent());
        announcement.setAudienceSpec(audienceSpecFor(spec.audienceKey()));
        announcement.setAudienceLabel(spec.audienceLabel());
        announcement.setStatus(spec.status());
        announcement.setCreatedAt(TODAY.minusDays(Math.max(spec.daysOffset(), 0) + 1L).atTime(8, 45));
        if (spec.status() == AnnouncementStatus.SENT) {
            announcement.setSentAt(TODAY.minusDays(spec.daysOffset()).atTime(9, 0));
            announcement.setExpiresAt(TODAY.plusDays(30));
        } else {
            announcement.setScheduledFor(TODAY.plusDays(-spec.daysOffset()).atTime(8, 0));
        }
        return announcement;
    }

    private AudienceSpec audienceSpecFor(AudienceSegmentKey key) {
        if (key == AudienceSegmentKey.ALL) {
            return AudienceSpec.wholeSection();
        }
        if (key == AudienceSegmentKey.ABSENCES) {
            return new AudienceSpec(key, null, AudienceQuantifier.ANY, 5);
        }
        return new AudienceSpec(key, "2", AudienceQuantifier.ANY, null);
    }

    private void materializeRecipients(
            DemoDataset dataset, Announcement announcement, SubjectSection section, AudienceSegmentKey key) {
        List<Student> students = new ArrayList<>();
        for (SubjectSectionEnrollment enrollment : dataset.getSectionEnrollments()) {
            if (enrollment.getSubjectSection().getId().equals(section.getId())
                    && enrollment.getStatus() == EnrollmentStatus.IN_PROGRESS) {
                students.add(enrollment.getStudent());
            }
        }
        int limit = key == AudienceSegmentKey.ALL ? students.size() : Math.max(1, students.size() / 2);
        for (int i = 0; i < limit; i++) {
            AnnouncementRecipient recipient = new AnnouncementRecipient();
            recipient.setAnnouncement(announcement);
            recipient.setStudent(students.get(i));
            recipientRepository.save(recipient);
        }
    }
}

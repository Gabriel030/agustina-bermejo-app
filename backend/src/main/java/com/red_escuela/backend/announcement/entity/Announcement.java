package com.red_escuela.backend.announcement.entity;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.announcement.exception.AnnouncementAlreadySentException;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "announcement")
@Check(constraints = "(subject_section_id IS NOT NULL AND course_section_id IS NULL) "
        + "OR (subject_section_id IS NULL AND course_section_id IS NOT NULL)")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_section_id")
    private SubjectSection subjectSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_section_id")
    private CourseSubjectSection courseSubjectSection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private AnnouncementCategory category;

    @Column(name = "urgent", nullable = false)
    private boolean urgent;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Embedded
    private AudienceSpec audienceSpec;

    @Column(name = "audience_label", nullable = false)
    private String audienceLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AnnouncementStatus status;

    @Column(name = "scheduled_for")
    private LocalDateTime scheduledFor;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void validateInvariants() {
        validateSingleSectionTarget();
        validateStatusMatchesSentAt();
    }

    private void validateSingleSectionTarget() {
        boolean hasProgram = this.subjectSection != null;
        boolean hasCourse = this.courseSubjectSection != null;
        if (hasProgram == hasCourse) {
            throw new IllegalStateException("Announcement must reference exactly one section: program OR course.");
        }
    }

    private void validateStatusMatchesSentAt() {
        if (this.status == AnnouncementStatus.SENT && this.sentAt == null) {
            throw new IllegalStateException("A sent announcement must have sentAt.");
        }
        if (this.status == AnnouncementStatus.SCHEDULED && this.sentAt != null) {
            throw new IllegalStateException("A scheduled announcement cannot have sentAt.");
        }
        if (this.status == AnnouncementStatus.SCHEDULED && this.scheduledFor == null) {
            throw new IllegalStateException("A scheduled announcement must have scheduledFor.");
        }
    }

    public void markAsSent(LocalDateTime when) {
        if (this.status == AnnouncementStatus.SENT) {
            throw new AnnouncementAlreadySentException(this.id);
        }
        this.status = AnnouncementStatus.SENT;
        this.sentAt = when;
    }

    public boolean isScheduled() {
        return this.status == AnnouncementStatus.SCHEDULED;
    }

    public SectionType getSectionType() {
        return subjectSection != null ? SectionType.PROGRAM : SectionType.COURSE;
    }

    public Long getSectionId() {
        return subjectSection != null ? subjectSection.getId() : courseSubjectSection.getId();
    }

    public LocalDateTime getFeedDate() {
        return sentAt != null ? sentAt : createdAt;
    }

    public boolean isExpiredOn(LocalDate date) {
        return expiresAt != null && expiresAt.isBefore(date);
    }
}

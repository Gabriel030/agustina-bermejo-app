package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;

@Getter
@Setter
@Entity
@Table(name = "course_enrollment")
public class CourseEnrollment implements BillableEnrollment {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private EnrollmentStatus status;

    @Column(name = "enrollment_date")
    private LocalDateTime enrollmentDate;

    @Column(name = "completion_date")
    private LocalDateTime completionDate;

    // Mantenido por EnrollmentBillingComplianceSweepService (Fase 6.3) — ver BillableEnrollment.
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_standing", nullable = false)
    private EnrollmentBillingStanding billingStanding = EnrollmentBillingStanding.UP_TO_DATE;

    @Column(name = "billing_standing_updated_at")
    private LocalDateTime billingStandingUpdatedAt;

    @OneToMany(mappedBy = "courseEnrollment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = false)
    private List<CourseSubjectSectionEnrollment> sectionEnrollments;
}

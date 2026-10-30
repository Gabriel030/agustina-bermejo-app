package com.red_escuela.backend.enrollment.entity;

import org.springframework.modulith.NamedInterface;
import com.red_escuela.backend.identity.entity.Student;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.academicstructure.entity.Course;

@NamedInterface("model")
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

    @Column(name = "withdrawal_effective_date")
    private LocalDate withdrawalEffectiveDate;

    @Column(name = "withdrawal_registered_on")
    private LocalDate withdrawalRegisteredOn;

    @Column(name = "withdrawn_by_person_id")
    private Long withdrawnByPersonId;

    @OneToMany(mappedBy = "courseEnrollment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = false)
    private List<CourseSubjectSectionEnrollment> sectionEnrollments;

    public void markWithdrawn(LocalDate effectiveDate, LocalDateTime completionMoment, LocalDate registeredOn, Long registeredByPersonId) {
        this.status = EnrollmentStatus.WITHDRAWN;
        this.withdrawalEffectiveDate = effectiveDate;
        this.withdrawalRegisteredOn = registeredOn;
        this.completionDate = completionMoment;
        this.withdrawnByPersonId = registeredByPersonId;
    }
}

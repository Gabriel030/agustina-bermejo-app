package com.red_escuela.backend.identity.entity;

import org.springframework.modulith.NamedInterface;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@NamedInterface("model")
@Getter
@Setter
@Entity
@Table(name = "student")
public class Student {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    @OneToMany(mappedBy = "student", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseEnrollment> courseEnrollments;

    @OneToMany(mappedBy = "student", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramEnrollment> programEnrollments;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "grant_final_exam_sign_up")
    private Boolean grantFinalExamSignUp;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false)
    private StudentOnboardingStatus onboardingStatus = StudentOnboardingStatus.CONFIRMADO;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_reason")
    private WithdrawalReason withdrawalReason;

    @Column(name = "withdrawal_detail")
    private String withdrawalDetail;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "registered_by_person_id")
    private Long registeredByPersonId;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "guardian_full_name")
    private String guardianFullName;

    @Column(name = "guardian_dni")
    private String guardianDni;

    @Column(name = "guardian_relationship")
    private String guardianRelationship;

    @Column(name = "guardian_phone")
    private String guardianPhone;

    @Column(name = "guardian_email")
    private String guardianEmail;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_relationship")
    private String emergencyContactRelationship;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;
}

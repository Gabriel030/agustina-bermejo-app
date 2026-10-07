package com.red_escuela.backend.identity.entity;

import org.springframework.modulith.NamedInterface;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
}

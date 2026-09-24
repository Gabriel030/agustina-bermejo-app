package com.red_escuela.backend.academicstructure.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;
import com.red_escuela.backend.entity.StatusType;

@Getter
@Setter
@Entity
@Table(name = "program")
public class Program {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "enrollment_fee_amount")
    private BigDecimal enrollmentFeeAmount;

    @Column(name = "total_years")
    private Integer totalYears;

    @Column(name = "total_credits_required", nullable = false)
    private Integer totalCreditsRequired = 0;

    @Column(name = "total_hours_required")
    private Integer totalHoursRequired;

    @Column(name = "visible_in_catalog", nullable = false)
    private boolean visibleInCatalog = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "teaching_mode")
    private TeachingMode teachingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "level")
    private ProgramLevel level;

    @Column(name = "graduation_profile", columnDefinition = "TEXT")
    private String graduationProfile;

    @Column(name = "admission_requirements", columnDefinition = "TEXT")
    private String admissionRequirements;

    @OneToMany(mappedBy = "program", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramSubject> programSubjectsList;
}

package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "program_elective_group")
public class ProgramElectiveGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "elective_group_id", nullable = false)
    private ElectiveGroup electiveGroup;

    @Column(name = "min_required", nullable = false)
    private Integer minRequired;

    @Column(name = "max_allowed", nullable = false)
    private Integer maxAllowed;
}

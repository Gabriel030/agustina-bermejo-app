package com.red_escuela.backend.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "term")
public class Term {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_number")
    private Integer year;

    @Column(name = "description")
    private String description;

    @Column(name = "type")
    private String type;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @Column(name = "enrollment_start")
    private LocalDateTime enrollmentStart;

    @Column(name = "enrollment_end")
    private LocalDateTime enrollmentEnd;
}

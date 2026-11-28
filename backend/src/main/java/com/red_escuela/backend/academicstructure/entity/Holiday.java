package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.academicstructure.domain.HolidayScope;
import com.red_escuela.backend.academicstructure.domain.HolidayType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "holiday")
public class Holiday {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", nullable = false, unique = true)
    private LocalDate date;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private HolidayType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private HolidayScope scope = HolidayScope.BOTH;

    @Column(name = "description")
    private String description;
}

package com.red_escuela.backend.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "materia_carrera")
public class MateriaCarrera {
    @Id
    @Column(name = "materia_carrera_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long materiaCarreraId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "materia_id")
    private Materia materia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrera_id")
    private Carrera carrera;

    @Column(name = "anio")
    private Integer anio;

    @Column(name = "activo")
    private Boolean activo;

}

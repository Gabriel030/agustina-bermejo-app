package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "programa_estudio")
public class ProgramaEstudio {
    @Id
    @Column(name = "programa_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long programaId;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "grado")
    private String grado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrera_id")
    private Carrera carrera;

    @Column(name = "anio")
    private Integer anio;

    @Column(name = "activo")
    private Boolean activo;

}

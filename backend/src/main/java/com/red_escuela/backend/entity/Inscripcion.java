package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "inscripcion")
public class Inscripcion {
    @Id
    @Column(name = "inscripcion_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long inscripcionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id")
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curso_id")
    private Curso curso;

    @Column(name = "activo")
    private Boolean activo;

}

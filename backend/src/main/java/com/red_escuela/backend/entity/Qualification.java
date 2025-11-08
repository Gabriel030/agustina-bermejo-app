package com.red_escuela.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "qualification")
@IdClass(NotasId.class) // clave compuesta persona + curso
public class Qualification {
    @Id
    @Column(name = "persona_id")
    private Long personaId;

    @Id
    @Column(name = "curso_id")
    private Long cursoId;

    @Column(name = "nota")
    private Double nota;

    @Column(name = "tipo")
    private String tipo;
}

package com.red_escuela.backend.entity;
import jakarta.persistence.*;

@Entity
@Table(name = "programa_estudio_curso")
public class ProgramaEstudioCurso {
    @Id
    @Column(name = "programa_estudio_curso_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long programaEstudioCursoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "programa_estudio_id")
    private ProgramaEstudio programaEstudio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curso_id")
    private Curso curso;

    @Column(name = "activo")
    private Boolean activo;
}

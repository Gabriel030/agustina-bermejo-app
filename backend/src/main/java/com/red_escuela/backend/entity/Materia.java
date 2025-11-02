package com.red_escuela.backend.entity;
import jakarta.persistence.*;

@Entity
@Table(name = "materia")
public class Materia {
    @Id
    @Column(name = "materia_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long materiaId;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "carga_horaria")
    private Integer cargaHoraria;

    @Column(name = "duracion")
    private Integer duracion;

    @Column(name = "status")
    private Boolean status;

    @Column(name = "forma_calificacion")
    private String formaCalificacion;

    @Column(name = "aprob_parcial")
    private Boolean aprobParcial;

    @Column(name = "aprob_recuperatorio")
    private Boolean aprobRecuperatorio;

    @Column(name = "aprob_final")
    private Boolean aprobFinal;
}

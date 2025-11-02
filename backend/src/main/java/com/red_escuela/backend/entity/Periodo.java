package com.red_escuela.backend.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "periodo")
public class Periodo {
    @Id
    @Column(name = "periodo_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long periodoId;

    @Column(name = "anio")
    private Integer anio;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "tipo")
    private String tipo;

    @Column(name = "inicio")
    private LocalDateTime inicio;

    @Column(name = "final")
    private LocalDateTime fin;

    @Column(name = "status")
    private Boolean status;
}

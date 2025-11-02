package com.red_escuela.backend.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "carrera")
public class Carrera {
    @Id
    @Column(name = "carrera_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long carreraId;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "status")
    private Boolean status;
}

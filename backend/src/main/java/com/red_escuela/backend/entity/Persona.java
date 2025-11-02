package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "persona")
public class Persona {
    @Id
    @Column(name = "persona_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long personaId;

    @Column(name = "legajo")
    private String legajo;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "apellido")
    private String apellido;

    @Column(name = "sexo")
    private String sexo;

    @Column(name = "celular")
    private String celular;

    @Column(name = "telefono_fijo")
    private String telefonoFijo;

    @Column(name = "email")
    private String email;

    @Column(name = "dni")
    private String dni;

    @Column(name = "direccion")
    private String direccion;

    @Column(name = "numero")
    private String numero;

    @Column(name = "piso")
    private String piso;

    @Column(name = "departamento")
    private String departamento;

    @Column(name = "ciudad")
    private String ciudad;

    @Column(name = "partido")
    private String partido;

    @Column(name = "provincia")
    private String provincia;

    @Column(name = "pais")
    private String pais;

    @Column(name = "nacionalidad")
    private String nacionalidad;

    @Column(name = "activo")
    private Boolean activo;

    @Column(name = "rol_id")
    private Long rolId;

    @Column(name = "password")
    private String password;

    @Column(name = "moroso")
    private Boolean moroso;

    @Column(name = "permitir_ingreso_sistema")
    private Boolean permitirIngresoAlSistema;

    @Column(name = "permitir_anotar_final")
    private Boolean permitirAnotarFinal;
}

package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "institucion")
public class Institucion {
    @Id
    @Column(name = "institucion_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long institucionId;

    @Column(name = "nombre")
    private String nombre;

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

    @Column(name = "telefono_1")
    private String telefono1;

    @Column(name = "telefono_2")
    private String telefono2;

    @Column(name = "telefono_3")
    private String telefono3;

    @Column(name = "sitio_web")
    private String sitioWeb;

    @Column(name = "facebook")
    private String facebook;

    @Column(name = "tweeter")
    private String tweeter;

    @Column(name = "pref_notas")
    private Boolean prefNotas;

    @Column(name = "pref_calendario")
    private Boolean prefCalendario;

    @Column(name = "pref_asignar_curso")
    private Boolean prefAsignarCurso;

    @Column(name = "email")
    private String email;

    @Column(name = "sms_url")
    private String smsUrl;

    @Column(name = "sms_usuario")
    private String smsUsuario;

    @Column(name = "sms_password")
    private String smsPassword;

}

package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "institution")
public class Institution {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "address")
    private Address address;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "secondary_phone_number")
    private String secondaryPhoneNumber;

    @Column(name = "website")
    private String website;

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

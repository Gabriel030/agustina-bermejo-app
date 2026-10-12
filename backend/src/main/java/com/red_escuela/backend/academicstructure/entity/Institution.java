package com.red_escuela.backend.academicstructure.entity;

import org.springframework.modulith.NamedInterface;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.red_escuela.backend.shared.domain.Address;

@NamedInterface("model")
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
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

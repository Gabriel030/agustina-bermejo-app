package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "person")
public class Person {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file")
    private String file;

    @Column(name = "name")
    private String name;

    @Column(name = "last_name")
    private String lastname;

    @Column(name = "sex")
    private String sex;

    @Column(name = "email")
    private String email;

    @Column(name = "dni")
    private String dni;

    @Column(name = "address")
    private Address address;

    @Column(name = "nationality")
    private String nationality;

    @Column(name = "status")
    private Boolean status;

    @Column(name = "rol_id")
    private Long rolId;

    @Column(name = "password")
    private String password;

    @Column(name = "defaulter")
    private Boolean defaulter;

    @Column(name = "grant_system_access")
    private Boolean grantSystemAccess;

    @Column(name = "grant_final_exam_sign_up")
    private Boolean grantFinalExamSignUp;
}

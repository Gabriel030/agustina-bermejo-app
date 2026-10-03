package com.red_escuela.backend.identity.entity;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.StatusType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "person")
public class Person {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file", insertable = false, updatable = false)
    private String file;

    @Column(name = "name")
    private String name;

    @Column(name = "last_name")
    private String lastname;

    @Column(name = "sex")
    private String sex;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "dni", unique = true)
    private String dni;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Column(name = "nationality")
    private String nationality;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "person_role",
        joinColumns = @JoinColumn(name = "person_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @Column(name = "password")
    private String password;

    @Column(name = "grant_system_access")
    private Boolean grantSystemAccess;

    @Column(name = "username", unique = true)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "google_subject_id", unique = true)
    private String googleSubjectId;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "avatar_path", length = 500)
    private String avatarPath;

    @Column(name = "email_verified")
    private Boolean emailVerified;

    public void changeDni(String newDni) {
        this.dni = newDni == null || newDni.isBlank() ? null : newDni.trim();
    }
}

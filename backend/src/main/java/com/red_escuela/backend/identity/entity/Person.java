package com.red_escuela.backend.identity.entity;

import org.springframework.modulith.NamedInterface;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.DocumentNumber;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.StatusType;

import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.persistence.EmailAddressConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@NamedInterface("model")
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
    @Convert(converter = EmailAddressConverter.class)
    private EmailAddress email;

    @Column(name = "dni")
    private String dni;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType = DocumentType.DNI;

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

    public void setEmail(EmailAddress email) {
        this.email = email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : EmailAddress.of(email);
    }

    public String emailValue() {
        return email == null ? null : email.value();
    }

    public void changeDocument(DocumentType type, String number) {
        DocumentType resolved = type == null ? DocumentType.DNI : type;
        if (number == null || number.isBlank()) {
            this.documentType = resolved;
            this.dni = null;
            return;
        }
        DocumentNumber document = DocumentNumber.of(resolved, number);
        this.documentType = document.type();
        this.dni = document.value();
    }
}

package com.red_escuela.backend.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "login_attempt")
public class LoginAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "person_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Person person;

    @Column(name = "attempted_username")
    private String attemptedUsername;

    @Column(name = "successful", nullable = false)
    private Boolean successful;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;
}

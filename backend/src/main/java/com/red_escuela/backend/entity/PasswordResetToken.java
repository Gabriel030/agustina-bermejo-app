package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Person person;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    private Boolean used = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    // Se completan recién cuando el código de 6 dígitos se verificó correctamente
    // (AuthService.verifyResetCode) — separan "probaste que el email es tuyo" de
    // "ya cambiaste la contraseña", así la pantalla de contraseña nueva no necesita
    // volver a pedir el código. exchangeTokenHash se pone en null al consumirse
    // (AuthService.resetPassword), evitando que el mismo intercambio sirva dos veces.
    @Column(name = "exchange_token_hash")
    private String exchangeTokenHash;

    @Column(name = "exchange_expires_at")
    private LocalDateTime exchangeExpiresAt;
}

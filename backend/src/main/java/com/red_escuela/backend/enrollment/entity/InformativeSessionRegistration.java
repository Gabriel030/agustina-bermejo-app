package com.red_escuela.backend.enrollment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "informative_session_registration")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class InformativeSessionRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

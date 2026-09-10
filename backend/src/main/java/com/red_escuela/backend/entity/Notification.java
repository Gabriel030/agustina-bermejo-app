package com.red_escuela.backend.entity;
import com.red_escuela.backend.identity.entity.Person;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Person recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "read", nullable = false)
    private boolean read = false;

    @Column(name = "related_entity_type")
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

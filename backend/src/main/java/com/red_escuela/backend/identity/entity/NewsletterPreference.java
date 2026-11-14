package com.red_escuela.backend.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "newsletter_preference")
public class NewsletterPreference {

    @Id
    @Column(name = "person_id")
    private Long personId;

    @Column(name = "opted_in", nullable = false)
    private boolean optedIn;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

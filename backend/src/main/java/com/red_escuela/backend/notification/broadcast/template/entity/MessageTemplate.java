package com.red_escuela.backend.notification.broadcast.template.entity;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "message_template")
public class MessageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private BroadcastCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ChannelType channel;

    @Column(name = "subject", length = 300)
    private String subject;

    @Column(name = "body_text", nullable = false, columnDefinition = "text")
    private String bodyText;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by_person_id", nullable = false, updatable = false)
    private Long createdByPersonId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void rewrite(String newName, BroadcastCategory newCategory, ChannelType newChannel, String newSubject,
                        String newBodyText, Instant at) {
        this.name = newName;
        this.category = newCategory;
        this.channel = newChannel;
        this.subject = newSubject;
        this.bodyText = newBodyText;
        this.updatedAt = at;
    }

    public void deactivate(Instant at) {
        this.active = false;
        this.updatedAt = at;
    }
}

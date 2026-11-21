package com.red_escuela.backend.identity.entity;

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

import java.time.Instant;

@Entity
@Table(name = "permission_discrepancy_counter")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PermissionDiscrepancyCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "module", nullable = false)
    private String module;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    @Column(name = "occurrences", nullable = false)
    private long occurrences;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}

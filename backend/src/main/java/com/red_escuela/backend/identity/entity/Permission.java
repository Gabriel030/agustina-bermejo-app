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

@Entity
@Table(name = "permission")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "module", nullable = false)
    private String module;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "fixed_to_director", nullable = false)
    private boolean fixedToDirector;
}

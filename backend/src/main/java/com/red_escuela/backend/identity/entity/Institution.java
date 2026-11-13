package com.red_escuela.backend.identity.entity;

import org.springframework.modulith.NamedInterface;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.red_escuela.backend.shared.domain.Address;

@NamedInterface("model")
@Getter
@Setter
@Entity
@Table(name = "institution")
public class Institution {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private Address address;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "secondary_phone_number")
    private String secondaryPhoneNumber;

    @Column(name = "website")
    private String website;

    @Column(name = "email")
    private String email;

    @Column(name = "justification_deadline_business_days", nullable = false)
    private int justificationDeadlineBusinessDays = 5;

    @Column(name = "evaluation_config_due_days", nullable = false)
    private int evaluationConfigDueDays = 15;

    @Column(name = "announcement_email_enabled", nullable = false)
    private boolean announcementEmailEnabled = false;
}

package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.academicstructure.exception.SectionCapacityExceedsClassroomException;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "classroom")
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusType status = StatusType.ACTIVE;

    public boolean canHost(Integer seats) {
        return seats == null || seats <= capacity;
    }

    public void ensureCanHost(Integer seats) {
        if (!canHost(seats)) {
            throw new SectionCapacityExceedsClassroomException(id, capacity, seats);
        }
    }
}

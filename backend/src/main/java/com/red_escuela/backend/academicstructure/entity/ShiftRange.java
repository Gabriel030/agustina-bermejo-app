package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "shift_range")
public class ShiftRange {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "shift")
    private SectionShift shift;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public ShiftRange(SectionShift shift, LocalTime startTime, LocalTime endTime) {
        this.shift = shift;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean contains(LocalTime start, LocalTime end) {
        return !start.isBefore(startTime) && !end.isAfter(endTime);
    }
}

package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.HolidayAffectedItem;
import com.red_escuela.backend.academicstructure.api.HolidayImpactPort;
import com.red_escuela.backend.academicstructure.domain.HolidayScope;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class HolidayImpactFinder {

    private final List<HolidayImpactPort> impactSources;

    public HolidayImpactFinder(List<HolidayImpactPort> impactSources) {
        this.impactSources = impactSources;
    }

    @Transactional(readOnly = true)
    public List<HolidayAffectedItem> findAffectedBy(Holiday holiday) {
        if (holiday.getScope() == HolidayScope.BILLING) {
            return List.of();
        }
        return findAffectedOn(holiday.getDate());
    }

    @Transactional(readOnly = true)
    public List<HolidayAffectedItem> findAffectedOn(LocalDate date) {
        return impactSources.stream().flatMap(source -> source.findAffectedOn(date).stream()).toList();
    }
}

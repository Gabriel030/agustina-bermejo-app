package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.api.HolidayAffectedItem;
import com.red_escuela.backend.academicstructure.api.HolidayImpactPort;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class ClassSessionHolidayImpactPortAdapter implements HolidayImpactPort {

    private final ClassSessionRepository classSessionRepository;

    public ClassSessionHolidayImpactPortAdapter(ClassSessionRepository classSessionRepository) {
        this.classSessionRepository = classSessionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HolidayAffectedItem> findAffectedOn(LocalDate date) {
        return classSessionRepository.findByClassDate(date).stream()
                .map(session -> new HolidayAffectedItem("CLASS_SESSION", session.getId(), date))
                .toList();
    }
}

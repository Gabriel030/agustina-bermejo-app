package com.red_escuela.backend.testsupport;

import jakarta.persistence.EntityManager;

import java.time.LocalDate;

public final class HolidayFixtures {

    public static final int HOLIDAYS_PER_YEAR = 5;

    private HolidayFixtures() {
    }

    public static void ensureHolidaysLoaded(EntityManager entityManager, int fromYear, int toYear) {
        for (int year = fromYear; year <= toYear; year++) {
            for (int day = 1; day <= HOLIDAYS_PER_YEAR; day++) {
                entityManager.createNativeQuery("insert into holiday (date, description, name, type) values (?1, 'Fixture', 'Fixture', 'NATIONAL') on conflict do nothing")
                        .setParameter(1, LocalDate.of(year, 1, day))
                        .executeUpdate();
            }
        }
    }
}

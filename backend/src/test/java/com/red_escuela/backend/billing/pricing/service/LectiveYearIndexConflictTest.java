package com.red_escuela.backend.billing.pricing.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-532")
class LectiveYearIndexConflictTest {

    @Test
    void aViolationOfTheLectiveYearUniqueIndexIsTreatedAsAConcurrentPriceWrite() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_price_version_lective_year\"");

        assertThat(OfferingPriceCatalogService.isConcurrentPriceWrite(violation)).isTrue();
    }
}

package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.service.AdvanceEnrollmentFeePriceFinder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-532")
class AdvanceEnrollmentFeePriceFinderIT extends AbstractAdvanceEnrollmentFeeTest {

    @Autowired
    private AdvanceEnrollmentFeePriceFinder finder;

    private Long labeled(Career career, int year, String status) {
        Long id = com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(career.programId(), null, FeeType.ENROLLMENT_FEE, "Matrícula", ars(1500),
                date(2028, 2, 1), career.directorId());
        jdbcTemplate.update("update price_version set applies_to_lective_year = ?, status = ? where id = ?", year, status, id);
        return id;
    }

    @Test
    void itTakesTheLiveVersionLabeledForTheProgramAndYear() {
        Career career = newCareer();
        Long scheduled = labeled(career, 2028, "SCHEDULED");

        assertThat(finder.findLiveVersion(career.programId(), 2028)).map(PriceVersion::getId).contains(scheduled);
    }

    @Test
    void itIgnoresAVersionLabeledForAnotherProgram() {
        Career career = newCareer();
        Career other = newCareer();
        labeled(other, 2028, "SCHEDULED");

        assertThat(finder.findLiveVersion(career.programId(), 2028)).isEmpty();
    }

    @Test
    void itIgnoresAVersionLabeledForAnotherYear() {
        Career career = newCareer();
        labeled(career, 2029, "SCHEDULED");

        assertThat(finder.findLiveVersion(career.programId(), 2028)).isEmpty();
    }

    @Test
    void itIgnoresSupersededAndExpiredVersions() {
        Career career = newCareer();
        for (String status : new String[] {"SUPERSEDED", "EXPIRED"}) {
            labeled(career, 2028, status);
        }

        Optional<PriceVersion> found = finder.findLiveVersion(career.programId(), 2028);

        assertThat(found).isEmpty();
    }

    @Test
    void itIgnoresUnlabeledVersions() {
        Career career = newCareer();

        assertThat(finder.findLiveVersion(career.programId(), 2028)).isEmpty();
    }
}

package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static com.red_escuela.backend.shared.domain.TestMoney.usd;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-339")
class OverdueContactFilterTest {

    private static final OverdueContactCriteria CRITERIA = new OverdueContactCriteria(14, 2);

    private static ListedOverdueContact listed(int installments, int oldestDays, boolean newlyListed) {
        OverdueContact contact = new OverdueContact(1L, oldestDays, installments, List.of(ars(1000)));
        return new ListedOverdueContact(contact, newlyListed, CRITERIA.reasonFor(oldestDays, installments).orElseThrow());
    }

    @Test
    void theReasonSaysWhichPartOfTheCriteriaIsMet() {
        assertThat(CRITERIA.reasonFor(14, 1)).contains(OverdueContactReason.DAYS_OVERDUE);
        assertThat(CRITERIA.reasonFor(13, 2)).contains(OverdueContactReason.OVERDUE_INSTALLMENTS);
        assertThat(CRITERIA.reasonFor(14, 2)).contains(OverdueContactReason.DAYS_AND_INSTALLMENTS);
        assertThat(CRITERIA.reasonFor(13, 1)).isEmpty();
        assertThat(CRITERIA.isMetBy(13, 1)).isFalse();
        assertThat(CRITERIA.isMetBy(14, 1)).isTrue();
    }

    @Test
    void withoutFiltersEverythingPasses() {
        assertThat(OverdueContactFilter.none().accepts(listed(1, 20, false))).isTrue();
    }

    @Test
    void theMinimumsAreInclusive() {
        OverdueContactFilter filter = new OverdueContactFilter(null, 20, 3, false, null);

        assertThat(filter.accepts(listed(3, 20, false))).isTrue();
        assertThat(filter.accepts(listed(3, 19, false))).isFalse();
        assertThat(filter.accepts(listed(2, 20, false))).isFalse();
    }

    @Test
    void onlyNewlyListedKeepsTheNewcomers() {
        OverdueContactFilter filter = new OverdueContactFilter(null, null, null, true, null);

        assertThat(filter.accepts(listed(1, 14, true))).isTrue();
        assertThat(filter.accepts(listed(1, 14, false))).isFalse();
    }

    @Test
    void theEntryReasonKeepsOnlyThatReason() {
        OverdueContactFilter filter = new OverdueContactFilter(null, null, null, false, OverdueContactReason.OVERDUE_INSTALLMENTS);

        assertThat(filter.accepts(listed(2, 5, false))).isTrue();
        assertThat(filter.accepts(listed(2, 20, false))).isFalse();
        assertThat(filter.accepts(listed(1, 20, false))).isFalse();
    }

    @Test
    void theCurrencyKeepsOnlyWhoOwesIt() {
        OverdueContact dollars = new OverdueContact(2L, 20, 1, List.of(usd(10)));
        ListedOverdueContact listedDollars = new ListedOverdueContact(dollars, false, OverdueContactReason.DAYS_OVERDUE);

        assertThat(OverdueContactFilter.byCurrency(Currency.USD).accepts(listedDollars)).isTrue();
        assertThat(OverdueContactFilter.byCurrency(Currency.ARS).accepts(listedDollars)).isFalse();
    }
}

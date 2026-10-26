package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.billing.operation.BillingOperationType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-221")
class AuthorizationOperationCatalogTest {

    @Test
    void everyOperationTypeHasADistinctSpanishLabelAndAFamily() {
        Set<String> labels = new HashSet<>();
        for (BillingOperationType type : BillingOperationType.values()) {
            assertThat(AuthorizationOperationCatalog.familyOf(type)).isNotNull();
            assertThat(labels.add(AuthorizationOperationCatalog.labelOf(type))).as(type.name()).isTrue();
        }
    }

    @Test
    void theFamiliesPartitionEveryOperationType() {
        Set<BillingOperationType> union = new HashSet<>();
        int total = 0;
        for (AuthorizationOperationFamily family : AuthorizationOperationFamily.values()) {
            Set<BillingOperationType> types = AuthorizationOperationCatalog.typesOf(EnumSet.of(family));
            total += types.size();
            union.addAll(types);
        }
        assertThat(union).containsExactlyInAnyOrder(BillingOperationType.values());
        assertThat(total).isEqualTo(BillingOperationType.values().length);
    }

    @Test
    void theOperationsThatOpenRequestsTodayBelongToTheExpectedFamilies() {
        assertThat(AuthorizationOperationCatalog.familyOf(BillingOperationType.ISSUE_REFUND)).isEqualTo(AuthorizationOperationFamily.REFUNDS);
        assertThat(AuthorizationOperationCatalog.familyOf(BillingOperationType.CANCEL_INVOICE)).isEqualTo(AuthorizationOperationFamily.INVOICES_AND_NOTES);
        assertThat(AuthorizationOperationCatalog.familyOf(BillingOperationType.ISSUE_CREDIT_NOTE)).isEqualTo(AuthorizationOperationFamily.INVOICES_AND_NOTES);
        assertThat(AuthorizationOperationCatalog.familyOf(BillingOperationType.REGISTER_CHARGEBACK)).isEqualTo(AuthorizationOperationFamily.PAYMENTS_AND_CHARGEBACKS);
        assertThat(AuthorizationOperationCatalog.familyOf(BillingOperationType.GRANT_STUDENT_DISCOUNT)).isEqualTo(AuthorizationOperationFamily.DISCOUNTS_AND_PRICES);
        assertThat(AuthorizationOperationCatalog.familyOf(BillingOperationType.CHANGE_BILLING_CONFIGURATION)).isEqualTo(AuthorizationOperationFamily.CONFIGURATION);
        assertThat(Arrays.stream(AuthorizationOperationFamily.values()).map(AuthorizationOperationFamily::label)).doesNotHaveDuplicates();
    }

    @Test
    void theTypeAndFamilyFiltersIntersectAndAnEmptyFilterMatchesNothingToRestrict() {
        AuthorizationRequestSearch both = new AuthorizationRequestSearch(null, Set.of(BillingOperationType.CANCEL_INVOICE, BillingOperationType.ISSUE_REFUND),
                Set.of(AuthorizationOperationFamily.REFUNDS), null, null, true, 0, 20);
        AuthorizationRequestSearch disjoint = new AuthorizationRequestSearch(null, Set.of(BillingOperationType.CANCEL_INVOICE), Set.of(AuthorizationOperationFamily.REFUNDS),
                null, null, true, 0, 20);
        AuthorizationRequestSearch familyOnly = new AuthorizationRequestSearch(null, null, Set.of(AuthorizationOperationFamily.CONFIGURATION), null, null, true, 0, 20);
        AuthorizationRequestSearch none = new AuthorizationRequestSearch(null, null, null, null, null, true, 0, 20);

        assertThat(both.matchingOperationTypes()).containsExactly(BillingOperationType.ISSUE_REFUND);
        assertThat(disjoint.filtersOperationTypes()).isTrue();
        assertThat(disjoint.matchingOperationTypes()).isEmpty();
        assertThat(familyOnly.matchingOperationTypes()).containsExactlyInAnyOrder(BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS,
                BillingOperationType.CHANGE_BILLING_CONFIGURATION, BillingOperationType.CANCEL_CONFIGURATION_VERSION);
        assertThat(none.filtersOperationTypes()).isFalse();
    }

    @Test
    void thePageAndSizeAreBoundedAndTheSortDefaultsToExpiry() {
        AuthorizationRequestSearch huge = new AuthorizationRequestSearch(null, null, null, null, null, true, -3, 5000);
        AuthorizationRequestSearch tiny = new AuthorizationRequestSearch(null, null, null, null, AuthorizationRequestSort.REQUESTED_AT, false, 2, 0);

        assertThat(huge.size()).isEqualTo(AuthorizationRequestSearch.MAX_PAGE_SIZE);
        assertThat(huge.page()).isZero();
        assertThat(huge.sort()).isEqualTo(AuthorizationRequestSort.EXPIRES_ON);
        assertThat(tiny.size()).isEqualTo(1);
        assertThat(tiny.page()).isEqualTo(2);
    }
}

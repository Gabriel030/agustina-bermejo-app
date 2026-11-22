package com.red_escuela.backend.shared.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

class CatalogPriceVisibilityPolicyTest {

    private static Authentication userWith(String... authorities) {
        return new TestingAuthenticationToken("1", null, Arrays.asList(authorities).toArray(new String[0]));
    }

    private static CatalogPriceVisibilityPolicy policyWith(BillingMode mode) {
        return new CatalogPriceVisibilityPolicy(() -> mode);
    }

    @Test
    void billingOffHidesPricesOnStudentReads() {
        assertThat(policyWith(BillingMode.OFF).hidesPrices(HttpMethod.GET, userWith("ROLE_ALUMNO"))).isTrue();
    }

    @Test
    void billingOffHidesPricesOnAnonymousReads() {
        assertThat(policyWith(BillingMode.OFF).hidesPrices(HttpMethod.GET, null)).isTrue();
    }

    @Test
    void billingOffKeepsPricesForAnyStaffRoleEvenWhenAlsoStudent() {
        CatalogPriceVisibilityPolicy policy = policyWith(BillingMode.OFF);

        assertThat(policy.hidesPrices(HttpMethod.GET, userWith("ROLE_ADMINISTRATIVO"))).isFalse();
        assertThat(policy.hidesPrices(HttpMethod.GET, userWith("ROLE_ALUMNO", "ROLE_DOCENTE"))).isFalse();
    }

    @Test
    void billingOffKeepsPricesOnWrites() {
        CatalogPriceVisibilityPolicy policy = policyWith(BillingMode.OFF);

        assertThat(policy.hidesPrices(HttpMethod.POST, userWith("ROLE_ALUMNO"))).isFalse();
        assertThat(policy.hidesPrices(HttpMethod.PUT, userWith("ROLE_ALUMNO"))).isFalse();
    }

    @Test
    void billingEnabledNeverHidesPrices() {
        assertThat(policyWith(BillingMode.MIRROR).hidesPrices(HttpMethod.GET, userWith("ROLE_ALUMNO"))).isFalse();
        assertThat(policyWith(BillingMode.FULL).hidesPrices(HttpMethod.GET, userWith("ROLE_ALUMNO"))).isFalse();
    }
}

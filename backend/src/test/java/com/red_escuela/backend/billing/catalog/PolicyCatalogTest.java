package com.red_escuela.backend.billing.catalog;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-051")
class PolicyCatalogTest extends AbstractIntegrationTest {

    private static final Path CATALOG = Path.of("../docs/billing/policy-catalog.md");

    @Autowired
    private ApplicationContext context;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String renderDeclaredCatalog() {
        List<PolicySet<?>> sets = new ArrayList<>();
        for (PolicySet set : context.getBeansOfType(PolicySet.class).values()) {
            sets.add(set);
        }
        return PolicyCatalogRenderer.render(sets);
    }

    @Test
    void committedCatalogMatchesTheDeclaredPolicySets() throws IOException {
        String declared = renderDeclaredCatalog();
        if (Boolean.getBoolean("policy-catalog.write")) {
            Files.writeString(CATALOG, declared);
        }

        assertThat(CATALOG).as("run ./mvnw test -Dtest=PolicyCatalogTest -Dpolicy-catalog.write=true and commit the result").exists();
        assertThat(Files.readString(CATALOG))
                .as("docs/billing/policy-catalog.md is out of date: run ./mvnw test -Dtest=PolicyCatalogTest -Dpolicy-catalog.write=true and commit")
                .isEqualTo(declared);
    }

    @Test
    void everyDeclaredPolicySetAppearsInTheCatalogWithItsPolicies() {
        String declared = renderDeclaredCatalog();

        assertThat(declared).contains("## Momento INVOICE_ISSUING")
                .contains("| 1 | ReasonFeeTypePolicy | FEE_TYPE_NOT_ALLOWED_FOR_REASON | D-050 |")
                .contains("| 5 | NotAlreadyIssuedPolicy | ALREADY_ISSUED | D-009 |");
    }
}

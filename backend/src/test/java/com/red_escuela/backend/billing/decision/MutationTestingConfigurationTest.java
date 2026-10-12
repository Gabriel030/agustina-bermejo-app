package com.red_escuela.backend.billing.decision;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-172")
class MutationTestingConfigurationTest {

    private static final Pattern THRESHOLD = Pattern.compile("<mutationThreshold>(\\d+)</mutationThreshold>");
    private static final Pattern EXCLUDED_CLASSES = Pattern.compile("<excludedClasses>(.*?)</excludedClasses>", Pattern.DOTALL);
    private static final Pattern PARAM = Pattern.compile("<param>(.*?)</param>");

    private static String pom() throws IOException {
        return Files.readString(Path.of("pom.xml"));
    }

    @Test
    void theMutationProfileRequiresAtLeastNinetyPercent() throws IOException {
        Matcher threshold = THRESHOLD.matcher(pom());

        assertThat(threshold.find()).isTrue();
        assertThat(Integer.parseInt(threshold.group(1))).isGreaterThanOrEqualTo(90);
    }

    @Test
    void theMutationProfileTargetsOnlyPurePackagesAndSkipsIntegrationTests() throws IOException {
        String pom = pom();

        assertThat(pom).contains("<param>com.red_escuela.backend.shared.domain.*</param>")
                .contains("<param>com.red_escuela.backend.shared.domain.policy.*</param>")
                .contains("<param>com.red_escuela.backend.billing.configuration.policy.*</param>")
                .contains("<param>com.red_escuela.backend.billing.operation.*</param>")
                .contains("<param>integration</param>");
        assertThat(pom).doesNotContain("<param>com.red_escuela.backend.billing.service")
                .doesNotContain("<param>com.red_escuela.backend.billing.usecase");
    }

    @Test
    void everyExclusionOfTheProfileIsListedWithItsJustification() throws IOException {
        Matcher excluded = EXCLUDED_CLASSES.matcher(pom());
        String exclusions = Files.readString(Path.of("../docs/billing/mutation-exclusions.md"));

        if (excluded.find()) {
            Matcher param = PARAM.matcher(excluded.group(1));
            while (param.find()) {
                assertThat(exclusions).as("exclusion %s without a justification entry", param.group(1)).contains(param.group(1));
            }
        }
        assertThat(exclusions).contains("| Clase | Línea | Operador | Motivo de equivalencia |");
    }
}

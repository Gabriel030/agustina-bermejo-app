package com.red_escuela.backend.billing.guard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

public final class BillingEndpointClassificationFile {

    private static final Path FILE = Path.of("../docs/billing/mirror-mode-endpoint-classification.md");

    public enum Classification { READ, WRITE, EXCEPTION_OFF }

    private BillingEndpointClassificationFile() {
    }

    public static Map<String, Classification> read() throws IOException {
        Map<String, Classification> classified = new TreeMap<>();
        for (String line : Files.readAllLines(FILE)) {
            if (!line.startsWith("| ")) {
                continue;
            }
            String[] cells = line.substring(1, line.length() - 1).split("\\|", -1);
            if (cells.length != 6 || !List.of("READ", "WRITE", "EXCEPTION_OFF").contains(cells[4].trim())) {
                continue;
            }
            String key = cells[0].trim() + "." + cells[1].trim() + " " + cells[2].trim() + " " + cells[3].trim().replace("`", "");
            assertThat(classified.put(key, Classification.valueOf(cells[4].trim()))).as("duplicated row " + key).isNull();
        }
        return classified;
    }
}

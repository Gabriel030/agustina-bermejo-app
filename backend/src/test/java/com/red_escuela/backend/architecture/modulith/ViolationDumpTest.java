package com.red_escuela.backend.architecture.modulith;

import com.red_escuela.backend.testsupport.ImportedApplicationClasses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ViolationDumpTest {

    @Test
    @EnabledIfSystemProperty(named = "modulith.violations.dump", matches = ".+")
    void dumpsEveryRawViolationMessage() throws Exception {
        List<String> messages = ImportedApplicationClasses.modules().detectViolations().getMessages().stream()
                .map(message -> message.replace("\r", "").replaceAll("\\s*\\n\\s*", " | ").trim())
                .toList();
        Files.write(Path.of(System.getProperty("modulith.violations.dump")), messages);
    }
}

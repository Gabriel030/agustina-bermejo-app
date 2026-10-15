package com.red_escuela.backend.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class UseCaseBranchingScanner {

    private static final Pattern BRANCH = Pattern.compile("\\bif\\s*\\(|\\bswitch\\b|\\s\\?\\s.*\\s:\\s");

    private UseCaseBranchingScanner() {
    }

    public static List<String> findViolations(Path sourceRoot, List<String> subdomainPaths) {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<String> violations = new ArrayList<>();
            for (Path file : files.filter(UseCaseBranchingScanner::isUseCaseService).toList()) {
                String relative = sourceRoot.relativize(file).toString().replace('\\', '/');
                if (subdomainPaths.stream().noneMatch(relative::contains)) {
                    continue;
                }
                violations.addAll(scanSource(relative, Files.readString(file)));
            }
            return violations;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<String> scanSource(String name, String content) {
        List<String> violations = new ArrayList<>();
        if (!content.contains("@UseCase")) {
            return violations;
        }
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (BRANCH.matcher(lines[i]).find()) {
                violations.add(name + ":" + (i + 1) + " " + lines[i].strip());
            }
        }
        return violations;
    }

    private static boolean isUseCaseService(Path path) {
        String file = path.toString().replace('\\', '/');
        return file.endsWith("Service.java") && file.contains("/usecase/");
    }
}

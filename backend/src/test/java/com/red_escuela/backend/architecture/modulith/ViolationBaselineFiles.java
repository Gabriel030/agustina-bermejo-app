package com.red_escuela.backend.architecture.modulith;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class ViolationBaselineFiles {

    private static final String CYCLES_FILE = "cycles.txt";
    private static final String EXTENSION = ".txt";

    private ViolationBaselineFiles() {
    }

    public static ViolationSnapshot read(Path directory) {
        Map<String, List<String>> lines = new TreeMap<>();
        List<String> cycles = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return ViolationSnapshot.of(lines, cycles);
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(EXTENSION)) {
                    continue;
                }
                List<String> content = Files.readAllLines(file).stream().filter(line -> !line.isBlank()).toList();
                if (name.equals(CYCLES_FILE)) {
                    cycles.addAll(content);
                } else {
                    lines.put(name.substring(0, name.length() - EXTENSION.length()), content);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ViolationSnapshot.of(lines, cycles);
    }

    public static void write(Path directory, ViolationSnapshot snapshot) {
        try {
            Files.createDirectories(directory);
            try (Stream<Path> files = Files.list(directory)) {
                for (Path file : files.filter(path -> path.toString().endsWith(EXTENSION)).toList()) {
                    Files.delete(file);
                }
            }
            for (Map.Entry<String, List<String>> entry : snapshot.linesBySourceModule().entrySet()) {
                Files.write(directory.resolve(entry.getKey() + EXTENSION), entry.getValue());
            }
            Files.write(directory.resolve(CYCLES_FILE), snapshot.cyclePairs());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

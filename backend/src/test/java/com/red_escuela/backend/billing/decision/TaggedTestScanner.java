package com.red_escuela.backend.billing.decision;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class TaggedTestScanner {

    private static final Pattern TAG = Pattern.compile("@Tag\\(\"((?:contradicts-)?D-\\d{3})\"\\)");
    private static final Pattern TEXT_BLOCK = Pattern.compile("(?s)\"\"\".*?\"\"\"");
    private static final Pattern DECLARATION = Pattern.compile("\\b(void|class)\\s");
    private static final Pattern NIGHTLY_ANNOTATION = Pattern.compile("@NightlyTest\\(reason = \"[^\"]*\"\\)");
    private static final Pattern OUTSIDE_PR_MARKER = Pattern.compile("@NightlyTest\\b|@Tag\\(\"(?:nightly|migration)\"\\)");
    private static final Pattern CLASS_DECLARATION = Pattern.compile("\\bclass\\s");
    private static final Pattern DISABLED_CLASS =
            Pattern.compile("@Disabled[^\\n]*\\n(?:\\s*@[^\\n]*\\n)*\\s*(?:public |final |abstract )*class\\s");

    private TaggedTestScanner() {
    }

    public static List<TaggedTestUse> scanDirectory(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            List<TaggedTestUse> uses = new ArrayList<>();
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                uses.addAll(scanSource(root.relativize(file).toString(), Files.readString(file)));
            }
            return uses;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<TaggedTestUse> scanSources(Map<String, String> sourcesByName) {
        List<TaggedTestUse> uses = new ArrayList<>();
        sourcesByName.forEach((name, content) -> uses.addAll(scanSource(name, content)));
        return uses;
    }

    private static List<TaggedTestUse> scanSource(String name, String rawContent) {
        String content = NIGHTLY_ANNOTATION.matcher(TEXT_BLOCK.matcher(rawContent).replaceAll("")).replaceAll("@NightlyTest");
        Matcher firstClass = CLASS_DECLARATION.matcher(content);
        boolean classOutsidePrTier = firstClass.find() && OUTSIDE_PR_MARKER.matcher(content.substring(0, firstClass.start())).find();
        boolean classDisabled = DISABLED_CLASS.matcher(content).find();
        List<TaggedTestUse> uses = new ArrayList<>();
        Matcher tag = TAG.matcher(content);
        while (tag.find()) {
            Matcher declaration = DECLARATION.matcher(content);
            int end = declaration.find(tag.end()) ? declaration.start() : content.length();
            int start = Math.max(content.lastIndexOf('}', tag.start()), Math.max(content.lastIndexOf('{', tag.start()), content.lastIndexOf(';', tag.start())));
            boolean disabled = classDisabled || content.substring(Math.max(start, 0), end).contains("@Disabled");
            boolean outsidePrTier = classOutsidePrTier || OUTSIDE_PR_MARKER.matcher(content.substring(Math.max(start, 0), end)).find();
            uses.add(new TaggedTestUse(tag.group(1), name, disabled, outsidePrTier));
        }
        return uses;
    }
}

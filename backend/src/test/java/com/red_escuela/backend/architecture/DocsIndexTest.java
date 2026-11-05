package com.red_escuela.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocsIndexTest {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
    private static final Path REPO_ROOT = Path.of("..");
    private static final Path DOCS_ROOT = REPO_ROOT.resolve("docs");
    private static final String INDEX_NAME = "README.md";

    @Test
    void everyMarkdownFileUnderDocsIsListedInTheIndex() throws IOException {
        List<String> unlisted = unlistedDocuments(DOCS_ROOT);

        assertThat(unlisted).as("documentos de docs/ que no figuran en docs/README.md").isEmpty();
    }

    @Test
    void everyRelativeLinkInTheDocumentationPointsToAnExistingFile() throws IOException {
        List<Path> documents = new ArrayList<>(markdownFilesUnder(DOCS_ROOT));
        documents.add(REPO_ROOT.resolve("README.md"));
        documents.add(REPO_ROOT.resolve("CLAUDE.md"));

        List<String> broken = new ArrayList<>();
        for (Path document : documents) {
            broken.addAll(brokenLinksIn(document));
        }

        assertThat(broken).as("enlaces relativos rotos").isEmpty();
    }

    @Test
    void detectsADocumentMissingFromTheIndex(@TempDir Path docs) throws IOException {
        Files.writeString(docs.resolve(INDEX_NAME), "[listed](listed.md)\n");
        Files.writeString(docs.resolve("listed.md"), "# listed\n");
        Files.writeString(docs.resolve("forgotten.md"), "# forgotten\n");

        assertThat(unlistedDocuments(docs)).hasSize(1).first().asString().contains("forgotten.md");
    }

    @Test
    void detectsALinkToAMissingFileAndIgnoresExternalAnchorAndCodeLinks(@TempDir Path docs) throws IOException {
        Path document = docs.resolve("page.md");
        Files.writeString(document, String.join("\n",
                "[ok](other.md#section)",
                "[web](https://example.com/x.md)",
                "[anchor](#top)",
                "[vault](~/Documentos/nota.md)",
                "[missing](gone.md)",
                "```",
                "[inCode](alsoGone.md)",
                "```",
                ""));
        Files.writeString(docs.resolve("other.md"), "# other\n");

        assertThat(brokenLinksIn(document)).hasSize(1).first().asString().contains("gone.md");
    }

    private static List<String> unlistedDocuments(Path docsRoot) throws IOException {
        Path index = docsRoot.resolve(INDEX_NAME);
        String indexText = Files.readString(index);
        List<String> unlisted = new ArrayList<>();
        for (Path document : markdownFilesUnder(docsRoot)) {
            if (document.equals(index)) {
                continue;
            }
            String relative = docsRoot.relativize(document).toString().replace('\\', '/');
            if (!indexText.contains("](" + relative + ")")) {
                unlisted.add(relative);
            }
        }
        return unlisted;
    }

    private static List<String> brokenLinksIn(Path document) throws IOException {
        List<String> broken = new ArrayList<>();
        boolean insideFence = false;
        for (String line : Files.readAllLines(document)) {
            if (line.stripLeading().startsWith("```")) {
                insideFence = !insideFence;
                continue;
            }
            if (insideFence) {
                continue;
            }
            Matcher matcher = MARKDOWN_LINK.matcher(line);
            while (matcher.find()) {
                String target = matcher.group(1);
                if (isExternalOrAnchor(target)) {
                    continue;
                }
                String withoutAnchor = target.split("#", 2)[0];
                if (!Files.exists(document.resolveSibling(withoutAnchor).normalize())) {
                    broken.add(document + " -> " + target);
                }
            }
        }
        return broken;
    }

    private static boolean isExternalOrAnchor(String target) {
        return target.startsWith("#") || target.startsWith("mailto:") || target.startsWith("~") || target.startsWith("/")
                || target.contains("://");
    }

    private static List<Path> markdownFilesUnder(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".md")).sorted().toList();
        }
    }
}

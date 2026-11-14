package com.red_escuela.backend.dataimport.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.red_escuela.backend.dataimport.exception.ImportFileInvalidHeaderException;
import com.red_escuela.backend.dataimport.exception.ImportTooManyRowsException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImportCsvParserTest {
    private final ImportCsvParser parser = new ImportCsvParser();

    private ImportCsvParser.ParsedCsv parse(String content, int maxRows) {
        return parser.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), maxRows);
    }

    @Test
    void parsesCommaSeparatedFileWithBom() {
        ImportCsvParser.ParsedCsv parsed = parse("﻿a,b\n1,2\n3,4\n", 10);

        assertThat(parsed.headers()).containsExactly("a", "b");
        assertThat(parsed.rows()).containsExactly(java.util.List.of("1", "2"), java.util.List.of("3", "4"));
    }

    @Test
    void detectsSemicolonDelimiter() {
        ImportCsvParser.ParsedCsv parsed = parse("a;b\n1,5;2\n", 10);

        assertThat(parsed.headers()).containsExactly("a", "b");
        assertThat(parsed.rows().get(0)).containsExactly("1,5", "2");
    }

    @Test
    void keepsQuotedDelimitersNewlinesAndEscapedQuotes() {
        ImportCsvParser.ParsedCsv parsed = parse("a,b\r\n\"x,y\",\"line1\nline2 \"\"q\"\"\"\r\n", 10);

        assertThat(parsed.rows().get(0)).containsExactly("x,y", "line1\nline2 \"q\"");
    }

    @Test
    void skipsBlankLinesAndKeepsEmptyTrailingFields() {
        ImportCsvParser.ParsedCsv parsed = parse("a,b,c\n\n1,,\n", 10);

        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().get(0)).containsExactly("1", "", "");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> parse("", 10)).isInstanceOf(ImportFileInvalidHeaderException.class);
    }

    @Test
    void rejectsMoreRowsThanTheLimitButAcceptsExactlyTheLimit() {
        assertThat(parse("a\n1\n2\n", 2).rows()).hasSize(2);
        assertThatThrownBy(() -> parse("a\n1\n2\n3\n", 2)).isInstanceOf(ImportTooManyRowsException.class);
    }
}

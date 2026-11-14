package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.exception.ImportFileInvalidHeaderException;
import com.red_escuela.backend.dataimport.exception.ImportFileUnreadableException;
import com.red_escuela.backend.dataimport.exception.ImportTooManyRowsException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ImportCsvParser {
    private static final char BOM = '﻿';

    public ParsedCsv parse(InputStream input, int maxRows) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            reader.mark(1);
            int first = reader.read();
            if (first == -1) {
                throw new ImportFileInvalidHeaderException("el archivo está vacío");
            }
            if (first != BOM) {
                reader.reset();
            }
            reader.mark(8192);
            String headerLine = reader.readLine();
            reader.reset();
            char delimiter = detectDelimiter(headerLine == null ? "" : headerLine);
            List<List<String>> records = readRecords(reader, delimiter, maxRows + 1);
            if (records.isEmpty()) {
                throw new ImportFileInvalidHeaderException("el archivo está vacío");
            }
            List<String> headers = records.get(0).stream().map(String::strip).toList();
            return new ParsedCsv(headers, records.subList(1, records.size()));
        } catch (IOException e) {
            throw new ImportFileUnreadableException();
        }
    }

    private static char detectDelimiter(String headerLine) {
        long semicolons = headerLine.chars().filter(c -> c == ';').count();
        long commas = headerLine.chars().filter(c -> c == ',').count();
        return semicolons > commas ? ';' : ',';
    }

    private static List<List<String>> readRecords(BufferedReader reader, char delimiter, int maxRecords)
            throws IOException {
        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean anyContent = false;
        int c;
        while ((c = reader.read()) != -1) {
            char ch = (char) c;
            if (quoted) {
                if (ch == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        if (next != -1) {
                            reader.reset();
                        }
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }
            if (ch == '"') {
                quoted = true;
                anyContent = true;
            } else if (ch == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
                anyContent = true;
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next != '\n' && next != -1) {
                        reader.reset();
                    }
                }
                if (anyContent) {
                    fields.add(field.toString());
                    addRecord(records, fields, maxRecords);
                    fields = new ArrayList<>();
                    field.setLength(0);
                    anyContent = false;
                }
            } else {
                field.append(ch);
                anyContent = true;
            }
        }
        if (anyContent) {
            fields.add(field.toString());
            addRecord(records, fields, maxRecords);
        }
        return records;
    }

    private static void addRecord(List<List<String>> records, List<String> fields, int maxRecords) {
        records.add(fields);
        if (records.size() > maxRecords) {
            throw new ImportTooManyRowsException(maxRecords - 1);
        }
    }

    public record ParsedCsv(List<String> headers, List<List<String>> rows) {
    }
}

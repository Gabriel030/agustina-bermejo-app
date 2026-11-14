package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.dto.ImportFileDownload;
import com.red_escuela.backend.dataimport.exception.ImportFileUnreadableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ImportTemplateReader {
    private static final byte[] BOM = "﻿".getBytes(StandardCharsets.UTF_8);

    public ImportFileDownload read(ImportEntityType type) {
        try (InputStream stream = new ClassPathResource("dataimport/templates/" + type.name() + ".csv").getInputStream()) {
            byte[] body = stream.readAllBytes();
            byte[] content = new byte[BOM.length + body.length];
            System.arraycopy(BOM, 0, content, 0, BOM.length);
            System.arraycopy(body, 0, content, BOM.length, body.length);
            return new ImportFileDownload("plantilla-" + type.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".csv", content);
        } catch (IOException e) {
            throw new ImportFileUnreadableException();
        }
    }
}

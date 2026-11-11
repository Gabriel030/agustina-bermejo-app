package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.ClosureExportFormat;
import com.red_escuela.backend.evaluation.dto.ClosureSheetResponse;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ClosureExportFileNamer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

    public String nameOf(ClosureSheetResponse sheet, ClosureExportFormat format) {
        String base = "cierre-" + slugOf(sheet.subjectName()) + "-" + slugOf(sheet.sectionName());
        String suffix = sheet.status() == ClosureStatus.CLOSED && sheet.reportNumber() != null
                ? "-acta-" + slugOf(sheet.reportNumber()).toUpperCase(Locale.ROOT) + "-v" + sheet.reportVersion()
                : "-borrador";
        return base + suffix + "." + format.extension();
    }

    String slugOf(String value) {
        String stripped = DIACRITICS.matcher(Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)).replaceAll("");
        String slug = EDGE_DASHES.matcher(NON_ALPHANUMERIC.matcher(stripped.toLowerCase(Locale.ROOT)).replaceAll("-")).replaceAll("");
        return slug.isEmpty() ? "comision" : slug;
    }
}

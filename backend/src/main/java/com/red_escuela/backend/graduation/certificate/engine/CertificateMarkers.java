package com.red_escuela.backend.graduation.certificate.engine;

import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CertificateMarkers {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern MARKER = Pattern.compile("\\{([A-Za-z]+)}");

    private CertificateMarkers() {
    }

    public static boolean mentionsHours(String text) {
        return text != null && text.contains("{hours}");
    }

    public static String expand(String text, CertificateSnapshot snapshot, String registryNumber) {
        if (text == null) {
            return "";
        }
        Matcher matcher = MARKER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(valueOf(matcher.group(1), matcher.group(), snapshot, registryNumber)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String valueOf(String name, String original, CertificateSnapshot snapshot, String registryNumber) {
        return switch (name) {
            case "holder" -> snapshot.holderFullName();
            case "documentType" -> snapshot.documentTypeLabel();
            case "documentNumber" -> snapshot.documentNumber();
            case "titleKind" -> snapshot.titleKind();
            case "titleName" -> snapshot.titleName();
            case "hours" -> snapshot.hours() == null ? "" : snapshot.hours() + " horas";
            case "graduationDate" -> snapshot.graduationDate().format(DATE_FORMAT);
            case "issuedOn" -> snapshot.issuedOn().format(DATE_FORMAT);
            case "registryNumber" -> registryNumber == null ? "" : registryNumber;
            default -> original;
        };
    }
}

package com.red_escuela.backend.graduation.certificate.engine;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QrMatrixFactory {

    public boolean[][] create(String text) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 0));
            boolean[][] modules = new boolean[matrix.getHeight()][matrix.getWidth()];
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    modules[y][x] = matrix.get(x, y);
                }
            }
            return modules;
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el código QR", e);
        }
    }
}

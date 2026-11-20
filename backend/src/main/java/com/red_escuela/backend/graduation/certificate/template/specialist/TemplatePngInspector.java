package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.template.exception.TemplateImageInvalidException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

@Component
public class TemplatePngInspector {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_SIDE_PIXELS = 4000;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    public record PngFacts(int widthPixels, int heightPixels, boolean hasAlpha) {
    }

    public PngFacts inspect(byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_BYTES || !startsWithPngSignature(content)) {
            throw new TemplateImageInvalidException();
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
            if (stream == null || !readers.hasNext()) {
                throw new TemplateImageInvalidException();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_SIDE_PIXELS || height > MAX_SIDE_PIXELS) {
                    throw new TemplateImageInvalidException();
                }
                return new PngFacts(width, height, hasAlpha(reader));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof TemplateImageInvalidException invalid) {
                throw invalid;
            }
            throw new TemplateImageInvalidException();
        }
    }

    private boolean hasAlpha(ImageReader reader) throws IOException {
        ImageTypeSpecifier raw = reader.getRawImageType(0);
        if (raw != null) {
            return raw.getColorModel().hasAlpha();
        }
        Iterator<ImageTypeSpecifier> types = reader.getImageTypes(0);
        while (types.hasNext()) {
            if (types.next().getColorModel().hasAlpha()) {
                return true;
            }
        }
        return false;
    }

    private boolean startsWithPngSignature(byte[] content) {
        if (content.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (content[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }
}

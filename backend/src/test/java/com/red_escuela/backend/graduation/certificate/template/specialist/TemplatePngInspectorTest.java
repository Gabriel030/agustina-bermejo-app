package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.template.exception.TemplateImageInvalidException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplatePngInspectorTest {

    private final TemplatePngInspector inspector = new TemplatePngInspector();

    private static byte[] png(int width, int height, int type) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, type), "png", out);
        return out.toByteArray();
    }

    @Test
    void imageWiderThanThePixelCapIsRejected() throws IOException {
        assertThatThrownBy(() -> inspector.inspect(png(TemplatePngInspector.MAX_SIDE_PIXELS + 1, 1, BufferedImage.TYPE_INT_RGB)))
                .isInstanceOf(TemplateImageInvalidException.class);
        assertThatThrownBy(() -> inspector.inspect(png(1, TemplatePngInspector.MAX_SIDE_PIXELS + 1, BufferedImage.TYPE_INT_RGB)))
                .isInstanceOf(TemplateImageInvalidException.class);
    }

    @Test
    void imageAtThePixelCapIsAccepted() throws IOException {
        assertThat(inspector.inspect(png(TemplatePngInspector.MAX_SIDE_PIXELS, 1, BufferedImage.TYPE_INT_RGB)).widthPixels())
                .isEqualTo(TemplatePngInspector.MAX_SIDE_PIXELS);
    }

    @Test
    void contentOverTwoMegabytesIsRejectedBeforeDecoding() {
        byte[] oversized = new byte[TemplatePngInspector.MAX_BYTES + 1];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, oversized, 0, 8);

        assertThatThrownBy(() -> inspector.inspect(oversized)).isInstanceOf(TemplateImageInvalidException.class);
    }

    @Test
    void alteredSignatureIsRejected() throws IOException {
        byte[] altered = png(5, 5, BufferedImage.TYPE_INT_ARGB);
        altered[0] = 0;

        assertThatThrownBy(() -> inspector.inspect(altered)).isInstanceOf(TemplateImageInvalidException.class);
    }

    @Test
    void alphaIsDetectedWithoutDecodingTheWholeImage() throws IOException {
        assertThat(inspector.inspect(png(5, 5, BufferedImage.TYPE_INT_ARGB)).hasAlpha()).isTrue();
        assertThat(inspector.inspect(png(5, 5, BufferedImage.TYPE_INT_RGB)).hasAlpha()).isFalse();
    }
}

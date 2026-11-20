package com.red_escuela.backend.graduation.certificate.engine;

import java.util.ArrayList;
import java.util.List;

public final class GuillocheBand {

    public static final Rect LEFT = new Rect(58, 118, 92, 430);
    public static final Rect RIGHT = new Rect(1123 - 58 - 92, 118, 92, 430);

    private static final float[] AMPLITUDES = {38, 32, 26, 20, 14};
    private static final float[] PERIODS = {86, 61, 47, 113, 71};
    private static final float[] PHASES = {0f, 1.1f, 2.3f, 3.4f, 4.6f};
    private static final float STEP = 2;

    private GuillocheBand() {
    }

    public static List<List<float[]>> polylines(Rect box) {
        float centerX = box.x() + box.width() / 2;
        List<List<float[]>> lines = new ArrayList<>();
        for (int wave = 0; wave < AMPLITUDES.length; wave++) {
            List<float[]> points = new ArrayList<>();
            for (float y = 0; y <= box.height(); y += STEP) {
                double t = y / box.height();
                double envelope = 0.35 + 0.65 * Math.sin(Math.PI * t);
                double offset = AMPLITUDES[wave] * envelope * Math.sin(2 * Math.PI * y / PERIODS[wave] + PHASES[wave]);
                points.add(new float[]{(float) (centerX + offset), box.y() + y});
            }
            lines.add(points);
        }
        return lines;
    }
}

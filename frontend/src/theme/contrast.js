export function oklchToRawLinearRgb(l, c, hDeg) {
  const h = (hDeg * Math.PI) / 180;
  const a = c * Math.cos(h);
  const b = c * Math.sin(h);
  const l_ = (l + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m_ = (l - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s_ = (l - 0.0894841775 * a - 1.291485548 * b) ** 3;
  return [
    4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_,
    -1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_,
    -0.0041960863 * l_ - 0.7034186147 * m_ + 1.707614701 * s_,
  ];
}

export function oklchToLinearRgb(l, c, hDeg) {
  return oklchToRawLinearRgb(l, c, hDeg).map((v) => Math.min(1, Math.max(0, v)));
}

export function isInSrgbGamut(l, c, hDeg, tolerance = 0.0005) {
  return oklchToRawLinearRgb(l, c, hDeg).every((v) => v >= -tolerance && v <= 1 + tolerance);
}

export function contrastRatio(foreground, background) {
  const lum = ([r, g, b]) => 0.2126 * r + 0.7152 * g + 0.0722 * b;
  const [hi, lo] = [lum(foreground), lum(background)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

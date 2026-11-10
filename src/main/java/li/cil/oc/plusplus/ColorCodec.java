package li.cil.oc.plusplus;

/** Pure cell-colour operations shared by storage, rendering and tests. */
public final class ColorCodec {
    private ColorCodec() {}
    public static int to565(int rgb) {
        return ((rgb >>> 19) & 31) << 11 | ((rgb >>> 10) & 63) << 5 | ((rgb >>> 3) & 31);
    }
    public static int from565(int value) {
        int r = (value >>> 11) & 31, g = (value >>> 5) & 63, b = value & 31;
        return ((r << 3 | r >>> 2) << 16) | ((g << 2 | g >>> 4) << 8) | (b << 3 | b >>> 2);
    }
    /** Source-over an opaque destination, integer sRGB channels, nearest rounding. */
    public static int over(int rgb, int alpha, int destination) {
        if (alpha < 0 || alpha > 255) throw new IllegalArgumentException("alpha must be 0..255");
        if (alpha == 255) return rgb & 0xFFFFFF;
        if (alpha == 0) return destination & 0xFFFFFF;
        int result = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int a = (rgb >>> shift) & 255, b = (destination >>> shift) & 255;
            result |= ((a * alpha + b * (255 - alpha) + 127) / 255) << shift;
        }
        return result;
    }
    public static long rgba(int rgb, int alpha) {
        if (alpha < 0 || alpha > 255) throw new IllegalArgumentException("alpha must be 0..255");
        return ((rgb & 0xFFFFFFL) << 8) | alpha;
    }
    public static long checkedRgba(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 0xFFFFFFFFL || value != Math.floor(value))
            throw new IllegalArgumentException("RGBA must be an integer 0xRRGGBBAA in 0..0xFFFFFFFF");
        return (long) value;
    }
}

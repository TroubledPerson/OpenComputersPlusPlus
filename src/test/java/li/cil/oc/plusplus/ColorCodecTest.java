package li.cil.oc.plusplus;

import org.junit.Test;
import static org.junit.Assert.*;

public class ColorCodecTest {
    @Test public void allRgb565ValuesRoundTrip() {
        for (int packed = 0; packed <= 65535; packed++)
            assertEquals(packed, ColorCodec.to565(ColorCodec.from565(packed)));
    }
    @Test public void alphaBlendsHaveDefinedEndpointsAndChannels() {
        for (int alpha = 0; alpha <= 255; alpha++)
            assertEquals((alpha << 16) | (255 - alpha), ColorCodec.over(0xFF0000, alpha, 0x0000FF));
        assertEquals(0x80007F, ColorCodec.over(0xFF0000, 128, 0x0000FF));
        assertEquals(0x123456, ColorCodec.over(0x123456, 255, 0));
        assertEquals(0xABCDEF, ColorCodec.over(0, 0, 0xABCDEF));
    }
    @Test public void rgbaIsExplicitAndUnsigned() {
        assertEquals(0xFFFFFFFFL, ColorCodec.rgba(0xFFFFFF, 255));
        assertEquals(0x00FF0000L, ColorCodec.rgba(0x00FF00, 0));
        assertEquals(0xFFFFFFFFL, ColorCodec.checkedRgba(4294967295.0));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectNegativeRgba() { ColorCodec.checkedRgba(-1); }
    @Test(expected=IllegalArgumentException.class) public void rejectFractionalRgba() { ColorCodec.checkedRgba(1.5); }
    @Test(expected=IllegalArgumentException.class) public void rejectNonFiniteRgba() { ColorCodec.checkedRgba(Double.NaN); }
    @Test(expected=IllegalArgumentException.class) public void rejectOversizedRgba() { ColorCodec.checkedRgba(4294967296.0); }
    @Test(expected=IllegalArgumentException.class) public void rejectAlphaOverflow() { ColorCodec.over(0, 256, 0); }
}

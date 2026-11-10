package li.cil.oc.plusplus;

import org.junit.Test;
import static org.junit.Assert.*;

public class SlotTierLabelTest {
    @Test public void labelsUseRomanNumeralsAndOriginalNeutralInk() {
        assertEquals("IV", SlotTierLabel.numeral(4));
        assertEquals("V", SlotTierLabel.numeral(5));
        assertEquals("VI", SlotTierLabel.numeral(6));
        assertEquals(0xFF373737, SlotTierLabel.INK);
    }
    @Test public void glyphsFitTheOriginalBottomRightFivePixelBaseline() {
        for (int tier = 4; tier <= 6; tier++) {
            String[] rows = SlotTierLabel.rows(tier);
            assertEquals(5, rows.length);
            int width = rows[0].length();
            assertTrue(width > 0 && width <= 15);
            assertEquals(14, SlotTierLabel.TOP + rows.length - 1);
            for (String row : rows) {
                assertEquals(width, row.length());
                assertTrue(row.matches("[.#]+"));
            }
        }
    }
    @Test(expected=IllegalArgumentException.class)
    public void doesNotReplaceLegacyOrUnknownTierLabels() { SlotTierLabel.rows(3); }
}

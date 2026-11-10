package li.cil.oc.plusplus;

/** Code-drawn pixel glyphs matching the original 16x16 tier overlays' ink and
 * bottom-right baseline. Original I/II/III textures remain untouched. */
public final class SlotTierLabel {
    public static final int INK = 0xFF373737;
    public static final int TOP = 10;
    private SlotTierLabel() {}

    public static String numeral(int tier) {
        switch (tier) {
            case 4: return "IV";
            case 5: return "V";
            case 6: return "VI";
            default: throw new IllegalArgumentException("extended tier required");
        }
    }

    public static String[] rows(int tier) {
        String[] i = {"###", ".#.", ".#.", ".#.", "###"};
        String[] v = {"#...#", "#...#", ".#.#.", ".#.#.", "..#.."};
        String[] result = new String[5];
        String label = numeral(tier);
        for (int y = 0; y < result.length; y++) {
            StringBuilder line = new StringBuilder();
            for (int x = 0; x < label.length(); x++) {
                if (x != 0) line.append('.');
                line.append(label.charAt(x) == 'I' ? i[y] : v[y]);
            }
            result[y] = line.toString();
        }
        return result;
    }
}
